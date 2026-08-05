import type { RealtimeSessionResponse } from '@/api/conversations'
import {
  CLIENT_INTERRUPT_EVENT,
  CLIENT_READY_EVENT,
  type ClientInterruptPayload,
  type ConversationEventEnvelope,
  type SignalingIncoming,
  type SignalingOutgoing,
} from '@/lib/realtime/events'

export type RealtimeConnectionPhase =
  'idle' | 'signaling' | 'authenticated' | 'connecting' | 'active' | 'closed'

export type RealtimeFatalCode =
  | 'MIC_PERMISSION_DENIED'
  | 'SIGNALING_CLOSED'
  | 'WEBRTC_CONNECTION_FAILED'
  | 'OFFER_FAILED'
  | (string & {})

export type RealtimeClientCallbacks = {
  onPhaseChange: (phase: RealtimeConnectionPhase) => void
  onConversationEvent: (envelope: ConversationEventEnvelope) => void
  onLocalStream: (stream: MediaStream) => void
  onRemoteStream: (stream: MediaStream) => void
  onFatal: (code: RealtimeFatalCode, message: string) => void
}

/** 서버가 이 이름의 채널만 대화 이벤트 채널로 받는다. */
const DATA_CHANNEL_LABEL = 'conversation-events'

/*
  ADR-020 클라이언트 오디오 요구사항. 브라우저 기본값에 기대지 않고 명시한다.
  echoCancellation은 AI 자기 음성이 다시 들어가 Barge-in으로 오인되는 것을 막고,
  noiseSuppression은 서버 VAD의 턴 종료 판정을 배경음에서 보호하며,
  autoGainControl은 작게 말하는 아이의 발화를 끌어올린다.
*/
const MICROPHONE_CONSTRAINTS: MediaTrackConstraints = {
  autoGainControl: true,
  echoCancellation: true,
  noiseSuppression: true,
}

function randomId(prefix: string) {
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().replace(/-/g, '')
      : `${Math.random().toString(16).slice(2)}${Date.now().toString(16)}`
  return `${prefix}_${random}`
}

/**
 * https 페이지에서 ws:// 연결은 브라우저가 무조건 차단한다. 서버 설정이 평문
 * URL을 내려줘도 최소한 연결은 시도할 수 있게 프로토콜만 맞춰준다.
 */
function alignSignalingProtocol(signalingUrl: string) {
  if (
    typeof window !== 'undefined' &&
    window.location.protocol === 'https:' &&
    signalingUrl.startsWith('ws://')
  ) {
    return `wss://${signalingUrl.slice('ws://'.length)}`
  }
  return signalingUrl
}

/**
 * 대화 한 번의 Signaling·PeerConnection·DataChannel 수명을 관리한다.
 *
 * React와 무관하게 동작한다 — 리스너가 여럿이고 정리 순서가 중요해서, 훅의
 * 클로저 안에 두면 오래된 참조를 붙잡기 쉽다. 상태 반영은 콜백으로만 넘긴다.
 */
export class RealtimeConversationClient {
  private readonly session: RealtimeSessionResponse
  private readonly callbacks: RealtimeClientCallbacks

  private socket: WebSocket | null = null
  private peerConnection: RTCPeerConnection | null = null
  private dataChannel: RTCDataChannel | null = null
  private localStream: MediaStream | null = null

  private phase: RealtimeConnectionPhase = 'idle'
  private eventSeq = 0
  private disposed = false

  constructor(
    session: RealtimeSessionResponse,
    callbacks: RealtimeClientCallbacks,
  ) {
    this.session = session
    this.callbacks = callbacks
  }

  connect() {
    if (this.disposed || this.socket !== null) return

    const socket = new WebSocket(
      alignSignalingProtocol(this.session.signalingUrl),
    )
    this.socket = socket
    this.setPhase('signaling')

    socket.addEventListener('open', () => {
      this.sendSignaling({
        requestId: randomId('req'),
        ticket: this.session.realtimeTicket,
        type: 'session.authenticate',
      })
    })

    socket.addEventListener('message', (event) => {
      let message: SignalingIncoming
      try {
        message = JSON.parse(String(event.data)) as SignalingIncoming
      } catch {
        return
      }
      void this.handleSignaling(message)
    })

    socket.addEventListener('close', () => {
      if (this.disposed) return
      // 대화가 붙기 전에 닫혔다면 인증·Offer 단계에서 거절된 것이다. 붙은 뒤라면
      // 서버가 연결을 정리했다는 뜻이라 어느 쪽이든 이 연결로는 더 못 간다.
      this.callbacks.onFatal(
        'SIGNALING_CLOSED',
        '대화 서버와의 연결이 끊어졌어요.',
      )
    })

    socket.addEventListener('error', () => {
      // 상세 사유는 오지 않는다. close가 이어지므로 여기서는 알리지 않는다.
    })
  }

  setMicEnabled(enabled: boolean) {
    this.localStream?.getAudioTracks().forEach((track) => {
      track.enabled = enabled
    })
  }

  interruptAssistant(responseId?: string) {
    const payload: ClientInterruptPayload = {
      reason: 'USER_REQUESTED',
      ...(responseId === undefined ? {} : { responseId }),
    }
    this.sendConversationEvent(CLIENT_INTERRUPT_EVENT, payload)
  }

  /** 여러 번 불러도 안전하다. */
  close() {
    if (this.disposed) return
    this.disposed = true

    if (this.dataChannel !== null) {
      try {
        this.dataChannel.close()
      } catch {
        // 이미 닫힌 채널이면 그만이다.
      }
      this.dataChannel = null
    }

    if (this.peerConnection !== null) {
      try {
        this.peerConnection.close()
      } catch {
        // 위와 같다.
      }
      this.peerConnection = null
    }

    if (this.socket !== null) {
      try {
        this.socket.close()
      } catch {
        // 위와 같다.
      }
      this.socket = null
    }

    // 트랙 정리는 마지막이다 — 브라우저의 마이크 사용 표시가 여기서 꺼진다.
    this.localStream?.getTracks().forEach((track) => track.stop())
    this.localStream = null

    this.setPhase('closed')
  }

  private setPhase(phase: RealtimeConnectionPhase) {
    if (this.phase === phase) return
    this.phase = phase
    this.callbacks.onPhaseChange(phase)
  }

  private sendSignaling(message: SignalingOutgoing) {
    if (this.socket?.readyState !== WebSocket.OPEN) return
    this.socket.send(JSON.stringify(message))
  }

  private async handleSignaling(message: SignalingIncoming) {
    if (this.disposed) return

    switch (message.type) {
      case 'session.authenticated':
        this.setPhase('authenticated')
        await this.startPeerConnection()
        break
      case 'rtc.answer':
        await this.peerConnection?.setRemoteDescription({
          sdp: message.sdp.sdp,
          type: 'answer',
        })
        break
      case 'rtc.ice-candidate':
        // aiortc는 후보를 Answer SDP에 담아 보내므로 실제로는 오지 않는다.
        // 계약에 있는 메시지라 방어적으로만 처리한다.
        if (message.candidate) {
          await this.peerConnection?.addIceCandidate(message.candidate)
        }
        break
      case 'rtc.connected':
        this.setPhase('active')
        break
      case 'rtc.error':
        this.callbacks.onFatal(message.code, message.message)
        break
      default:
        break
    }
  }

  private async startPeerConnection() {
    try {
      this.localStream = await navigator.mediaDevices.getUserMedia({
        audio: MICROPHONE_CONSTRAINTS,
      })
    } catch {
      this.callbacks.onFatal(
        'MIC_PERMISSION_DENIED',
        '대화를 시작하려면 마이크 사용을 허용해 주세요.',
      )
      return
    }
    if (this.disposed) {
      // 권한 대화상자를 띄운 사이에 화면을 떠났다면 마이크를 바로 놓아준다.
      this.localStream.getTracks().forEach((track) => track.stop())
      this.localStream = null
      return
    }
    this.callbacks.onLocalStream(this.localStream)

    const peerConnection = new RTCPeerConnection(
      this.session.iceServers.length > 0
        ? { iceServers: [{ urls: this.session.iceServers }] }
        : undefined,
    )
    this.peerConnection = peerConnection
    this.setPhase('connecting')

    const localStream = this.localStream
    localStream
      .getTracks()
      .forEach((track) => peerConnection.addTrack(track, localStream))

    peerConnection.addEventListener('icecandidate', (event) => {
      if (event.candidate) {
        this.sendSignaling({
          candidate: {
            candidate: event.candidate.candidate,
            sdpMLineIndex: event.candidate.sdpMLineIndex,
            sdpMid: event.candidate.sdpMid,
          },
          requestId: randomId('req'),
          type: 'rtc.ice-candidate',
        })
        return
      }
      this.sendSignaling({
        requestId: randomId('req'),
        type: 'rtc.ice-complete',
      })
    })

    peerConnection.addEventListener('track', (event) => {
      const [stream] = event.streams
      if (stream) this.callbacks.onRemoteStream(stream)
    })

    peerConnection.addEventListener('connectionstatechange', () => {
      if (this.disposed) return
      if (peerConnection.connectionState === 'failed') {
        this.callbacks.onFatal(
          'WEBRTC_CONNECTION_FAILED',
          '대화 연결이 끊어졌어요.',
        )
      }
    })

    // DataChannel은 Offer를 만들기 전에 열어야 SDP에 실린다. 채널을 여는 쪽은
    // 프론트다 — 서버는 받은 채널에 대화 이벤트를 흘려보낸다.
    this.attachDataChannel(
      peerConnection.createDataChannel(DATA_CHANNEL_LABEL, { ordered: true }),
    )

    try {
      const offer = await peerConnection.createOffer()
      await peerConnection.setLocalDescription(offer)
      this.sendSignaling({
        requestId: randomId('req'),
        sdp: { sdp: offer.sdp ?? '', type: 'offer' },
        type: 'rtc.offer',
      })
    } catch {
      this.callbacks.onFatal('OFFER_FAILED', '대화 연결을 시작하지 못했어요.')
    }
  }

  private attachDataChannel(channel: RTCDataChannel) {
    this.dataChannel = channel

    channel.addEventListener('open', () => {
      this.sendConversationEvent(CLIENT_READY_EVENT, {
        localVADReady: false,
        microphoneReady: true,
        remoteAudioReady: true,
      })
    })

    channel.addEventListener('message', (event) => {
      let envelope: ConversationEventEnvelope
      try {
        envelope = JSON.parse(String(event.data)) as ConversationEventEnvelope
      } catch {
        return
      }
      // 다른 대화·다른 실시간 세션의 이벤트는 받아도 쓰지 않는다(서버의 폐기
      // 규칙과 같은 방향의 최소 가드).
      if (
        envelope.conversationId !== this.session.conversationId ||
        envelope.realtimeSessionId !== this.session.realtimeSessionId
      ) {
        return
      }
      this.callbacks.onConversationEvent(envelope)
    })
  }

  private sendConversationEvent(
    type: string,
    payload: Record<string, unknown>,
  ) {
    if (this.dataChannel?.readyState !== 'open') return
    this.eventSeq += 1
    this.dataChannel.send(
      JSON.stringify({
        conversationId: this.session.conversationId,
        eventId: randomId('evt'),
        eventSeq: this.eventSeq,
        occurredAt: new Date().toISOString(),
        payload,
        realtimeSessionId: this.session.realtimeSessionId,
        sessionEpoch: this.session.sessionEpoch,
        type,
      }),
    )
  }
}
