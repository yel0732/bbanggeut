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

/**
 * 부모 프로필 생성(온보딩)을 아직 마치지 않았는지.
 *
 * 판단 근거는 서버에 핀이 설정돼 있는지 하나다. 예전에는 가입 직후 브라우저에
 * 남긴 표시를 봤는데, 그게 sessionStorage라 창을 닫으면 사라졌다. 그러면 핀도
 * 없는 사람이 프로필 선택 화면으로 넘어가 부모 모드에 영영 못 들어갔다 — 핀이
 * 없으니 인증을 통과할 수 없고, 온보딩으로 돌아갈 길도 없었다.
 *
 * 조회에 실패하면 `false`다. 모르는 것을 "안 했다"로 단정하면 이미 핀이 있는
 * 사람을 온보딩으로 보내고, 거기서 완료를 누르면 서버가 PIN_ALREADY_SET(403)을
 * 돌려준다. 잘못 보내느니 평소 경로로 두는 편이 낫다.
 */
export async function needsParentOnboarding() {
  try {
    const profile = await getParentProfile()
    return !profile.pinSet
  } catch {
    return false
  }
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
