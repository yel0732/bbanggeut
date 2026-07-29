import { createBrowserRouter } from 'react-router-dom'

import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { ParentRouteGuard } from '@/components/auth/ParentRouteGuard'
import {
  ChildHomePage,
  ChildProfileListPage,
  ConversationPage,
  DiaryListPage,
  DobblePlayPage,
  DobbleResultPage,
  EmailLoginPage,
  EmailSignupPage,
  GameEntryPage,
  GameGuidePage,
  GamePlayPage,
  GameResultPage,
  GoogleBirthdatePage,
  ParentModePage,
  ParentProfilePage,
  ProfileSelectPage,
  ReportListPage,
  ReportDetailPage,
  StartPage,
} from '@/routes/lazyPages'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <StartPage />,
  },
  {
    path: '/profiles',
    element: <ProfileSelectPage />,
  },
  {
    path: '/profiles/new',
    element: <ParentProfilePage />,
  },
  {
    path: '/profiles/children',
    element: <ChildProfileListPage />,
  },
  {
    path: '/child',
    element: <ChildHomePage />,
  },
  {
    path: '/games',
    element: <GameEntryPage />,
  },
  {
    path: '/games/:gameId/guide',
    element: <GameGuidePage />,
  },
  // 도블은 양손 포인터/카드 판정이라 플레이·결과 화면을 따로 쓴다.
  {
    path: '/games/dobble/play',
    element: <DobblePlayPage />,
  },
  {
    path: '/games/dobble/result',
    element: <DobbleResultPage />,
  },
  {
    path: '/games/:gameId/play',
    element: <GamePlayPage />,
  },
  {
    path: '/games/:gameId/result',
    element: <GameResultPage />,
  },
  {
    path: '/conversation',
    element: <ConversationPage />,
  },
  {
    path: '/auth',
    element: (
      <PlaceholderPage
        eyebrow="Auth"
        title="부모 모드 인증"
        description="프로필 선택 후 부모 모드로 들어갈 때 거치는 PIN·비밀번호 인증 화면을 연결할 자리입니다."
      />
    ),
  },
  {
    path: '/auth/google',
    element: <GoogleBirthdatePage />,
  },
  {
    path: '/auth/login',
    element: <EmailLoginPage />,
  },
  {
    path: '/auth/signup',
    element: <EmailSignupPage />,
  },
  {
    path: '/diaries',
    element: <DiaryListPage />,
  },
  {
    element: <ParentRouteGuard />,
    children: [
      {
        path: '/parent',
        element: <ParentModePage />,
      },
      {
        path: '/reports',
        element: <ReportListPage />,
      },
      {
        path: '/reports/:reportDate',
        element: <ReportDetailPage />,
      },
    ],
  },
])
