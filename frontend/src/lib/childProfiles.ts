export type ChildProfile = {
  id: string
  name: string
  photoUrl: string | null
}

export const MAX_CHILD_PROFILES = 10

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
    (profile.photoUrl === null || typeof profile.photoUrl === 'string')
  )
}
