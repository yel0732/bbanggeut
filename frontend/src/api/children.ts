import { authFetch } from '@/api/auth'
import { apiUrl } from '@/api/config'
import {
  loadActiveChildProfileId,
  loadChildProfiles,
} from '@/lib/childProfiles'

export type BackendChild = {
  childId: number
  name: string
}

/**
 * 선택된 아이 프로필을 서버의 childId로 옮긴다.
 *
 * 서버에서 만들어진 프로필은 로컬 id가 숫자 childId 그대로다. 그 밖의(로컬에만
 * 있던) 프로필은 이름으로 서버 목록과 맞춰본다 — 같은 이름이 둘 이상이면 어느
 * 쪽인지 확정할 수 없으므로 추측하지 않고 실패로 둔다.
 */
export async function resolveBackendChildId() {
  const activeProfileId = loadActiveChildProfileId()
  const numericId = Number(activeProfileId)
  if (Number.isSafeInteger(numericId) && numericId > 0) return numericId

  const activeProfile = loadChildProfiles().find(
    (profile) => profile.id === activeProfileId,
  )
  if (!activeProfile) {
    throw new Error('아이 정보를 찾지 못했어요.')
  }

  const response = await authFetch(apiUrl('/api/children'))
  if (!response.ok) {
    throw new Error(
      '아이 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  const children = (await response.json()) as BackendChild[]
  const matches = children.filter((child) => child.name === activeProfile.name)
  if (matches.length !== 1) {
    throw new Error('선택한 아이와 서버의 아이 정보를 연결하지 못했어요.')
  }
  return matches[0].childId
}
