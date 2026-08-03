import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { getStoredAccessToken } from '@/api/auth'
import { clearActiveChildProfileId } from '@/lib/childProfiles'
import { hasParentAccess } from '@/lib/parentAccess'

export function ParentRouteGuard({
  allowOnboarding = false,
}: {
  allowOnboarding?: boolean
}) {
  const location = useLocation()
  const hasToken = Boolean(getStoredAccessToken())
  /*
    부모 프로필 생성 화면은 핀을 만들러 가는 자리다. 핀으로 막으면 들어갈 방법이
    없으니 로그인만 확인한다.

    핀을 이미 만든 사람이 주소로 들어오는 경우는 그 화면이 스스로 돌려보낸다 —
    설정 여부는 서버에 물어야 알 수 있어 여기서는 판단할 수 없다.
  */
  const canContinueOnboarding = allowOnboarding && hasToken
  const canEnter = (hasToken && hasParentAccess()) || canContinueOnboarding

  useEffect(() => {
    if (canEnter) clearActiveChildProfileId()
  }, [canEnter])

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
