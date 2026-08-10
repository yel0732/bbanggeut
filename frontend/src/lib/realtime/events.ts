import type { ConversationTopicType } from '@/api/conversations'

/*
  AI 서버와 주고받는 메시지 형식.

  - Signaling(WebSocket): ai-server `app/signaling/messages.py`와 1:1이다.
    서버가 `extra="forbid"`로 검증하므로 계약에 없는 필드를 얹으면 거절된다.
  - conversation-events(DataChannel): 공통 Envelope에 payload를 싣는다.
    ai-server `app/events/envelope.py`(수신)·`app/events/outbound.py`(송신) 참고.
*/

// --- Signaling ------------------------------------------------------------

export type SessionDescriptionMessage = {
  type: 'offer' | 'answer'
  sdp: string
}

export type IceCandidateMessage = {
  candidate: string
  sdpMid: string | null
  sdpMLineIndex: number | null
}

export type SignalingOutgoing =
  | { type: 'session.authenticate'; requestId: string; ticket: string }
  | { type: 'rtc.offer'; requestId: string; sdp: SessionDescriptionMessage }
  | {
      type: 'rtc.ice-candidate'
      requestId: string
      candidate: IceCandidateMessage
    }
  | { type: 'rtc.ice-complete'; requestId: string }

export type SignalingIncoming =
  | {
      type: 'session.authenticated'
      requestId: string
      conversationId: string
      realtimeSessionId: string
      sessionEpoch: number
    }
  | { type: 'rtc.answer'; requestId: string; sdp: SessionDescriptionMessage }
  | {
      type: 'rtc.ice-candidate'
      requestId: string
      candidate: IceCandidateMessage
    }
  | {
      type: 'rtc.connected'
      conversationId: string
      realtimeSessionId: string
      sessionEpoch: number
    }
  | {
      type: 'rtc.error'
      requestId: string | null
      code: string
      message: string
    }

// --- conversation-events --------------------------------------------------

export type ConversationEventEnvelope = {
  type: string
  eventId: string
  conversationId: string
  realtimeSessionId: string
  sessionEpoch: number
  turnId: string | null
  turnSeq: number | null
  responseId: string | null
  eventSeq: number
  occurredAt: string
  payload: Record<string, unknown>
}

export const CONVERSATION_EVENT = {
  STT_FINAL: 'stt.final',
  ASSISTANT_RESPONSE_CREATED: 'assistant.response.created',
  ASSISTANT_TEXT_FINAL: 'assistant.text.final',
  ASSISTANT_AUDIO_STARTED: 'assistant.audio.started',
  ASSISTANT_AUDIO_COMPLETED: 'assistant.audio.completed',
  ASSISTANT_AUDIO_INTERRUPTED: 'assistant.audio.interrupted',
  ASSISTANT_AUDIO_FAILED: 'assistant.audio.failed',
  SLOTS_UPDATED: 'conversation.slots.updated',
  SESSION_COMPLETED: 'session.completed',
  ERROR: 'error',
} as const

/** 프론트가 보내는 준비 완료 통지. */
export const CLIENT_READY_EVENT = 'client.ready'

/** 사용자가 명시적으로 현재 AI 발화를 멈출 때 보내는 이벤트. */
export const CLIENT_INTERRUPT_EVENT = 'client.interrupt'

/** 되묻기를 유발한 낮은 STT 신뢰도. 서버가 확인 질문을 이어서 보낸다. */
export const STT_LOW_CONFIDENCE_CODE = 'STT_LOW_CONFIDENCE'

export type SttFinalPayload = {
  text: string
  confidence: number | null
}

export type AssistantTextFinalPayload = {
  text: string
}

export type AssistantAudioFailedPayload = {
  code: string
  message: string
  recoverable: boolean
}

export type AssistantAudioInterruptedPayload = {
  reason: 'CHILD_BARGE_IN' | 'USER_REQUESTED'
  interruptionId: string
}

export type ClientInterruptPayload = {
  responseId?: string
  reason: 'USER_REQUESTED'
}

export type ConversationSlotsUpdatedPayload = {
  topicType: ConversationTopicType
  slots: Record<string, unknown>
  targetSlot: string | null
  topicQuestionCount: number
  topicMaxQuestionCount: number
}

export type SessionCompletedPayload = {
  reason: string
}

export type ConversationErrorPayload = {
  code: string
  message: string
  recoverable: boolean
}

function readString(payload: Record<string, unknown>, key: string) {
  const value = payload[key]
  return typeof value === 'string' ? value : null
}

function readNumber(payload: Record<string, unknown>, key: string) {
  const value = payload[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

export function readSttFinal(
  payload: Record<string, unknown>,
): SttFinalPayload {
  return {
    text: readString(payload, 'text') ?? '',
    confidence: readNumber(payload, 'confidence'),
  }
}

export function readAssistantText(payload: Record<string, unknown>) {
  return readString(payload, 'text') ?? ''
}

export function readAssistantAudioInterrupted(
  payload: Record<string, unknown>,
): AssistantAudioInterruptedPayload | null {
  const reason = readString(payload, 'reason')
  const interruptionId = readString(payload, 'interruptionId')
  if (
    (reason !== 'CHILD_BARGE_IN' && reason !== 'USER_REQUESTED') ||
    interruptionId === null
  ) {
    return null
  }

  return { interruptionId, reason }
}

export function readSlotsUpdated(
  payload: Record<string, unknown>,
): ConversationSlotsUpdatedPayload | null {
  const topicType = readString(payload, 'topicType')
  const topicQuestionCount = readNumber(payload, 'topicQuestionCount')
  const topicMaxQuestionCount = readNumber(payload, 'topicMaxQuestionCount')
  if (
    (topicType !== 'GAME' && topicType !== 'DAILY') ||
    topicQuestionCount === null ||
    topicMaxQuestionCount === null
  ) {
    return null
  }

  const slots = payload.slots
  return {
    topicType,
    slots:
      slots !== null && typeof slots === 'object'
        ? (slots as Record<string, unknown>)
        : {},
    targetSlot: readString(payload, 'targetSlot'),
    topicQuestionCount,
    topicMaxQuestionCount,
  }
}

export function readSessionCompleted(payload: Record<string, unknown>) {
  return readString(payload, 'reason')
}

export function readConversationError(
  payload: Record<string, unknown>,
): ConversationErrorPayload {
  return {
    code: readString(payload, 'code') ?? 'UNKNOWN',
    message: readString(payload, 'message') ?? '',
    recoverable: payload.recoverable === true,
  }
}
