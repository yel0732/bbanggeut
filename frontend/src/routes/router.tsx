import { createBrowserRouter } from 'react-router-dom'

import { PlaceholderPage } from '@/pages/PlaceholderPage'
import {
  ChildHomePage,
  ChildProfileListPage,
  EmailLoginPage,
  EmailSignupPage,
  GameEntryPage,
  GameGuidePage,
  GamePlayPage,
  GameResultPage,
  GoogleBirthdatePage,
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
    element: (
      <PlaceholderPage
        eyebrow="Conversation"
        title="캐릭터와 대화하기"
        description="음성 안내, 마이크 입력, 대화 진행 상태를 연결할 자리입니다."
      />
    ),
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
    element: (
      <PlaceholderPage
        eyebrow="Parent"
        title="부모 홈"
        description="리포트 목록, 일기 열람, 아이 프로필 관리 화면을 연결할 자리입니다."
      />
    ),
  },
  {
    path: '/diaries',
    element: (
      <PlaceholderPage
        eyebrow="Diary"
        title="일기 모아보기"
        description="대화 로그와 완성된 일기를 다시 볼 수 있는 화면을 연결할 자리입니다."
      />
    ),
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
