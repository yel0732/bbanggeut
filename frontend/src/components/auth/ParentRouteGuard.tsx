import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { hasPendingNewSignupOnboarding } from '@/api/auth'
import { hasParentAccess } from '@/lib/parentAccess'

export function ParentRouteGuard({
  allowOnboarding = false,
}: {
  allowOnboarding?: boolean
}) {
  const location = useLocation()
  const canEnter =
    hasParentAccess() ||
    (allowOnboarding && hasPendingNewSignupOnboarding())

  if (!canEnter) {
    return (
      <Navigate
        replace
        state={{
          openParentPin: true,
          parentRedirectTo: `${location.pathname}${location.search}${location.hash}`,
        }}
        to="/profiles"
      />
    )
  }

  return <Outlet />
}
