import appleUrl from '@/assets/images/fruit-game/apple.webp'
import bananaUrl from '@/assets/images/fruit-game/banana.webp'
import coconutUrl from '@/assets/images/fruit-game/coconut.webp'
import grapeUrl from '@/assets/images/fruit-game/grape.webp'
import kiwiUrl from '@/assets/images/fruit-game/kiwi.webp'
import mangoUrl from '@/assets/images/fruit-game/mango.webp'
import orangeUrl from '@/assets/images/fruit-game/orange.webp'
import pineappleUrl from '@/assets/images/fruit-game/pineapple.webp'
import strawberryUrl from '@/assets/images/fruit-game/strawberry.webp'
import watermelonUrl from '@/assets/images/fruit-game/watermelon.webp'

/**
 * 과일 자르기 게임 규칙 설정 (MGAME-08/09/10/13).
 *
 * 과일 종류/크기/점수, 생성 간격, 베기 기준 속도 등은 모두 이 파일의 설정값으로
 * 관리한다. 사용자 테스트 후 코드 로직 변경 없이 값만 조정할 수 있도록 한다.
 */

export type FruitType = {
  id: string
  name: string
  imageUrl: string
  /** 원본 이미지 가로/세로 비율 (w / h) */
  aspect: number
  /** 가장 긴 변 기준 표시 크기(px, 1280x720 캔버스 기준) */
  baseSize: number
  /** 베기 성공 시 점수 (과일당 최대 500) */
  score: number
}

/**
 * 작을수록 높은 점수, 클수록 낮은 점수 (MGAME-09).
 * aspect는 최적화 후 실제 이미지 비율.
 */
export const FRUIT_TYPES: FruitType[] = [
  {
    id: 'grape',
    name: '포도',
    imageUrl: grapeUrl,
    aspect: 0.788,
    baseSize: 96,
    score: 50,
  },
  {
    id: 'strawberry',
    name: '딸기',
    imageUrl: strawberryUrl,
    aspect: 0.778,
    baseSize: 104,
    score: 45,
  },
  {
    id: 'kiwi',
    name: '키위',
    imageUrl: kiwiUrl,
    aspect: 0.931,
    baseSize: 112,
    score: 40,
  },
  {
    id: 'apple',
    name: '사과',
    imageUrl: appleUrl,
    aspect: 1.006,
    baseSize: 122,
    score: 35,
  },
  {
    id: 'orange',
    name: '오렌지',
    imageUrl: orangeUrl,
    aspect: 0.956,
    baseSize: 132,
    score: 30,
  },
  {
    id: 'banana',
    name: '바나나',
    imageUrl: bananaUrl,
    aspect: 1.139,
    baseSize: 156,
    score: 28,
  },
  {
    id: 'mango',
    name: '망고',
    imageUrl: mangoUrl,
    aspect: 1.039,
    baseSize: 156,
    score: 25,
  },
  {
    id: 'coconut',
    name: '코코넛',
    imageUrl: coconutUrl,
    aspect: 0.984,
    baseSize: 168,
    score: 20,
  },
  {
    id: 'pineapple',
    name: '파인애플',
    imageUrl: pineappleUrl,
    aspect: 0.553,
    baseSize: 180,
    score: 18,
  },
  {
    id: 'watermelon',
    name: '수박',
    imageUrl: watermelonUrl,
    aspect: 1.016,
    baseSize: 202,
    score: 12,
  },
]

export const FRUIT_GAME_CONFIG = {
  /** 규칙 버전 (결과 저장 시 기록) */
  ruleVersion: 'fruit-ninja-v1',
  canvasWidth: 1280,
  canvasHeight: 720,
  /** 총 진행 시간(초) — 일시정지 제외 실제 플레이 시간 (MGAME-17) */
  durationSec: 120,
  /** 플레이 손 인식 후 카운트다운(초) (MGAME-04) */
  countdownSec: 3,
  /** 과일 생성 간격(ms) (MGAME-13) */
  spawnIntervalMs: 1100,
  /** 동시 표시 최대 과일 수 (MGAME-12) */
  maxVisibleFruit: 5,
  /** 베기 기준 속도(px/s). 손이 이보다 빠르게 움직여야 성공 (MGAME-07/08) */
  sliceSpeedThreshold: 1400,
  /** 중력 가속도(px/s^2) */
  gravity: 900,
  /** 발사 초기 수직 속도 범위(px/s, 위로) */
  launchVyMin: -1180,
  launchVyMax: -980,
  /** 발사 초기 수평 속도 절댓값 최대(px/s) */
  launchVxAbsMax: 280,
  /** 생성 x 위치 좌우 여백(px) */
  spawnXMargin: 220,
  /** 베인 과일 조각이 화면에 남아 있는 시간(ms) */
  slicedLingerMs: 650,
} as const

/** 표시 크기(가장 긴 변 = baseSize)에 맞춘 가로/세로 px. */
export function fruitDimensions(type: FruitType): { w: number; h: number } {
  if (type.aspect >= 1) {
    return { w: type.baseSize, h: type.baseSize / type.aspect }
  }
  return { w: type.baseSize * type.aspect, h: type.baseSize }
}

export function pickRandomFruitType(): FruitType {
  return FRUIT_TYPES[Math.floor(Math.random() * FRUIT_TYPES.length)]
}
