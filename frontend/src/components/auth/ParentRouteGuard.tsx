import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { getStoredAccessToken, hasPendingNewSignupOnboarding } from '@/api/auth'
import { hasParentAccess } from '@/lib/parentAccess'

export function ParentRouteGuard({
  allowOnboarding = false,
}: {
  allowOnboarding?: boolean
}) {
  const location = useLocation()
  const hasToken = Boolean(getStoredAccessToken())
  const canContinueOnboarding =
    allowOnboarding && hasPendingNewSignupOnboarding()
  const canEnter = (hasToken && hasParentAccess()) || canContinueOnboarding

  if (!hasToken && !canContinueOnboarding) {
    return <Navigate replace to="/" />
  }

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
