import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import childTigerAvatarUrl from '@/assets/images/profile/child-avatar-tiger.webp'
import childRabbitAvatarUrl from '@/assets/images/profile/child-avatar-rabbit.webp'

export type ChildProfile = {
  id: string
  name: string
  photoUrl: string | null
  profileImageNum?: number
}

export const MAX_CHILD_PROFILES = 10

export function childPhotoFromImageNum(profileImageNum: number) {
  if (profileImageNum === 2) return childTigerAvatarUrl
  if (profileImageNum === 3) return childRabbitAvatarUrl
  return null
}

export function childImageNumFromPhoto(
  photoUrl: string | null,
  fallback = 1,
) {
  if (photoUrl === childAvatarUrl || photoUrl === null) return 1
  if (photoUrl === childTigerAvatarUrl) return 2
  if (photoUrl === childRabbitAvatarUrl) return 3
  return fallback
}

const STORAGE_KEY = 'bbanggut-child-profiles'
const ACTIVE_PROFILE_KEY = 'bbanggut-active-child-profile-id'

export function loadChildProfiles(): ChildProfile[] {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (!stored) return []
    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isChildProfile)
  } catch {
    return []
  }
}

export function saveChildProfiles(profiles: ChildProfile[]) {
  if (profiles.length > MAX_CHILD_PROFILES) return false
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(profiles))
  return true
}

export function loadActiveChildProfileId() {
  return window.localStorage.getItem(ACTIVE_PROFILE_KEY)
}

export function saveActiveChildProfileId(profileId: string) {
  window.localStorage.setItem(ACTIVE_PROFILE_KEY, profileId)
}

function isChildProfile(value: unknown): value is ChildProfile {
  if (!value || typeof value !== 'object') return false
  const profile = value as Partial<ChildProfile>
  return (
    typeof profile.id === 'string' &&
    typeof profile.name === 'string' &&
    (profile.photoUrl === null || typeof profile.photoUrl === 'string') &&
    (profile.profileImageNum === undefined ||
      (typeof profile.profileImageNum === 'number' &&
        Number.isInteger(profile.profileImageNum) &&
        profile.profileImageNum > 0))
  )
}
