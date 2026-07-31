import type { ReactNode } from 'react'

import celebrationUrl from '@/assets/images/tutorial/common/common-celebration.webm'
import greetingUrl from '@/assets/images/tutorial/common/common-greeting.webm'
import dobbleDemoUrl from '@/assets/images/tutorial/dobble/dobble-open-fist-demo.webm'
import handsFistUrl from '@/assets/images/tutorial/dobble/dino-hands-fist.png'
import handsOpenUrl from '@/assets/images/tutorial/dobble/dino-hands-open.png'
import fruitDemoUrl from '@/assets/images/tutorial/fruit/fruit-slice-demo.webm'
import fruitPosesUrl from '@/assets/images/tutorial/fruit/dino-fruit-left-arm-2poses-low.png'
import type { GameId } from '@/features/games/games'

import {
  UPCOMING_DEMO_HEIGHT,
  UPCOMING_DEMO_X,
  UPCOMING_DEMO_Y,
  UPCOMING_DEMOS,
  upcomingDemoWidth,
} from './upcomingDemos'

/**
 * 튜토리얼 그림·영상.
 *
 * 영상은 알파 채널이 있는 VP9 WebM이라 배경 없이 빵긋만 떠 있는다. 나무 보드
 * 위에 그대로 얹어도 사각 테두리가 보이지 않는다.
 *
 * 알파 WebM은 Chromium·Firefox에서 재생된다. Safari는 알파를 무시해 검은
 * 배경이 보일 수 있어, 영상이 뜨기 전/실패했을 때를 대비해 정지 그림을
 * poster로 깔아 둔다.
 */

/** 과일 포즈 시트: 887px 정사각 두 컷이 가로로 붙어 있다. */
const FRUIT_POSE_FRAME = 887
const FRUIT_POSE_COUNT = 2

export type FruitPose = 'raise-arm' | 'swing'

const FRUIT_POSE_INDEX: Record<FruitPose, number> = {
  'raise-arm': 0,
  swing: 1,
}

/**
 * 알파 영상 재생기.
 *
 * 튜토리얼 영상은 소리가 없고 계속 돌아야 하므로 muted·loop·autoplay를 묶어
 * 둔다. muted가 빠지면 브라우저가 자동 재생을 막는다.
 */
export function TutorialVideo({
  label,
  poster,
  scale = 1,
  size,
  src,
}: {
  label: string
  poster?: string
  scale?: number
  size: number
  src: string
}) {
  return (
    <video
      aria-label={label}
      autoPlay
      className="block shrink-0 object-contain drop-shadow-[0_10px_16px_rgba(60,40,15,0.24)]"
      disablePictureInPicture
      loop
      muted
      playsInline
      poster={poster}
      preload="auto"
      role="img"
      src={src}
      style={{
        width: size,
        height: size,
        transform: `scale(${scale})`,
        transformOrigin: 'center',
      }}
    />
  )
}

/** 인사하는 빵긋 (소개 단계). */
export function GreetingVideo({ size }: { size: number }) {
  return (
    <TutorialVideo
      label="빵긋이 손을 흔들며 인사해요"
      size={size}
      src={greetingUrl}
    />
  )
}

/** 축하하는 빵긋 (시작 단계). */
export function CelebrationVideo({
  scale,
  size,
}: {
  scale?: number
  size: number
}) {
  return (
    <TutorialVideo
      label="빵긋이 신나게 축하해요"
      scale={scale}
      size={size}
      src={celebrationUrl}
    />
  )
}

/** 팔을 칼처럼 휘둘러 과일을 자르는 시범. */
export function FruitSliceVideo({
  scale,
  size,
}: {
  scale?: number
  size: number
}) {
  return (
    <TutorialVideo
      label="빵긋이 팔을 휘둘러 과일을 자르는 모습"
      scale={scale}
      size={size}
      src={fruitDemoUrl}
    />
  )
}

/** 손을 펴서 가져간 뒤 주먹을 쥐는 시범. */
export function DobbleGrabVideo({
  scale,
  size,
}: {
  scale?: number
  size: number
}) {
  return (
    <TutorialVideo
      label="빵긋이 손을 폈다가 주먹을 쥐는 모습"
      scale={scale}
      size={size}
      src={dobbleDemoUrl}
    />
  )
}

/** 과일 게임 포즈 컷. 시트에서 한 컷만 잘라 보여 준다. */
export function FruitPose({ pose, size }: { pose: FruitPose; size: number }) {
  const index = FRUIT_POSE_INDEX[pose]

  return (
    <div
      aria-label={
        pose === 'raise-arm'
          ? '빵긋이 한 팔을 번쩍 든 모습'
          : '빵긋이 팔을 휘두르는 모습'
      }
      className="block shrink-0 bg-no-repeat drop-shadow-[0_10px_16px_rgba(60,40,15,0.24)]"
      role="img"
      style={{
        width: size,
        height: size,
        backgroundImage: `url(${fruitPosesUrl})`,
        backgroundSize: `${size * FRUIT_POSE_COUNT}px ${size}px`,
        backgroundPosition: `${-index * size}px 0`,
      }}
    />
  )
}

/** 도블 게임 손 포즈. 펼친 손 / 주먹. */
export function DobbleHands({
  pose,
  size,
}: {
  pose: 'open' | 'fist'
  size: number
}) {
  const open = pose === 'open'

  return (
    <img
      alt={open ? '빵긋이 두 손을 펼친 모습' : '빵긋이 두 주먹을 쥔 모습'}
      className="block shrink-0 object-contain drop-shadow-[0_10px_16px_rgba(60,40,15,0.24)]"
      decoding="async"
      draggable={false}
      src={open ? handsOpenUrl : handsFistUrl}
      style={{ height: size }}
    />
  )
}

/** 포즈 시트 한 컷의 원본 크기 — 배치 계산이 필요할 때 쓴다. */
export const FRUIT_POSE_SOURCE_SIZE = FRUIT_POSE_FRAME

/**
 * 나중에 추가한 게임의 시범 동작 그림.
 *
 * 에셋이 없는 게임이면 `fallback`을 그린다. 부르는 쪽이 에셋 유무를 따로
 * 물어보지 않아도 되게 여기서 갈라 준다.
 *
 * 크기와 위치는 `upcomingDemos.ts`의 `UPCOMING_DEMO_HEIGHT`·`_X`·`_Y`로
 * 조절한다. 위치를 여기서 적용하므로 부르는 쪽은 감싸지 않아도 된다.
 */
export function UpcomingGameDemo({
  fallback = null,
  gameId,
  height = UPCOMING_DEMO_HEIGHT,
  x = UPCOMING_DEMO_X,
  y = UPCOMING_DEMO_Y,
}: {
  fallback?: ReactNode
  gameId: GameId
  height?: number
  x?: number
  y?: number
}) {
  const demo = UPCOMING_DEMOS[gameId]

  if (!demo) {
    return fallback
  }

  return (
    <img
      alt={demo.label}
      className="block shrink-0 object-contain drop-shadow-[0_10px_16px_rgba(60,40,15,0.24)]"
      decoding="async"
      draggable={false}
      src={demo.url}
      style={{
        height,
        width: upcomingDemoWidth(height),
        transform: `translate(${x}px, ${y}px)`,
      }}
    />
  )
}
