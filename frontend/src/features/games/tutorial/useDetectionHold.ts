import { useEffect, useRef, useState } from 'react'

/**
 * "잠깐 유지되면 통과" 판정.
 *
 * 인식 결과는 프레임마다 깜빡일 수 있어서 한 프레임만 잡혀도 통과시키면
 * 아이가 뭘 해서 넘어갔는지 모른다. 일정 시간 계속 잡혀야 통과로 보고, 그동안
 * 차오르는 진행도를 돌려줘 화면에 링으로 보여 준다.
 *
 * 인식 상태는 ref로만 오므로(리렌더 없음) rAF로 들여다본다. 진행도는 그대로
 * state에 올리면 초당 60번 리렌더되므로 눈에 보일 만큼만 잘라서 발행한다.
 */

/** 진행도를 이 간격으로 끊어서 발행한다. (5%) */
const PROGRESS_STEP = 0.05

export type DetectionHold = {
  detected: boolean
  /** 0..1 */
  progress: number
}

export function useDetectionHold({
  active,
  check,
  holdMs,
  onComplete,
}: {
  active: boolean
  check: () => boolean
  holdMs: number
  onComplete: () => void
}): DetectionHold {
  const [hold, setHold] = useState<DetectionHold>({
    detected: false,
    progress: 0,
  })

  const checkRef = useRef(check)
  checkRef.current = check
  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete

  useEffect(() => {
    if (!active) {
      setHold({ detected: false, progress: 0 })
      return
    }

    let rafId = 0
    let heldSince = 0
    let done = false
    let publishedDetected = false
    let publishedProgress = 0

    const loop = (now: number): void => {
      const detected = checkRef.current()

      if (!detected) {
        heldSince = 0
      } else if (heldSince === 0) {
        heldSince = now
      }

      const progress = detected ? Math.min(1, (now - heldSince) / holdMs) : 0

      const quantized = Math.round(progress / PROGRESS_STEP) * PROGRESS_STEP
      if (detected !== publishedDetected || quantized !== publishedProgress) {
        publishedDetected = detected
        publishedProgress = quantized
        setHold({ detected, progress: quantized })
      }

      if (progress >= 1 && !done) {
        done = true
        onCompleteRef.current()
        return
      }

      rafId = requestAnimationFrame(loop)
    }

    rafId = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(rafId)
  }, [active, holdMs])

  return hold
}
