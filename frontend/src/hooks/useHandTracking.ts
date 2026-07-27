import { type PoseLandmarkerResult } from '@mediapipe/tasks-vision'
import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

import {
  POSE_LM as LM,
  POSE_VISIBILITY_MIN as VISIBILITY_MIN,
  usePoseLandmarker,
} from '@/hooks/usePoseLandmarker'

// 팔뚝 길이(정규화)로 카메라 거리 추정
const ARM_NEAR = 0.5
const ARM_FAR = 0.1
// 손목에서 뻗는 검 길이 배율(팔뚝 길이 기준)
const SWORD_REACH = 1.15

export type DistanceHint = 'near' | 'ok' | 'far'

export type HandBox = {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export type PlayHand = {
  /** 지정된 플레이 팔: 'left' | 'right' */
  side: 'left' | 'right'
  /** 정규화 팔/검 영역 */
  box: HandBox
  /** 정규화 손목 포인터 */
  pointer: { x: number; y: number }
  /** 손날(검): 손목 -> 손끝 방향 연장 선분, 정규화 좌표 */
  blade: { ax: number; ay: number; bx: number; by: number }
  /** 카메라 거리 힌트 */
  distance: DistanceHint
}

export type HandTrackingState = {
  modelReady: boolean
  designated: boolean
  playHand: PlayHand | null
  error: string | null
  /** 플레이 팔 지정을 초기화해 다음에 든 팔을 다시 지정 (MGAME-25/28) */
  redesignate: () => void
}

/**
 * 플레이 팔 추적 훅 (MGAME-03/05/06).
 *
 * MediaPipe Pose로 팔꿈치~손목을 인식한다. 손만 보는 것보다 인식 범위가 넓어
 * 아이가 카메라에서 멀리 떨어져도 동작한다. 먼저 든 한쪽 팔을 플레이 팔로
 * 지정하고 그 팔의 손목~손끝 방향만 베기에 사용한다.
 */
export function useHandTracking(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
): HandTrackingState {
  const [designated, setDesignated] = useState(false)
  const [playHand, setPlayHand] = useState<PlayHand | null>(null)

  // 지정된 플레이 팔. 한 번 정해지면 계속 유지한다.
  const playSideRef = useRef<'left' | 'right' | null>(null)

  const redesignate = useCallback(() => {
    playSideRef.current = null
    setDesignated(false)
    setPlayHand(null)
  }, [])

  const handleResult = useCallback((result: PoseLandmarkerResult) => {
    const pose = result.landmarks?.[0]
    if (!pose) {
      setPlayHand(null)
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

    // 미지정이면 먼저 든(더 높이 올린) 팔을 플레이 팔로 지정한다.
    if (playSideRef.current === null) {
      if (leftOk && rightOk) {
        playSideRef.current = lw.y < rw.y ? 'left' : 'right'
      } else if (leftOk) {
        playSideRef.current = 'left'
      } else if (rightOk) {
        playSideRef.current = 'right'
      } else {
        setPlayHand(null)
        return
      }
      setDesignated(true)
    }

    const side = playSideRef.current
    const ok = side === 'left' ? leftOk : rightOk
    if (!ok) {
      setPlayHand(null)
      return
    }
    const wrist = side === 'left' ? lw : rw
    const elbow = side === 'left' ? le : re

    // 팔뚝 방향으로 손목에서 손끝 너머까지 검을 뻗는다.
    const dx = wrist.x - elbow.x
    const dy = wrist.y - elbow.y
    const forearm = Math.hypot(dx, dy) || 0.0001
    const ux = dx / forearm
    const uy = dy / forearm
    const reach = forearm * SWORD_REACH
    const tipX = wrist.x + ux * reach
    const tipY = wrist.y + uy * reach

    let distance: DistanceHint = 'ok'
    if (forearm > ARM_NEAR) distance = 'near'
    else if (forearm < ARM_FAR) distance = 'far'

    setPlayHand({
      side,
      box: {
        minX: Math.min(elbow.x, wrist.x, tipX),
        minY: Math.min(elbow.y, wrist.y, tipY),
        maxX: Math.max(elbow.x, wrist.x, tipX),
        maxY: Math.max(elbow.y, wrist.y, tipY),
      },
      pointer: { x: wrist.x, y: wrist.y },
      blade: { ax: wrist.x, ay: wrist.y, bx: tipX, by: tipY },
      distance,
    })
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
    setDesignated(false)
    setPlayHand(null)
  }, [enabled])

  return { modelReady, designated, playHand, error, redesignate }
}
