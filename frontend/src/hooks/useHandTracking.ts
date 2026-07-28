import { type PoseLandmarkerResult } from '@mediapipe/tasks-vision'
import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

import {
  POSE_LM as LM,
  POSE_VISIBILITY_MIN as VISIBILITY_MIN,
  usePoseLandmarker,
} from '@/hooks/usePoseLandmarker'

/**
 * 카메라 거리 추정은 어깨너비(정규화)로 한다.
 *
 * 팔뚝 길이로 재면 팔을 뻗은 방향에 따라 값이 두 배 가까이 달라져서, 서서 팔을
 * 가로로 휘두르는 것만으로 "앞으로 오세요"가 떴다 사라졌다 한다. 어깨너비는
 * 팔 자세와 무관하게 거리에만 비례한다.
 *
 * 서서 상반신이 화면에 들어오는 거리(약 1.5~2.5m)가 목표 구간이다.
 */
const SHOULDER_NEAR = 0.3
const SHOULDER_FAR = 0.11
/** 랜드마크가 잠깐 끊겨도 팔을 유지하는 시간(ms) */
const HOLD_MS = 240
/** 인식 결과 지터를 줄이는 지수 평활 계수 (1에 가까울수록 즉각 반응) */
const SAMPLE_SMOOTHING = 0.55
/**
 * 팔을 "들었다"고 볼 여유.
 *
 * 손목이 같은 쪽 어깨보다 이만큼 위에 있어야 든 것으로 본다. 어깨너비에 대한
 * 비율이라 카메라 거리와 무관하게 성립한다. 경계에서 깜빡이지 않을 만큼만 둔다.
 */
const RAISE_MARGIN_RATIO = 0.22

export type DistanceHint = 'near' | 'ok' | 'far'

export type PlayHand = {
  /** 지정된 플레이 팔: 'left' | 'right' */
  side: 'left' | 'right'
  /** 정규화 손목 좌표 — 칼 손잡이가 놓이는 지점 */
  wrist: { x: number; y: number }
  /** 정규화 팔꿈치 좌표 — 팔꿈치→손목이 칼이 향하는 방향 */
  elbow: { x: number; y: number }
  /** 카메라 거리 힌트 */
  distance: DistanceHint
  /** 마지막으로 실제 인식된 시각(performance.now). 홀드 중이면 갱신되지 않는다. */
  updatedAt: number
}

export type HandTrackingState = {
  modelReady: boolean
  designated: boolean
  /** 매 프레임 갱신되는 최신 플레이 팔. 렌더 없이 게임 루프에서 읽는다. */
  playHandRef: RefObject<PlayHand | null>
  error: string | null
  /** 플레이 팔 지정을 초기화해 다음에 든 팔을 다시 지정 (MGAME-25/28) */
  redesignate: () => void
}

type Tracked = {
  ex: number
  ey: number
  wx: number
  wy: number
  /** 평활한 어깨너비. 어깨가 잠깐 안 보여도 직전 값을 쓴다. */
  shoulder: number
  lastSeenAt: number
}

/**
 * 플레이 팔 추적 훅 (MGAME-03/05/06).
 *
 * MediaPipe Pose로 어깨~팔꿈치~손목을 인식한다. 손만 보는 것보다 인식 범위가
 * 넓어 아이가 카메라에서 떨어져 서 있어도 동작한다. 먼저 든 한쪽 팔을 플레이
 * 팔로 지정하고 그 팔의 팔꿈치→손목 방향만 베기에 사용한다.
 *
 * 칼의 실제 길이는 여기서 정하지 않는다. 정규화 좌표에서 길이를 정하면 화면
 * 가로/세로 비율 때문에 세로로 뻗은 칼이 가로보다 짧아진다. 게임 쪽에서 화면
 * px 기준으로 길이를 붙인다.
 *
 * 결과는 React state로 올리지 않고 ref로만 노출한다. 프레임마다 setState하면
 * 게임 화면 전체가 초당 30번 리렌더되어 칼이 뚝뚝 끊겨 보인다.
 */
export function useHandTracking(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
): HandTrackingState {
  const [designated, setDesignated] = useState(false)
  const playHandRef = useRef<PlayHand | null>(null)
  const trackedRef = useRef<Tracked | null>(null)

  // 지정된 플레이 팔. 한 번 정해지면 계속 유지한다.
  const playSideRef = useRef<'left' | 'right' | null>(null)

  const redesignate = useCallback(() => {
    playSideRef.current = null
    trackedRef.current = null
    playHandRef.current = null
    setDesignated(false)
  }, [])

  const handleResult = useCallback((result: PoseLandmarkerResult) => {
    const now = performance.now()

    /** 인식이 잠깐 끊겼을 때: 짧은 시간은 직전 값을 유지하고, 넘으면 놓는다. */
    const lose = (): void => {
      const previous = trackedRef.current
      if (previous && now - previous.lastSeenAt <= HOLD_MS) {
        return
      }
      trackedRef.current = null
      playHandRef.current = null
    }

    const pose = result.landmarks?.[0]
    if (!pose) {
      lose()
      return
    }

    const lw = pose[LM.leftWrist]
    const rw = pose[LM.rightWrist]
    const le = pose[LM.leftElbow]
    const re = pose[LM.rightElbow]

    const leftOk =
      (lw?.visibility ?? 0) > VISIBILITY_MIN &&
      (le?.visibility ?? 0) > VISIBILITY_MIN
    const rightOk =
      (rw?.visibility ?? 0) > VISIBILITY_MIN &&
      (re?.visibility ?? 0) > VISIBILITY_MIN

    const ls = pose[LM.leftShoulder]
    const rs = pose[LM.rightShoulder]
    const shouldersOk =
      (ls?.visibility ?? 0) > VISIBILITY_MIN &&
      (rs?.visibility ?? 0) > VISIBILITY_MIN
    const shoulderWidth = shouldersOk
      ? Math.hypot(ls.x - rs.x, ls.y - rs.y)
      : null

    /**
     * 그 팔을 들고 있는지. y는 위로 갈수록 작다.
     *
     * 어깨가 보이면 같은 쪽 어깨보다 위인지로 본다. 어깨가 안 보이면 팔뚝이
     * 위를 향하는지(손목이 팔꿈치보다 위)로 대신한다.
     */
    const isRaised = (
      wrist: { y: number },
      elbow: { y: number },
      shoulder: { y: number } | null,
    ): boolean => {
      if (shoulder && shoulderWidth !== null) {
        return wrist.y < shoulder.y - shoulderWidth * RAISE_MARGIN_RATIO
      }
      return wrist.y < elbow.y
    }

    /*
      플레이 팔은 "든 팔"만 후보로 삼는다.

      가시성만 보면 서서 팔을 내리고 있어도 조건을 넘겨서, 추적이 시작되는 첫
      프레임에 우연히 위에 있던 팔로 정해져 버린다. 아이는 오른팔을 휘두르는데
      칼이 왼팔에 붙어 있는 일이 생긴다. 아무 팔도 안 들었으면 지정을 보류하고
      기다린다 — 화면 안내("한 손을 번쩍 들어 주세요")와 조건이 같아진다.

      한 번 정해진 뒤에는 다시 확인하지 않는다. 내리치기를 하려면 팔을 내려야
      하는데 그때 칼이 사라지면 게임이 성립하지 않는다.
    */
    if (playSideRef.current === null) {
      const leftRaised = leftOk && isRaised(lw, le, shouldersOk ? ls : null)
      const rightRaised = rightOk && isRaised(rw, re, shouldersOk ? rs : null)

      if (leftRaised && rightRaised) {
        // 둘 다 들었으면 더 높이 든 팔로 정한다.
        playSideRef.current = lw.y < rw.y ? 'left' : 'right'
      } else if (leftRaised) {
        playSideRef.current = 'left'
      } else if (rightRaised) {
        playSideRef.current = 'right'
      } else {
        lose()
        return
      }
      setDesignated(true)
    }

    const side = playSideRef.current
    const ok = side === 'left' ? leftOk : rightOk
    if (!ok) {
      lose()
      return
    }
    const rawWrist = side === 'left' ? lw : rw
    const rawElbow = side === 'left' ? le : re

    const previous = trackedRef.current
    const k = SAMPLE_SMOOTHING

    // 어깨너비로 거리를 잰다. 어깨가 안 보이는 프레임은 직전 값을 유지한다.
    let shoulder = previous?.shoulder ?? shoulderWidth ?? 0
    if (shoulderWidth !== null) {
      shoulder = previous
        ? shoulder + (shoulderWidth - shoulder) * k
        : shoulderWidth
    }

    // 인식 결과는 프레임마다 미세하게 떨린다. 지수 평활로 흔들림을 눌러 준다.
    const ex = previous
      ? previous.ex + (rawElbow.x - previous.ex) * k
      : rawElbow.x
    const ey = previous
      ? previous.ey + (rawElbow.y - previous.ey) * k
      : rawElbow.y
    const wx = previous
      ? previous.wx + (rawWrist.x - previous.wx) * k
      : rawWrist.x
    const wy = previous
      ? previous.wy + (rawWrist.y - previous.wy) * k
      : rawWrist.y
    trackedRef.current = { ex, ey, wx, wy, shoulder, lastSeenAt: now }

    let distance: DistanceHint = 'ok'
    if (shoulder > SHOULDER_NEAR) distance = 'near'
    else if (shoulder > 0 && shoulder < SHOULDER_FAR) distance = 'far'

    playHandRef.current = {
      side,
      wrist: { x: wx, y: wy },
      elbow: { x: ex, y: ey },
      distance,
      updatedAt: now,
    }
  }, [])

  const { modelReady, error } = usePoseLandmarker(
    videoRef,
    enabled,
    handleResult,
  )

  // 추적이 꺼지면 플레이 팔 지정도 초기화한다.
  useEffect(() => {
    if (enabled) {
      return
    }
    playSideRef.current = null
    trackedRef.current = null
    playHandRef.current = null
    setDesignated(false)
  }, [enabled])

  return { modelReady, designated, playHandRef, error, redesignate }
}
