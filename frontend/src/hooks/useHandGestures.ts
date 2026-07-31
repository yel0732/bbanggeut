import {
  HandLandmarker,
  type HandLandmarkerResult,
} from '@mediapipe/tasks-vision'
import { useCallback, useEffect, useRef, type RefObject } from 'react'

import { useVisionTask, type VisionFileset } from '@/hooks/useVisionTask'

const MODEL_PATH =
  'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task'

/** MediaPipe Hands 랜드마크 인덱스 */
const LM = {
  wrist: 0,
  middleMcp: 9,
} as const

/** 손가락별 [끝, 밑동(MCP)] 랜드마크. 엄지는 개인차가 커서 제외한다. */
const FINGERS: [number, number][] = [
  [8, 5],
  [12, 9],
  [16, 13],
  [20, 17],
]

/**
 * 손가락이 펴졌다고 볼 기준.
 *
 * 손끝이 자기 밑동보다 손목에서 이만큼 더 멀면 펴진 것으로 본다. 손 크기와
 * 화면상 거리에 상관없이 성립하는 비율이다.
 */
const FINGER_EXTENDED_RATIO = 1.45
/** 펴진 손가락이 이 개수 이하면 주먹으로 본다. */
const FIST_MAX_EXTENDED = 1
/** 주먹 상태에서 이 개수 이상 펴져야 다시 펴진 것으로 본다 (히스테리시스). */
const OPEN_MIN_EXTENDED = 3
/**
 * 손 좌표 지터를 줄이는 지수 평활 계수 (1에 가까울수록 즉각 반응).
 *
 * 낮으면 손이 묵직하게 따라온다. 그림 맞추기는 제자리에 놓인 그림을 짚는
 * 게임이라 이 값으로 충분하지만, 화면을 크게 오가는 게임은 답답하다. 그래서
 * 기본값만 여기 두고 게임별로 올려 쓸 수 있게 인자로 받는다.
 */
const DEFAULT_SMOOTHING = 0.45
/** 랜드마크가 잠깐 끊겨도 포인터를 유지하는 시간(ms) */
const HOLD_MS = 220

/** 두 손을 구분하는 슬롯. 표시 색만 다르고 실제 좌우 의미는 두지 않는다. */
export type HandSlot = 'a' | 'b'

export type HandGesture = {
  /** 카메라 이미지 기준 정규화 손바닥 중심 (미러링 전) */
  x: number
  y: number
  /** 손목 → 손바닥 방향 각도(라디안). 미러링된 화면 좌표 기준. */
  angle: number
  /** 주먹을 쥐고 있는지 */
  fist: boolean
}

export type HandGesturesFrame = Record<HandSlot, HandGesture | null>

export type HandGesturesState = {
  /** 매 프레임 갱신되는 최신 양손 상태. 렌더 없이 게임 루프에서 읽는다. */
  gesturesRef: RefObject<HandGesturesFrame>
  modelReady: boolean
  error: string | null
}

type Tracked = {
  x: number
  y: number
  angle: number
  fist: boolean
  lastSeenAt: number
}

function distance(
  a: { x: number; y: number },
  b: { x: number; y: number },
): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

/**
 * 양손 위치 + 주먹 여부 추적 훅 (도블 게임용).
 *
 * Pose 모델은 손목까지만 주므로 주먹을 판별할 수 없어 HandLandmarker(21개
 * 랜드마크)를 쓴다. 손바닥 중심(중지 MCP)을 포인터로, 손끝 4개의 손목까지 평균
 * 거리를 손바닥 크기로 나눈 값으로 주먹을 판별한다. 임계값을 둘로 나눠
 * (닫힘/열림) 경계에서 깜빡이지 않게 한다.
 *
 * 두 손은 handedness 라벨로 슬롯을 유지하고, 라벨이 겹치면 화면 x 순서로
 * 나눈다. 화면에는 손 이름을 쓰지 않으므로 라벨의 좌우 의미 자체는 쓰지 않는다.
 *
 * 상태는 React state로 올리지 않고 ref로만 노출해 프레임당 리렌더가 없다.
 */
export function useHandGestures(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
  {
    maxHands = 2,
    smoothing = DEFAULT_SMOOTHING,
  }: { maxHands?: 1 | 2; smoothing?: number } = {},
): HandGesturesState {
  const gesturesRef = useRef<HandGesturesFrame>({ a: null, b: null })
  // 매 프레임 콜백에서 읽으므로 ref에 담아 콜백을 다시 만들지 않는다.
  const smoothingRef = useRef(smoothing)
  smoothingRef.current = smoothing
  const maxHandsRef = useRef(maxHands)
  maxHandsRef.current = maxHands
  /**
   * 한 손만 쓸 때 따라가고 있는 손의 직전 위치.
   *
   * 두 손이 화면에 있어도 쓰던 손을 계속 따라가야 한다. 매 프레임 인식 점수가
   * 높은 손을 새로 고르면, 아이가 다른 손을 들었을 때 포인터가 건너뛰면서 들고
   * 있던 것을 놓친다. 그래서 직전 위치에 가장 가까운 손을 이어서 잡는다.
   */
  const followedRef = useRef<{ x: number; y: number } | null>(null)
  const trackedRef = useRef<Record<HandSlot, Tracked | null>>({
    a: null,
    b: null,
  })

  const handleResult = useCallback((result: HandLandmarkerResult) => {
    const now = performance.now()
    const hands = result.landmarks ?? []
    const labels = result.handedness ?? result.handednesses ?? []

    const bySlot: Record<HandSlot, number | null> = { a: null, b: null }

    if (maxHandsRef.current === 1) {
      /*
        한 손만 쓴다. 두 손이 화면에 있어도 쓰던 손을 계속 따라간다.

        직전 위치에 가장 가까운 손을 이어서 잡는다. 손은 한 프레임에 순간이동하지
        않으므로 이 기준이면 안 헷갈린다. 따라가던 손이 사라지면 그때 다시 고른다.

        슬롯은 항상 `a`로 고정한다. 왼손/오른손 라벨에 따라 슬롯이 바뀌면 포인터
        색이 변하고, 슬롯마다 따로 두는 조준·잡기 상태가 초기화돼 들고 있던 것을
        놓친다.
      */
      const followed = followedRef.current
      let best = 0
      if (followed && hands.length > 1) {
        let bestDist = Infinity
        for (let i = 0; i < hands.length && i < 2; i++) {
          const dist = distance(hands[i][LM.middleMcp], followed)
          if (dist < bestDist) {
            bestDist = dist
            best = i
          }
        }
      }
      if (hands.length > 0) {
        bySlot.a = best
      }
    } else {
      // 라벨로 슬롯을 정하고, 같은 라벨이 두 번 나오면 x 순서로 나눈다.
      for (let i = 0; i < hands.length && i < 2; i++) {
        const label = labels[i]?.[0]?.categoryName === 'Left' ? 'a' : 'b'
        if (bySlot[label] === null) {
          bySlot[label] = i
        } else {
          const other: HandSlot = label === 'a' ? 'b' : 'a'
          if (bySlot[other] === null) {
            bySlot[other] = i
          }
        }
      }
    }

    const tracked = trackedRef.current

    for (const slot of ['a', 'b'] as const) {
      const index = bySlot[slot]
      const points = index === null ? undefined : hands[index]

      if (!points) {
        // 짧은 끊김은 직전 상태를 유지해 포인터가 깜빡이지 않게 한다.
        const previous = tracked[slot]
        if (previous && now - previous.lastSeenAt <= HOLD_MS) {
          gesturesRef.current[slot] = {
            x: previous.x,
            y: previous.y,
            angle: previous.angle,
            fist: previous.fist,
          }
          continue
        }
        tracked[slot] = null
        gesturesRef.current[slot] = null
        continue
      }

      const wrist = points[LM.wrist]
      const palm = points[LM.middleMcp]

      // 손가락마다 "끝이 밑동보다 손목에서 충분히 먼가"로 펴짐을 센다.
      let extended = 0
      for (const [tip, mcp] of FINGERS) {
        const mcpReach = distance(points[mcp], wrist) || 0.0001
        if (distance(points[tip], wrist) / mcpReach > FINGER_EXTENDED_RATIO) {
          extended++
        }
      }

      const previous = tracked[slot]
      // 히스테리시스: 이미 주먹이면 충분히 펴야 풀리고, 펴져 있으면 거의 다 접어야 쥔다.
      const fist = previous?.fist
        ? extended < OPEN_MIN_EXTENDED
        : extended <= FIST_MAX_EXTENDED

      const smoothing = smoothingRef.current
      const x = previous
        ? previous.x + (palm.x - previous.x) * smoothing
        : palm.x
      const y = previous
        ? previous.y + (palm.y - previous.y) * smoothing
        : palm.y
      // 화면은 좌우 반전되므로 각도도 반전된 좌표계에서 계산한다.
      const angle = Math.atan2(palm.y - wrist.y, -(palm.x - wrist.x))

      tracked[slot] = { x, y, angle, fist, lastSeenAt: now }
      gesturesRef.current[slot] = { x, y, angle, fist }
    }

    // 한 손 모드에서 다음 프레임에 같은 손을 이어 잡을 기준점.
    if (maxHandsRef.current === 1) {
      const current = gesturesRef.current.a
      followedRef.current = current ? { x: current.x, y: current.y } : null
    }
  }, [])

  const createTask = useCallback(
    (fileset: VisionFileset) =>
      HandLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL_PATH, delegate: 'GPU' },
        numHands: 2,
        // 아이가 카메라에서 떨어져 손이 작게 보여도 놓치지 않도록 낮춘다.
        minHandDetectionConfidence: 0.4,
        minHandPresenceConfidence: 0.4,
        minTrackingConfidence: 0.4,
        runningMode: 'VIDEO',
      }),
    [],
  )

  const { modelReady, error } = useVisionTask(
    videoRef,
    enabled,
    createTask,
    handleResult,
  )

  useEffect(() => {
    if (enabled) {
      return
    }
    trackedRef.current = { a: null, b: null }
    gesturesRef.current = { a: null, b: null }
    followedRef.current = null
  }, [enabled])

  return { gesturesRef, modelReady, error }
}
