import { authFetch } from '@/api/auth'
import { apiUrl } from '@/api/config'

export type ConversationState = 'ACTIVE' | 'COMPLETED' | 'TERMINATED'

type CreateConversationResponse = {
  conversationId: string
  status: ConversationState
}

type TextTurnResponse = {
  assistantTurn: { content: string } | null
  conversationState: ConversationState
}

async function conversationRequest<T>(path: string, init?: RequestInit) {
  const response = await authFetch(apiUrl(path), init)
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as {
      message?: string
    } | null
    throw new Error(
      error?.message ?? `대화 요청에 실패했어요. (${response.status})`,
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

export function sendConversationText(conversationId: string, text: string) {
  return conversationRequest<TextTurnResponse>(
    `/api/v1/conversations/${conversationId}/text-turns`,
    {
      body: JSON.stringify({ text }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    },
  )
}

export function completeConversationSession(conversationId: string) {
  return conversationRequest<{ status: ConversationState }>(
    `/api/v1/conversations/${conversationId}/complete`,
    {
      body: JSON.stringify({ reason: 'USER_FINISHED' }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    },
  )
}
