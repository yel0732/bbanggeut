export type ChildProfile = {
  id: string
  name: string
  photoUrl: string | null
}

const STORAGE_KEY = 'bbanggut-child-profiles'

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
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(profiles))
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
