import { useCallback, useEffect, useRef, useState } from 'react'

import { resolveBackendChildId } from '@/api/children'
import {
  type ClientTerminationReason,
  ConversationApiError,
  type ConversationTopicType,
  completeConversation,
  completeConversationOnUnload,
  createConversation,
  issueRealtimeSession,
} from '@/api/conversations'
import {
  CONVERSATION_EVENT,
  type ConversationEventEnvelope,
  STT_LOW_CONFIDENCE_CODE,
  readAssistantText,
  readConversationError,
  readSessionCompleted,
  readSlotsUpdated,
  readSttFinal,
} from '@/lib/realtime/events'
import {
  RealtimeConversationClient,
  type RealtimeFatalCode,
} from '@/lib/realtime/RealtimeConversationClient'

export type ConversationPhase =
  | 'idle'
  | 'preparing'
  | 'connecting'
  | 'active'
  | 'closing'
  | 'ended'
  | 'left'
  | 'failed'

export type VoiceStatus = 'waiting' | 'listening' | 'thinking' | 'speaking'

export type ConversationProgress = {
  topicType: ConversationTopicType
  questionCount: number
  maxQuestionCount: number
}

export type RealtimeConversationState = {
  phase: ConversationPhase
  voice: VoiceStatus
  assistantText: string
  childTranscript: string
  progress: ConversationProgress | null
  endedReason: string | null
  failure: { code: string; message: string } | null
  notice: string | null
  localStream: MediaStream | null
  remoteStream: MediaStream | null
  micEnabled: boolean
  /** 방금 대화의 식별자. 종료 후 일기 생성(preview) 호출에 쓴다. */
  conversationId: string | null
  childId: number | null
}

const INITIAL_STATE: RealtimeConversationState = {
  assistantText: '',
  childId: null,
  childTranscript: '',
  conversationId: null,
  endedReason: null,
  failure: null,
  localStream: null,
  micEnabled: true,
  notice: null,
  phase: 'idle',
  progress: null,
  remoteStream: null,
  voice: 'waiting',
}

/**
 * 종료 인사 오디오를 기다리는 한도.
 *
 * `session.completed`는 인사말 재생이 끝나기를 기다리지 않고 먼저 온다. 같은
 * responseId의 `assistant.audio.completed`가 재생 끝을 알려주지만, 합성이
 * 아예 실패하거나 오디오 없는 연결이면 그 이벤트가 오지 않는다. 그때도 화면이
 * 멈춰 있으면 안 되므로 한도를 두고 넘어간다.
 */
const CLOSING_AUDIO_TIMEOUT_MS = 10_000

/** 되묻기 같은 일시적 안내가 화면에 남아 있는 시간. */
const NOTICE_TIMEOUT_MS = 3_000

const TERMINAL_PHASES: ReadonlySet<ConversationPhase> = new Set([
  'ended',
  'left',
  'failed',
])

const IN_FLIGHT_PHASES: ReadonlySet<ConversationPhase> = new Set([
  'preparing',
  'connecting',
  'active',
  'closing',
])

/**
 * 대화 한 번의 REST 준비 → 실시간 연결 → 종료까지를 화면 상태로 옮긴다.
 *
 * 대화 진행은 전적으로 서버 이벤트가 끌고 간다. 질문·전사·진행도·종료 모두
 * 서버가 알려주는 대로만 반영하고, 프론트가 앞질러 추측하지 않는다.
 */
export function useRealtimeConversation() {
  const [state, setState] = useState<RealtimeConversationState>(INITIAL_STATE)

  const clientRef = useRef<RealtimeConversationClient | null>(null)
  const conversationIdRef = useRef<string | null>(null)
  const childIdRef = useRef<number | null>(null)
  const phaseRef = useRef<ConversationPhase>('idle')
  /** 마지막으로 만들어진 AI 응답. 종료 인사의 오디오를 짚어내는 데 쓴다. */
  const lastResponseIdRef = useRef<string | null>(null)
  const closingResponseIdRef = useRef<string | null>(null)
  const closingTimerRef = useRef<number | null>(null)
  const noticeTimerRef = useRef<number | null>(null)
  const micEnabledRef = useRef(true)
  /** 준비 도중 화면을 떠났는지. await 사이사이에 확인해 연결을 만들지 않는다. */
  const unmountedRef = useRef(false)

  const updatePhase = useCallback((phase: ConversationPhase) => {
    phaseRef.current = phase
    setState((current) => ({ ...current, phase }))
  }, [])

  const clearTimers = useCallback(() => {
    if (closingTimerRef.current !== null) {
      window.clearTimeout(closingTimerRef.current)
      closingTimerRef.current = null
    }
    if (noticeTimerRef.current !== null) {
      window.clearTimeout(noticeTimerRef.current)
      noticeTimerRef.current = null
    }
  }, [])

  const showNotice = useCallback((message: string) => {
    setState((current) => ({ ...current, notice: message }))
    if (noticeTimerRef.current !== null) {
      window.clearTimeout(noticeTimerRef.current)
    }
    noticeTimerRef.current = window.setTimeout(() => {
      noticeTimerRef.current = null
      setState((current) => ({ ...current, notice: null }))
    }, NOTICE_TIMEOUT_MS)
  }, [])

  /** 정상 종료 마무리: 연결을 정리하고 끝난 상태로 넘어간다. */
  const finalizeCompletion = useCallback(() => {
    if (phaseRef.current !== 'closing') return
    if (closingTimerRef.current !== null) {
      window.clearTimeout(closingTimerRef.current)
      closingTimerRef.current = null
    }
    clientRef.current?.close()
    clientRef.current = null
    updatePhase('ended')
  }, [updatePhase])

  /**
   * 중도 이탈로 대화를 끝낸다.
   *
   * 완주는 대화 엔진이 선언하므로 이 경로는 종료 API를 부른다. 연결을 먼저
   * 끊는 이유는 서버에 종료를 알려도 AI 발화가 멈추지 않기 때문이다 — 연결
   * 정리는 프론트 책임이다.
   */
  const leave = useCallback(
    (reason: ClientTerminationReason, options?: { unloading?: boolean }) => {
      if (TERMINAL_PHASES.has(phaseRef.current)) return
      const conversationId = conversationIdRef.current
      if (phaseRef.current === 'idle' || conversationId === null) {
        return
      }

      // 종료 인사가 흐르는 동안 화면을 떠나는 경우. 대화는 이미 완주로 끝났고
      // 서버는 확정 이벤트를 저장하는 중이다. 여기서 종료 API를 부르면 중도
      // 이탈로 먼저 처리되어 방금 끝낸 대화 원문이 통째로 지워진다 — 연결만
      // 정리하고 정상 종료로 남긴다.
      if (phaseRef.current === 'closing') {
        finalizeCompletion()
        return
      }

      clearTimers()
      clientRef.current?.close()
      clientRef.current = null

      if (options?.unloading) {
        completeConversationOnUnload(conversationId, reason)
      } else {
        void completeConversation(conversationId, reason).catch(() => {
          // 이미 끝난 대화면 서버가 기존 상태를 돌려주고, 실패해도 Lease 만료
          // 정리가 남아 있다. 떠나는 아이에게 보여줄 오류는 없다.
        })
      }
      updatePhase('left')
    },
    [clearTimers, finalizeCompletion, updatePhase],
  )

  const handleFatal = useCallback(
    (code: RealtimeFatalCode, message: string) => {
      // 정상 종료 뒤 서버가 연결을 닫는 것은 실패가 아니다.
      if (TERMINAL_PHASES.has(phaseRef.current)) return
      // 종료 인사를 기다리던 중 연결이 먼저 끊기면, 대화 자체는 이미 완주로
      // 끝났으므로 실패가 아니라 정상 종료로 마무리한다.
      if (phaseRef.current === 'closing') {
        finalizeCompletion()
        return
      }

      clearTimers()
      clientRef.current?.close()
      clientRef.current = null
      phaseRef.current = 'failed'
      setState((current) => ({
        ...current,
        failure: { code, message },
        phase: 'failed',
      }))

      // 실패한 시도가 남긴 대화를 서버에서도 끝낸다. 실패 화면에서 그대로 탭을
      // 닫으면 이 호출이 유일한 정리 기회다 — 남겨두면 아이의 다음 대화가
      // ACTIVE_CONVERSATION_EXISTS로 막힌다. conversationId는 지우지 않는다:
      // 이 호출이 실패해도 다시 시도할 때 start가 한 번 더 정리한다.
      const conversationId = conversationIdRef.current
      if (conversationId !== null) {
        void completeConversation(conversationId, 'CONNECTION_LOST').catch(
          () => undefined,
        )
      }
    },
    [clearTimers, finalizeCompletion],
  )

  const handleConversationEvent = useCallback(
    (envelope: ConversationEventEnvelope) => {
      switch (envelope.type) {
        case CONVERSATION_EVENT.ASSISTANT_RESPONSE_CREATED:
          lastResponseIdRef.current = envelope.responseId
          setState((current) => ({ ...current, voice: 'thinking' }))
          break

        case CONVERSATION_EVENT.ASSISTANT_TEXT_FINAL:
          setState((current) => ({
            ...current,
            assistantText: readAssistantText(envelope.payload),
          }))
          break

        case CONVERSATION_EVENT.ASSISTANT_AUDIO_STARTED:
          setState((current) => ({ ...current, voice: 'speaking' }))
          break

        case CONVERSATION_EVENT.ASSISTANT_AUDIO_COMPLETED:
        case CONVERSATION_EVENT.ASSISTANT_AUDIO_FAILED: {
          // AI 발화가 끝나야 서버가 아이의 입력을 다시 받는다(ADR-020).
          const isClosing = phaseRef.current === 'closing'

          /*
            아이 차례가 오면 마이크를 다시 연다.

            마이크 버튼은 아이가 눌러 놓고 잊기 쉬운 자리에 있다. 끈 상태를 그대로
            두면 화면은 "듣고 있어"라고 하는데 소리는 서버로 가지 않아, 아이는
            아무리 말해도 대답이 없는 대화에 갇힌다. 매 차례 열어 주는 편이
            유아에게는 예측 가능하다.

            마무리 인사 중(closing)에는 열지 않는다. 그때는 대화가 이미 끝나
            일부러 닫아 둔 것이다.
          */
          if (!isClosing) {
            clientRef.current?.setMicEnabled(true)
            micEnabledRef.current = true
          }
          setState((current) => ({
            ...current,
            micEnabled: isClosing ? current.micEnabled : true,
            voice: 'listening',
          }))

          if (
            isClosing &&
            envelope.responseId === closingResponseIdRef.current
          ) {
            finalizeCompletion()
          }
          break
        }

        case CONVERSATION_EVENT.STT_FINAL: {
          const { text } = readSttFinal(envelope.payload)
          setState((current) => ({
            ...current,
            childTranscript: text,
            voice: 'thinking',
          }))
          break
        }

        case CONVERSATION_EVENT.SLOTS_UPDATED: {
          const payload = readSlotsUpdated(envelope.payload)
          if (payload === null) break
          setState((current) => ({
            ...current,
            progress: {
              maxQuestionCount: payload.topicMaxQuestionCount,
              questionCount: payload.topicQuestionCount,
              topicType: payload.topicType,
            },
          }))
          break
        }

        case CONVERSATION_EVENT.SESSION_COMPLETED: {
          if (TERMINAL_PHASES.has(phaseRef.current)) break
          const reason = readSessionCompleted(envelope.payload)
          // 인사말이 아직 재생 중이다. 마이크만 먼저 닫고 끝까지 들려준다.
          clientRef.current?.setMicEnabled(false)
          micEnabledRef.current = false
          closingResponseIdRef.current = lastResponseIdRef.current
          phaseRef.current = 'closing'
          setState((current) => ({
            ...current,
            childTranscript: '',
            endedReason: reason,
            micEnabled: false,
            phase: 'closing',
          }))
          closingTimerRef.current = window.setTimeout(() => {
            closingTimerRef.current = null
            finalizeCompletion()
          }, CLOSING_AUDIO_TIMEOUT_MS)
          break
        }

        case CONVERSATION_EVENT.ERROR: {
          const error = readConversationError(envelope.payload)
          if (error.code === STT_LOW_CONFIDENCE_CODE) {
            // 확인 질문은 서버가 바로 이어서 보낸다. 여기서는 표시만 한다.
            showNotice('잘 못 들었어요. 한 번만 더 말해 줄래?')
            break
          }
          if (error.recoverable) {
            showNotice('조금만 기다려 줄래?')
          }
          break
        }

        default:
          break
      }
    },
    [finalizeCompletion, showNotice],
  )

  const start = useCallback(async () => {
    // 준비·연결·대화 중에는 다시 시작하지 않는다. 그 밖(처음·실패·끝난 뒤)에서는
    // 같은 화면에서 한 번 더 시작할 수 있다.
    if (IN_FLIGHT_PHASES.has(phaseRef.current)) return

    clearTimers()
    lastResponseIdRef.current = null
    closingResponseIdRef.current = null
    micEnabledRef.current = true
    setState({ ...INITIAL_STATE, phase: 'preparing' })
    phaseRef.current = 'preparing'

    try {
      // 앞선 시도가 연결까지 못 가고 끝났다면 그때 만든 대화가 서버에 아직
      // 살아 있다. 아이 한 명에게 진행 중인 대화는 하나뿐이라, 정리하지 않으면
      // 다시 시도할 때마다 ACTIVE_CONVERSATION_EXISTS로 막힌다.
      const abandonedConversationId = conversationIdRef.current
      if (abandonedConversationId !== null) {
        conversationIdRef.current = null
        await completeConversation(
          abandonedConversationId,
          'CONNECTION_LOST',
        ).catch(() => undefined)
      }

      const childId = await resolveBackendChildId()
      const conversation = await createConversation(childId)
      conversationIdRef.current = conversation.conversationId
      childIdRef.current = childId
      // 종료 후 일기 preview에 필요한 식별자를 상태에 실어 둔다. 정상 완주
      // 경로에선 초기화되지 않아 ended 화면까지 유지된다.
      setState((current) => ({
        ...current,
        childId,
        conversationId: conversation.conversationId,
      }))
      const session = await issueRealtimeSession(conversation.conversationId)

      // 준비하는 사이에 화면을 떠났다면 연결을 만들지 않는다. 여기서 멈추지
      // 않으면 마이크를 잡은 PeerConnection이 화면 없이 살아남는다.
      if (unmountedRef.current) {
        conversationIdRef.current = null
        void completeConversation(
          conversation.conversationId,
          'USER_ABANDONED',
        ).catch(() => undefined)
        return
      }

      const client = new RealtimeConversationClient(session, {
        onConversationEvent: handleConversationEvent,
        onFatal: handleFatal,
        onLocalStream: (stream) =>
          setState((current) => ({ ...current, localStream: stream })),
        onPhaseChange: (connectionPhase) => {
          if (
            connectionPhase === 'active' &&
            phaseRef.current === 'connecting'
          ) {
            updatePhase('active')
          }
        },
        onRemoteStream: (stream) =>
          setState((current) => ({ ...current, remoteStream: stream })),
      })
      clientRef.current = client
      updatePhase('connecting')
      client.connect()
    } catch (error) {
      const message =
        error instanceof ConversationApiError &&
        error.code === 'ACTIVE_CONVERSATION_EXISTS'
          ? '아직 끝나지 않은 이야기가 있어요. 잠시 뒤에 다시 해볼까?'
          : error instanceof Error
            ? error.message
            : '대화를 시작하지 못했어요.'
      phaseRef.current = 'failed'
      setState((current) => ({
        ...current,
        failure: { code: 'START_FAILED', message },
        phase: 'failed',
      }))
    }
  }, [clearTimers, handleConversationEvent, handleFatal, updatePhase])

  const finish = useCallback(
    (reason: ClientTerminationReason = 'USER_FINISHED') => {
      leave(reason)
    },
    [leave],
  )

  const toggleMic = useCallback(() => {
    // 토글 값은 ref로 뒤집는다 — setState 업데이터 안에서 마이크를 건드리면
    // React가 업데이터를 두 번 호출할 때 부수효과도 같이 두 번 일어난다.
    const micEnabled = !micEnabledRef.current
    micEnabledRef.current = micEnabled
    clientRef.current?.setMicEnabled(micEnabled)
    setState((current) => ({ ...current, micEnabled }))
  }, [])

  useEffect(() => {
    // StrictMode는 마운트 직후 한 번 정리했다가 다시 붙인다. 여기서 되돌리지
    // 않으면 그 정리가 남긴 플래그 때문에 대화를 영영 시작하지 못한다.
    unmountedRef.current = false

    const handlePageHide = () => {
      leave('PAGE_CLOSED', { unloading: true })
    }
    window.addEventListener('pagehide', handlePageHide)

    return () => {
      window.removeEventListener('pagehide', handlePageHide)
      unmountedRef.current = true
      clearTimers()
      // 화면을 떠나는데 대화가 아직 살아 있으면 이탈로 정리한다. 이미 끝났거나
      // 시작 전이면 아무 일도 하지 않는다.
      leave('USER_ABANDONED')
      clientRef.current?.close()
      clientRef.current = null
    }
  }, [clearTimers, leave])

  return { finish, start, state, toggleMic }
}
