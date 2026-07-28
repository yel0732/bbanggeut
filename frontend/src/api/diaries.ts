import { authFetch } from '@/api/auth'
import { apiUrl } from '@/api/config'

export type ChildDiary = {
  childDiaryId: number
  childId: number
  conversationId: string
  diaryDate: string
  title: string
  lines: string[]
  createdAt: string
}

export type ChildDiaryListResponse = {
  items: ChildDiary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ChildDiaryPreview = {
  conversationId: string
  diaryDate: string
  title: string
  lines: string[]
  previewToken: string
}

async function diaryRequest<T>(path: string, init?: RequestInit) {
  const response = await authFetch(apiUrl(path), init)
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as {
      message?: string
    } | null
    throw new Error(
      error?.message ?? `일기 요청에 실패했어요. (${response.status})`,
    )
  }
  return (await response.json()) as T
}

export function previewChildDiary(childId: number, conversationId: string) {
  return diaryRequest<ChildDiaryPreview>(
    `/api/v1/children/${childId}/conversations/${conversationId}/child-diary/preview`,
    { method: 'POST' },
  )
}

export function saveChildDiary(childId: number, previewToken: string) {
  return diaryRequest<ChildDiary>(`/api/v1/children/${childId}/child-diaries`, {
    body: JSON.stringify({ previewToken }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export async function getChildDiaries(childId: number, page = 0, size = 100) {
  const response = await authFetch(
    apiUrl(
      `/api/v1/children/${childId}/child-diaries?page=${page}&size=${size}`,
    ),
  )
  if (!response.ok) {
    throw new Error(`일기 목록을 불러오지 못했어요. (${response.status})`)
  }
  return (await response.json()) as ChildDiaryListResponse
}
