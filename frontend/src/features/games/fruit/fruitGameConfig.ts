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
  /**
   * 총 진행 시간(초) — 일시정지 제외 실제 플레이 시간 (MGAME-17).
   *
   * 팔을 계속 휘두르는 게임이라 길면 아이가 지친다. 1분으로 짧게 끊는다.
   */
  durationSec: 60,
  /** 플레이 손 인식 후 카운트다운(초) (MGAME-04) */
  countdownSec: 3,
  /** 과일 생성 간격(ms) (MGAME-13) */
  spawnIntervalMs: 1100,
  /** 동시 표시 최대 과일 수 (MGAME-12) */
  maxVisibleFruit: 5,

  /**
   * 손 위치 평활 시상수(ms).
   *
   * 인식은 초당 30번 남짓 들어오는데 화면은 60fps로 그린다. 새 좌표로 곧장
   * 튀지 않고 이 시상수로 따라가게 해 프레임 사이를 메운다. 작을수록 즉각
   * 반응하고 클수록 부드럽다.
   */
  handSmoothTauMs: 55,
  /** 인식이 잠깐 끊겨도 손을 유지하는 시간(ms) */
  handHoldMs: 240,
  /** 손 속도를 재는 구간(ms). 한 프레임 차이만 보면 값이 심하게 튄다. */
  speedWindowMs: 100,
  /** 베기 기준 속도(px/s). 손이 이보다 빠르게 움직여야 성공 (MGAME-07/08) */
  sliceSpeedThreshold: 950,
  /** 기준 속도를 넘긴 뒤 베기 상태를 유지하는 시간(ms) */
  sliceSpeedHoldMs: 110,
  /**
   * 손목에서 칼끝까지 길이(화면 px, 고정).
   *
   * 카메라와의 거리나 팔이 향한 방향에 상관없이 항상 같은 길이로 그린다.
   * 가까이 왔다고 판정이 헐거워지지도, 세로로 뻗었다고 짧아지지도 않는다.
   */
  bladeLengthPx: 240,
  /**
   * 칼날에서 실제로 베는 구간의 시작 지점 (0=손목, 1=칼끝).
   *
   * 손목 쪽까지 날로 치면 팔을 휘두르기만 해도 과일이 잘린다. 바깥쪽만 날로 둔다.
   */
  bladeEdgeStart: 0.35,
  /** 과일 히트 반경 배율. 1보다 작으면 더 정확히 스쳐야 잘린다. */
  hitRadiusScale: 0.9,
  /**
   * 칼날 두께(px, 반폭).
   *
   * 칼날은 선분이라 스윙 방향이 칼날과 나란하면(팔을 위로 든 채 아래로 긋기)
   * 훑는 자리가 선 하나뿐이라 거의 안 잘린다. 두께를 줘서 그 선 주변까지
   * 걸리게 한다.
   */
  bladeThicknessPx: 20,
  /** 스와이프 트레일에 남기는 시간(ms) */
  trailMs: 170,

  /** 콤보가 이어지는 최대 간격(ms). 이 안에 또 베면 콤보가 올라간다. */
  comboWindowMs: 1300,
  /** 콤보 한 단계당 점수 배수 증가폭 */
  comboStep: 0.25,
  /** 점수 배수 상한 */
  comboMaxMultiplier: 3,

  /**
   * 피버 타임.
   *
   * 콤보가 이 수에 닿으면 과일이 쏟아지는 구간이 열린다. 콤보를 계속 이어 가면
   * 그때마다 남은 시간이 다시 채워지므로 잘할수록 오래 간다.
   */
  feverCombo: 5,
  /** 피버 지속 시간(ms) */
  feverDurationMs: 8000,
  /** 피버 중 과일 생성 간격(ms) */
  feverSpawnIntervalMs: 380,
  /** 피버 중 동시 표시 최대 과일 수 */
  feverMaxVisibleFruit: 10,
  /** 피버 중 보장되는 최소 점수 배수 */
  feverMinMultiplier: 1.5,

  /** 중력 가속도(px/s^2) */
  gravity: 900,
  /**
   * 발사 초기 수직 속도 범위(px/s, 위로).
   *
   * 서서 하는 게임이라 온몸이 화면에 들어온다. 그러면 손목이 닿는 높이는 화면
   * 위쪽 절반 남짓이므로, 과일이 화면 꼭대기까지 솟구치면 매번 팔을 머리 위로
   * 쭉 뻗어야 한다. 정점이 화면 중상단(y 약 160~360)에 오도록 낮춘다.
   */
  launchVyMin: -1080,
  launchVyMax: -900,
  /** 발사 초기 수평 속도 절댓값 최대(px/s) */
  launchVxAbsMax: 280,
  /** 생성 x 위치 좌우 여백(px). 선 자세에서 팔이 닿는 폭 안에 둔다. */
  spawnXMargin: 280,
  /** 베인 과일 조각이 화면에 남아 있는 시간(ms) */
  slicedLingerMs: 650,
  /** 플레이 손이 사라진 뒤 자동 일시정지까지 유예(ms) (MGAME-24) */
  handLostGraceMs: 700,
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
