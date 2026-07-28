import { authFetch } from '@/api/auth'
import { apiUrl } from '@/api/config'

export type BackendChildProfile = {
  childId: number
  name: string
  profileImageNum: number
}

type ApiError = { message?: string }

async function requireOk(response: Response, fallback: string) {
  if (response.ok) return
  const error = (await response.json().catch(() => null)) as ApiError | null
  throw new Error(error?.message ?? fallback)
}

export async function getChildProfiles() {
  const response = await authFetch(apiUrl('/api/children'))
  await requireOk(response, '아이 프로필을 불러오지 못했어요.')
  return (await response.json()) as BackendChildProfile[]
}

export async function createChildProfile(name: string, profileImageNum: number) {
  const response = await authFetch(apiUrl('/api/children'), {
    body: JSON.stringify({ name, profileImageNum }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
  await requireOk(response, '아이 프로필을 만들지 못했어요.')
  return (await response.json()) as BackendChildProfile
}

export async function updateChildProfile(
  childId: number,
  name: string,
  profileImageNum: number,
) {
  const response = await authFetch(apiUrl(`/api/children/${childId}`), {
    body: JSON.stringify({ name, profileImageNum }),
    headers: { 'Content-Type': 'application/json' },
    method: 'PATCH',
  })
  await requireOk(response, '아이 프로필을 수정하지 못했어요.')
  return (await response.json()) as BackendChildProfile
}

export async function deleteChildProfile(childId: number, pin: string) {
  const response = await authFetch(apiUrl(`/api/children/${childId}`), {
    body: JSON.stringify({ pin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'DELETE',
  })
  await requireOk(response, '아이 프로필을 삭제하지 못했어요.')
}

export async function setParentPin(pin: string) {
  const response = await authFetch(apiUrl('/api/profile/pin'), {
    body: JSON.stringify({ pin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
  await requireOk(response, '핀 번호를 저장하지 못했어요.')
}
