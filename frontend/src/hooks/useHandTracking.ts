import {
  FilesetResolver,
  HandLandmarker,
  type HandLandmarkerResult,
} from '@mediapipe/tasks-vision'
import { useEffect, useRef, useState, type RefObject } from 'react'

const TASKS_VISION_VERSION = '0.10.35'
const WASM_PATH = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${TASKS_VISION_VERSION}/wasm`
const MODEL_PATH =
  'https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task'

/** 정규화(0..1) 손 영역. 영상 원본 좌표계 기준(미러링 전). */
export type HandBox = {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export type PlayHand = {
  /** 지정된 플레이 손 식별자: 'Left' | 'Right' */
  handedness: string
  /** 정규화 손 영역 */
  box: HandBox
  /** 정규화 손 중심 포인터 */
  pointer: { x: number; y: number }
}

export type HandTrackingState = {
  /** 모델 로드 완료 여부 */
  modelReady: boolean
  /** 플레이 손이 한 번이라도 지정됐는지 */
  designated: boolean
  /** 현재 프레임에서 추적된 플레이 손 (안 보이면 null) */
  playHand: PlayHand | null
  /** 초기화 실패 메시지 */
  error: string | null
}

// 손바닥 중심 추정용 랜드마크(손목 + 각 손가락 MCP).
const PALM_LANDMARKS = [0, 5, 9, 13, 17]

/**
 * 플레이 손 추적 훅 (MGAME-03/05/06).
 *
 * - 먼저 정상 인식된 한 손을 플레이 손으로 지정하고 이후 그 손만 추적한다.
 * - 반대 손은 추적/표시 대상에서 제외한다.
 * - 플레이 손이 화면에서 사라지면 playHand가 null이 되어 안내를 띄울 수 있다.
 *
 * 좌표는 영상 원본(미러링 전) 정규화값이다. 표시할 때 영상과 같은 미러링을 적용한다.
 */
export function useHandTracking(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
): HandTrackingState {
  const [modelReady, setModelReady] = useState(false)
  const [designated, setDesignated] = useState(false)
  const [playHand, setPlayHand] = useState<PlayHand | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 지정된 플레이 손의 handedness. 한 번 정해지면 계속 유지한다.
  const playHandednessRef = useRef<string | null>(null)

  useEffect(() => {
    if (!enabled) {
      return
    }

    let landmarker: HandLandmarker | null = null
    let rafId = 0
    let cancelled = false
    let lastVideoTime = -1

    const detectLoop = () => {
      if (cancelled) {
        return
      }
      const video = videoRef.current
      if (
        landmarker &&
        video &&
        video.readyState >= 2 &&
        video.videoWidth > 0
      ) {
        if (video.currentTime !== lastVideoTime) {
          lastVideoTime = video.currentTime
          const result = landmarker.detectForVideo(video, performance.now())
          handleResult(result)
        }
      }
      rafId = requestAnimationFrame(detectLoop)
    }

    const handleResult = (result: HandLandmarkerResult) => {
      const hands = result.landmarks
      const handedness = result.handedness

      if (!hands || hands.length === 0) {
        setPlayHand(null)
        return
      }

      // 아직 미지정이면 먼저 인식된 손을 플레이 손으로 지정한다.
      if (playHandednessRef.current === null) {
        playHandednessRef.current = handedness[0]?.[0]?.categoryName ?? 'Right'
        setDesignated(true)
      }

      const index = handedness.findIndex(
        (categories) =>
          categories[0]?.categoryName === playHandednessRef.current,
      )
      if (index === -1) {
        // 지정된 손이 이번 프레임에 없음 → 안내를 위해 null.
        setPlayHand(null)
        return
      }

      const landmarks = hands[index]
      let minX = 1
      let minY = 1
      let maxX = 0
      let maxY = 0
      for (const point of landmarks) {
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
      }

      let sumX = 0
      let sumY = 0
      for (const i of PALM_LANDMARKS) {
        sumX += landmarks[i].x
        sumY += landmarks[i].y
      }

      setPlayHand({
        handedness: playHandednessRef.current,
        box: { minX, minY, maxX, maxY },
        pointer: {
          x: sumX / PALM_LANDMARKS.length,
          y: sumY / PALM_LANDMARKS.length,
        },
      })
    }

    const init = async () => {
      try {
        const vision = await FilesetResolver.forVisionTasks(WASM_PATH)
        if (cancelled) {
          return
        }
        landmarker = await HandLandmarker.createFromOptions(vision, {
          baseOptions: { modelAssetPath: MODEL_PATH, delegate: 'GPU' },
          numHands: 2,
          runningMode: 'VIDEO',
        })
        if (cancelled) {
          landmarker.close()
          landmarker = null
          return
        }
        setModelReady(true)
        rafId = requestAnimationFrame(detectLoop)
      } catch {
        if (!cancelled) {
          setError('손 인식을 준비하지 못했어요')
        }
      }
    }

    void init()

    return () => {
      cancelled = true
      cancelAnimationFrame(rafId)
      landmarker?.close()
      landmarker = null
      // 재진입 시 새 손을 다시 지정하도록 초기화한다.
      playHandednessRef.current = null
      setModelReady(false)
      setDesignated(false)
      setPlayHand(null)
    }
  }, [enabled, videoRef])

  return { modelReady, designated, playHand, error }
}
