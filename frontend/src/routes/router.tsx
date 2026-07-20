import { createBrowserRouter } from 'react-router-dom'

import { HomePage } from '@/pages/HomePage'
import { PlaceholderPage } from '@/pages/PlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '/child',
    element: (
      <PlaceholderPage
        eyebrow="Child"
        title="아이 홈"
        description="운동 게임, 인지 게임, 캐릭터 대화 화면을 연결할 자리입니다."
      />
    ),
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
