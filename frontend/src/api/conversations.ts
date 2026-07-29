import { authFetch, getStoredAccessToken } from '@/api/auth'
import { apiUrl } from '@/api/config'

export type ConversationTopicType = 'GAME' | 'DAILY'

export type ConversationStatus =
  'READY' | 'IN_PROGRESS' | 'COMPLETED' | 'ABORTED' | 'EXPIRED' | 'FAILED'

export type ConversationTopicStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

/**
 * 대화를 끝까지 마쳤을 때만 나오는 종료 사유.
 *
 * 이 두 사유로 끝난 대화만 원문·슬롯이 남고 일기를 만들 수 있다. 그 밖의 종료는
 * 서버가 대화 내용을 정리한다. 완주 선언은 대화 엔진만 할 수 있어서 프론트가
 * 이 사유로 종료를 요청하면 서버가 400으로 막는다.
 */
export const COMPLETED_TERMINATION_REASONS = [
  'REQUIRED_SLOTS_FILLED',
  'MAX_QUESTION_REACHED',
] as const

export type CompletedTerminationReason =
  (typeof COMPLETED_TERMINATION_REASONS)[number]

/** 프론트가 보낼 수 있는 종료 사유. */
export type ClientTerminationReason =
  'USER_FINISHED' | 'USER_ABANDONED' | 'PAGE_CLOSED' | 'CONNECTION_LOST'

export function isCompletedTerminationReason(
  reason: string | null | undefined,
): reason is CompletedTerminationReason {
  return COMPLETED_TERMINATION_REASONS.some((value) => value === reason)
}

export type ConversationTopic = {
  type: ConversationTopicType
  order: number
  status: ConversationTopicStatus
  questionCount: number
  maxQuestionCount: number
  completionReason: string | null
}

export type CreateConversationResponse = {
  conversationId: string
  childId: number
  status: ConversationStatus
  currentTopic: ConversationTopicType
  totalQuestionCount: number
  topics: ConversationTopic[]
  createdAt: string
}

export type RealtimeSessionResponse = {
  conversationId: string
  realtimeSessionId: string
  sessionEpoch: number
  signalingUrl: string
  realtimeTicket: string
  expiresAt: string
  iceServers: string[]
}

export type CompleteConversationResponse = {
  conversationId: string
  status: ConversationStatus
  questionCount: number
  terminationReason: string | null
  completedAt: string | null
}

/** 서버 ErrorResponse의 `error`(에러 코드)를 그대로 들고 있는 예외. */
export class ConversationApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ConversationApiError'
    this.code = code
    this.status = status
  }
}

type ErrorResponse = {
  error?: string
  message?: string
}

async function conversationRequest<T>(path: string, init?: RequestInit) {
  const response = await authFetch(apiUrl(path), init)
  if (!response.ok) {
    const body = (await response
      .json()
      .catch(() => null)) as ErrorResponse | null
    throw new ConversationApiError(
      response.status,
      body?.error ?? 'UNKNOWN',
      body?.message ?? `요청에 실패했어요. (${response.status})`,
    )
  }
  return (await response.json()) as T
}

export function createConversation(childId: number) {
  return conversationRequest<CreateConversationResponse>(
    `/api/v1/children/${childId}/conversations`,
    { method: 'POST' },
  )
}

export function issueRealtimeSession(conversationId: string) {
  return conversationRequest<RealtimeSessionResponse>(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/realtime-session`,
    { method: 'POST' },
  )
}

/**
 * 대화를 중도 종료한다.
 *
 * 완주는 대화 엔진이 스스로 선언하므로 이 API는 이탈 경로에서만 쓴다. 이미
 * 끝난 대화에 다시 불러도 서버가 기존 상태를 그대로 돌려주므로(멱등) 종료
 * 경로가 겹쳐도 안전하다.
 */
export function completeConversation(
  conversationId: string,
  reason: ClientTerminationReason,
) {
  return conversationRequest<CompleteConversationResponse>(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/complete`,
    {
      body: JSON.stringify({ reason }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    },
  )
}

/**
 * 문서가 사라지는 순간(pagehide)에 보내는 종료 통지.
 *
 * `authFetch`는 토큰 갱신을 기다릴 수 있어 이 시점에는 완주를 보장하지 못한다.
 * 저장된 토큰을 그대로 붙여 `keepalive`로 한 번만 던진다. 실패해도 서버의
 * Lease 만료 정리가 남아 있으므로 이 호출이 유일한 방어선은 아니다.
 */
export function completeConversationOnUnload(
  conversationId: string,
  reason: ClientTerminationReason,
) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const accessToken = getStoredAccessToken()
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  try {
    void fetch(
      apiUrl(
        `/api/v1/conversations/${encodeURIComponent(conversationId)}/complete`,
      ),
      {
        body: JSON.stringify({ reason }),
        headers,
        keepalive: true,
        method: 'POST',
      },
    ).catch(() => undefined)
  } catch {
    // 페이지가 사라지는 중이라 실패를 알릴 방법도, 되돌릴 방법도 없다.
  }
}
