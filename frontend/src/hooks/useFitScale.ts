import { useEffect, useRef } from 'react'

const CANVAS_WIDTH = 1280
const CANVAS_HEIGHT = 720

/**
 * GameStart 계열 화면(1280x720 고정 캔버스)을 뷰포트에 맞춰 cover 스케일한다.
 * 원본 .dc.html 디자인의 scaleRef 로직과 동일하게 Math.max로 계산한다.
 */
export function useFitScale<T extends HTMLElement>() {
  const ref = useRef<T>(null)

  useEffect(() => {
    const fit = () => {
      const el = ref.current
      if (!el) return
      const scale = Math.max(
        window.innerWidth / CANVAS_WIDTH,
        window.innerHeight / CANVAS_HEIGHT,
      )
      el.style.transform = `scale(${scale})`
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])

  return ref
}
