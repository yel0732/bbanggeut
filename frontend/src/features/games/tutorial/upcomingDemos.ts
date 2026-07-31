import recycleDemoUrl from '@/assets/images/tutorial/recycle/recycle-clean-demo.webp'
import wordmatchDemoUrl from '@/assets/images/tutorial/wordmatch/wordmatch-demo.webp'
import type { GameId } from '@/features/games/games'

/**
 * 나중에 추가한 게임의 시범 동작 그림.
 *
 * 기존 두 게임은 1024x1024 알파 WebM을 `<video>`로 돌리지만, 이 에셋은 512x800
 * 알파 애니메이션 WebP라 `<img>`로 붙인다 — WebP 애니메이션은 img에서 알아서
 * 반복 재생되고 muted·autoPlay를 챙길 필요가 없다.
 *
 * 컴포넌트가 아닌 값과 함수만 두는 파일이다. 그림 자체를 그리는 컴포넌트는
 * `TutorialMedia.tsx`에 있다.
 */

const SOURCE = { width: 512, height: 800 } as const

/**
 * 시범 단계에서 공룡 프레임을 그릴 높이(px).
 *
 * 점선 안내 판은 360px인데 테두리 3px 두 줄과 안쪽 여백 12px 두 줄을 빼면 담을
 * 수 있는 높이가 330px이다. 기존 두 게임의 시범은 판 밖으로 넘치게 두지만, 이
 * 그림은 세로로 길어 넘치면 보드 밖까지 삐져나온다. 그래서 330px을 넘기지
 * 않는 선에서 최대한 키운다.
 */
export const UPCOMING_DEMO_HEIGHT = 328

export function upcomingDemoWidth(height: number): number {
  return (height * SOURCE.width) / SOURCE.height
}

export const UPCOMING_DEMOS: Partial<
  Record<GameId, { label: string; url: string }>
> = {
  wordmatch: {
    label: '빵긋이 낱말 카드를 골라 맞추는 모습',
    url: wordmatchDemoUrl,
  },
  recycle: {
    label: '빵긋이 숲에 떨어진 쓰레기를 주워 담는 모습',
    url: recycleDemoUrl,
  },
}

/** 이 게임에 시범 동작 그림이 있는지. 없으면 부르는 쪽이 대신 보여 줄 것을 고른다. */
export function hasUpcomingDemo(gameId: GameId): boolean {
  return gameId in UPCOMING_DEMOS
}
