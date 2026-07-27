import { FilesetResolver } from '@mediapipe/tasks-vision'
import { useEffect, useRef, useState, type RefObject } from 'react'

const TASKS_VISION_VERSION = '0.10.35'
export const MEDIAPIPE_WASM_PATH = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${TASKS_VISION_VERSION}/wasm`

/** `WasmFileset`은 패키지에서 export되지 않아 반환 타입으로 얻는다. */
export type VisionFileset = Awaited<
  ReturnType<typeof FilesetResolver.forVisionTasks>
>

/** MediaPipe 태스크가 공통으로 제공하는 최소 인터페이스 */
type VideoTask<TResult> = {
  detectForVideo: (video: HTMLVideoElement, timestamp: number) => TResult
  close: () => void
}

export type VisionTaskState = {
  modelReady: boolean
  error: string | null
}

/**
 * MediaPipe Vision 태스크 실행 루프 공통 훅.
 *
 * WASM 로딩, 모델 생성, video 프레임 추적, rAF 루프, 정리를 담당하고 프레임
 * 결과만 `onResult`로 넘긴다. 어떤 태스크를 쓸지는 `createTask`가 정한다.
 * (과일 자르기 = PoseLandmarker, 도블 = HandLandmarker)
 */
export function useVisionTask<TResult>(
  videoRef: RefObject<HTMLVideoElement | null>,
  enabled: boolean,
  createTask: (fileset: VisionFileset) => Promise<VideoTask<TResult>>,
  onResult: (result: TResult) => void,
): VisionTaskState {
  const [modelReady, setModelReady] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 매 프레임 최신 콜백을 쓰되 루프는 재시작하지 않는다.
  const onResultRef = useRef(onResult)
  onResultRef.current = onResult
  const createTaskRef = useRef(createTask)
  createTaskRef.current = createTask

  useEffect(() => {
    if (!enabled) {
      return
    }

    let task: VideoTask<TResult> | null = null
    let rafId = 0
    let cancelled = false
    let lastVideoTime = -1

    const detectLoop = () => {
      if (cancelled) {
        return
      }
      const video = videoRef.current
      if (task && video && video.readyState >= 2 && video.videoWidth > 0) {
        // 같은 프레임을 두 번 추론하지 않는다.
        if (video.currentTime !== lastVideoTime) {
          lastVideoTime = video.currentTime
          onResultRef.current(task.detectForVideo(video, performance.now()))
        }
      }
      rafId = requestAnimationFrame(detectLoop)
    }

    const init = async () => {
      try {
        const fileset =
          await FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_PATH)
        if (cancelled) {
          return
        }
        task = await createTaskRef.current(fileset)
        if (cancelled) {
          task.close()
          task = null
          return
        }
        setModelReady(true)
        rafId = requestAnimationFrame(detectLoop)
      } catch {
        if (!cancelled) {
          setError('동작 인식을 준비하지 못했어요')
        }
      }
    }

    void init()

    return () => {
      cancelled = true
      cancelAnimationFrame(rafId)
      task?.close()
      task = null
      setModelReady(false)
    }
  }, [enabled, videoRef])

  return { modelReady, error }
}
