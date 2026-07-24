import { lazy } from 'react'

export const StartPage = lazy(() =>
  import('@/pages/StartPage').then(({ StartPage }) => ({ default: StartPage })),
)
export const EmailLoginPage = lazy(() =>
  import('@/pages/EmailLoginPage').then(({ EmailLoginPage }) => ({
    default: EmailLoginPage,
  })),
)
export const EmailSignupPage = lazy(() =>
  import('@/pages/EmailSignupPage').then(({ EmailSignupPage }) => ({
    default: EmailSignupPage,
  })),
)
export const ProfileSelectPage = lazy(() =>
  import('@/pages/ProfileSelectPage').then(({ ProfileSelectPage }) => ({
    default: ProfileSelectPage,
  })),
)
export const ChildHomePage = lazy(() =>
  import('@/pages/ChildHomePage').then(({ ChildHomePage }) => ({
    default: ChildHomePage,
  })),
)
export const GameEntryPage = lazy(() =>
  import('@/pages/GameEntryPage').then(({ GameEntryPage }) => ({
    default: GameEntryPage,
  })),
)
export const GameGuidePage = lazy(() =>
  import('@/pages/GameGuidePage').then(({ GameGuidePage }) => ({
    default: GameGuidePage,
  })),
)
export const GamePlayPage = lazy(() =>
  import('@/pages/GamePlayPage').then(({ GamePlayPage }) => ({
    default: GamePlayPage,
  })),
)
