import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { hasParentAccess } from '@/lib/parentAccess'

export function ParentRouteGuard() {
  const location = useLocation()

  if (!hasParentAccess()) {
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
