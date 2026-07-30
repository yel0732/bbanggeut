import { Navigate, Outlet } from 'react-router-dom'

import {
  loadActiveChildProfileId,
  loadChildProfiles,
} from '@/lib/childProfiles'

export function ChildRouteGuard() {
  const activeChildProfileId = loadActiveChildProfileId()
  const hasActiveChildProfile = loadChildProfiles().some(
    (profile) => profile.id === activeChildProfileId,
  )

  if (!activeChildProfileId || !hasActiveChildProfile) {
    return <Navigate replace to="/profiles" />
  }

  return <Outlet />
}
