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
 * ⭐ 시범 단계(두 번째 단계) 공룡 크기 — 이 숫자만 바꾸면 된다.
 *
 * 프레임 높이(px)다. 폭은 원본 비율(512:800)대로 따라오므로 따로 만질 것이 없다.
 *
 * 점선 안내 판은 과일 자르기·그림 맞추기와 같은 360px이다. 테두리 3px 두 줄과
 * 안쪽 여백 12px 두 줄을 빼면 판이 담을 수 있는 높이는 330px이다.
 *
 * - 330 이하 → 판 안에 들어온다
 * - 330 초과 → 판 위아래로 넘쳐 보인다. 잘리지는 않는다(기존 두 게임도 시범
 *   단계는 넘치게 둔다). 어디까지 키울지는 눈으로 보고 정하면 된다.
 */
export const UPCOMING_DEMO_HEIGHT = 420

/**
 * ⭐ 시범 단계 공룡 좌우 위치(px). 양수면 오른쪽, 음수면 왼쪽.
 *
 * 기준은 점선 판의 가운데다. 0이면 판 가운데에 선다.
 */
export const UPCOMING_DEMO_X = 10

/**
 * ⭐ 시범 단계 공룡 상하 위치(px). 양수면 아래, 음수면 위.
 *
 * 기준은 점선 판의 가운데다. 공룡이 판보다 크면 위아래로 넘치는데, 이 값으로
 * 어느 쪽으로 더 넘길지 정한다. 예를 들어 발끝을 판 아래에 맞추고 머리만 위로
 * 넘기고 싶으면 음수를 준다.
 */
export const UPCOMING_DEMO_Y = -35

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
