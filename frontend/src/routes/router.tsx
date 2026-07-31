import { createBrowserRouter } from 'react-router-dom'

import { PlaceholderPage } from '@/pages/PlaceholderPage'
import { AdventureBgmLayout } from '@/components/adventure/AdventureBgmLayout'
import { ChildRouteGuard } from '@/components/auth/ChildRouteGuard'
import { ParentRouteGuard } from '@/components/auth/ParentRouteGuard'
import { ConversationBgmLayout } from '@/components/conversation/ConversationBgmLayout'
import { ParentBgmLayout } from '@/components/parent/ParentBgmLayout'
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
  MoreGamesPage,
  ParentModePage,
  ParentProfilePage,
  ProfileSelectPage,
  ReportListPage,
  ReportDetailPage,
  StartPage,
} from '@/routes/lazyPages'

export const router = createBrowserRouter([
  {
    // 시작·인증·프로필·아이 홈·게임 결과 화면은 여기 묶여 하나의 배경 음악을
    // 끊김 없이 이어 재생한다. 실제 게임 플레이·대화·일기장·부모 모드는
    // 각자 다른 음악을 쓰므로 이 레이아웃 밖에 둔다.
    element: <AdventureBgmLayout />,
    children: [
      {
        path: '/',
        element: <StartPage />,
      },
      {
        path: '/profiles',
        element: <ProfileSelectPage />,
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
        element: <ChildRouteGuard />,
        children: [
          {
            path: '/child',
            element: <ChildHomePage />,
          },
          {
            path: '/games',
            element: <GameEntryPage />,
          },
          {
            path: '/games/more',
            element: <MoreGamesPage />,
          },
          {
            path: '/games/dobble/result',
            element: <DobbleResultPage />,
          },
          {
            path: '/games/:gameId/result',
            element: <GameResultPage />,
          },
        ],
      },
      {
        element: <ParentRouteGuard allowOnboarding />,
        children: [
          {
            path: '/profiles/new',
            element: <ParentProfilePage />,
          },
          {
            path: '/profiles/children',
            element: <ChildProfileListPage />,
          },
        ],
      },
    ],
  },
  {
    element: <ChildRouteGuard />,
    children: [
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
        path: '/games/:gameId/play',
        element: <GamePlayPage />,
      },
      {
        element: <ConversationBgmLayout />,
        children: [
          {
            path: '/conversation',
            element: <ConversationPage />,
          },
          {
            path: '/diaries',
            element: <DiaryListPage />,
          },
        ],
      },
    ],
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
    element: <ParentRouteGuard />,
    children: [
      {
        element: <ParentBgmLayout />,
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
    ],
  },
])
