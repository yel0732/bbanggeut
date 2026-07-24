import { createBrowserRouter } from 'react-router-dom'

import { PlaceholderPage } from '@/pages/PlaceholderPage'
import {
  ChildHomePage,
  ChildProfileListPage,
  ConversationPage,
  DiaryListPage,
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
    path: '/parent',
    element: <ParentModePage />,
  },
  {
    path: '/diaries',
    element: <DiaryListPage />,
  },
  {
    path: '/reports',
    element: (
      <PlaceholderPage
        eyebrow="Report"
        title="성장 리포트"
        description="게임 결과와 일기를 종합한 부모 리포트 화면을 연결할 자리입니다."
      />
    ),
  },
])
