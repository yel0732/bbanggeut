import {
  PoseLandmarker,
  type PoseLandmarkerResult,
} from '@mediapipe/tasks-vision'
import { useCallback, type RefObject } from 'react'

import { useVisionTask, type VisionFileset } from '@/hooks/useVisionTask'

const MODEL_PATH =
  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task'

/** BlazePose 랜드마크 인덱스 (필요한 것만) */
export const POSE_LM = {
  leftShoulder: 11,
  rightShoulder: 12,
  leftElbow: 13,
  rightElbow: 14,
  leftWrist: 15,
  rightWrist: 16,
} as const

/** 랜드마크를 신뢰할 최소 visibility */
export const POSE_VISIBILITY_MIN = 0.4

export type PoseLandmarkerState = {
  modelReady: boolean
  error: string | null
}

/**
 * MediaPipe Pose 추론 훅.
 *
 * 팔꿈치~손목만 필요한 게임(과일 자르기)이 쓴다. 손가락 정보는 없다.
 */
export function usePoseLandmarker(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
  onResult: (result: PoseLandmarkerResult) => void,
): PoseLandmarkerState {
  const createTask = useCallback(
    (fileset: VisionFileset) =>
      PoseLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL_PATH, delegate: 'GPU' },
        numPoses: 1,
        runningMode: 'VIDEO',
      }),
    [],
  )

  return useVisionTask(videoRef, enabled, createTask, onResult)
}
