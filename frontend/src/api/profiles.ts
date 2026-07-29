import { authFetch, readApiError } from '@/api/auth'
import { apiUrl } from '@/api/config'

export type BackendChildProfile = {
  childId: number
  name: string
  profileImageKey: string
  profileImageUrl: string | null
}

export type BackendProfileImage = {
  profileImageKey: string
  profileImageUrl: string | null
}

export type BackendParentProfile = {
  parentId: number
  email: string
  profileImageKey: string
  profileImageUrl: string | null
  pinSet: boolean
}

async function requireOk(response: Response, fallback: string) {
  if (response.ok) return
  throw await readApiError(response, fallback)
}

export async function getChildProfiles() {
  const response = await authFetch(apiUrl('/api/children'))
  await requireOk(response, '아이 프로필을 불러오지 못했어요.')
  return (await response.json()) as BackendChildProfile[]
}

export async function getParentProfile() {
  const response = await authFetch(apiUrl('/api/profile'))
  await requireOk(response, '부모 프로필을 불러오지 못했어요.')
  return (await response.json()) as BackendParentProfile
}

export async function createChildProfile(name: string, imageKey?: string) {
  const response = await authFetch(apiUrl('/api/children'), {
    body: JSON.stringify(imageKey ? { name, imageKey } : { name }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
  await requireOk(response, '아이 프로필을 만들지 못했어요.')
  return (await response.json()) as BackendChildProfile
}

export async function updateChildProfile(
  childId: number,
  name: string,
  imageKey: string,
) {
  const response = await authFetch(apiUrl(`/api/children/${childId}`), {
    body: JSON.stringify({ name, imageKey }),
    headers: { 'Content-Type': 'application/json' },
    method: 'PATCH',
  })
  await requireOk(response, '아이 프로필을 수정하지 못했어요.')
  return (await response.json()) as BackendChildProfile
}

export async function uploadChildProfileImage(childId: number, file: Blob) {
  const formData = new FormData()
  formData.append('file', file, 'child-profile.png')
  const response = await authFetch(apiUrl(`/api/children/${childId}/image`), {
    body: formData,
    method: 'POST',
  })
  await requireOk(response, '아이 프로필 사진을 저장하지 못했어요.')
  return (await response.json()) as BackendChildProfile
}

export async function deleteChildProfile(childId: number, pin: string) {
  const response = await authFetch(apiUrl(`/api/children/${childId}`), {
    body: JSON.stringify({ pin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'DELETE',
    skipUnauthorizedRetry: true,
  })
  await requireOk(response, '아이 프로필을 삭제하지 못했어요.')
}

export async function setParentPin(pin: string) {
  const response = await authFetch(apiUrl('/api/profile/pin'), {
    body: JSON.stringify({ pin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
  await requireOk(response, 'PIN 번호를 저장하지 못했어요.')
}

export async function changeParentPin(currentPin: string, newPin: string) {
  const response = await authFetch(apiUrl('/api/profile/pin'), {
    body: JSON.stringify({ currentPin, newPin, newPinConfirm: newPin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'PATCH',
  })
  await requireOk(response, 'PIN을 변경하지 못했어요.')
}

export async function updateParentProfileImage(imageKey: string) {
  const response = await authFetch(apiUrl('/api/profile/image'), {
    body: JSON.stringify({ imageKey }),
    headers: { 'Content-Type': 'application/json' },
    method: 'PATCH',
  })
  await requireOk(response, '부모 프로필 사진을 변경하지 못했어요.')
  return (await response.json()) as BackendProfileImage
}

export async function uploadParentProfileImage(file: Blob) {
  const formData = new FormData()
  formData.append('file', file, 'parent-profile.png')
  const response = await authFetch(apiUrl('/api/profile/image/upload'), {
    body: formData,
    method: 'POST',
  })
  await requireOk(response, '부모 프로필 사진을 저장하지 못했어요.')
  return (await response.json()) as BackendProfileImage
}
