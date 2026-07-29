import { useEffect, useRef } from 'react'

/** 모든 화면이 공유하는 설계 해상도. */
export const CANVAS_WIDTH = 1280
export const CANVAS_HEIGHT = 720

/**
 * 1280x720 고정 캔버스를 뷰포트에 맞춰 확대·축소한다.
 *
 * 기본은 `contain`(레터박스)이다. 캔버스 전체가 항상 화면 안에 들어오고 남는
 * 자리는 바깥 배경이 채운다. 그래서 16:10 노트북, 3:2 화면, 21:9 모니터, 전체
 * 화면 어디서든 구도가 똑같다.
 *
 * `cover`(Math.max)는 화면을 꽉 채우는 대신 넘치는 만큼 잘라 낸다. 1440x900에서
 * 가로 160px, 3:2 화면에서 400px 넘게 잘려 화면 가장자리 버튼이 사라진다. 아이가
 * 누를 것이 안 보이는 쪽이 여백보다 나쁘다.
 *
 * 배율은 `resize`와 `visualViewport`를 함께 듣는다. 브라우저 확대·축소는 `resize`를
 * 항상 발생시키지 않는다.
 */
export function useFitScale<T extends HTMLElement>(
  mode: 'cover' | 'contain' = 'contain',
) {
  const ref = useRef<T>(null)

  useEffect(() => {
    const fit = () => {
      const el = ref.current
      if (!el) return
      const viewport = window.visualViewport
      const width = viewport?.width ?? window.innerWidth
      const height = viewport?.height ?? window.innerHeight
      const scaleMethod = mode === 'contain' ? Math.min : Math.max
      const scale = scaleMethod(width / CANVAS_WIDTH, height / CANVAS_HEIGHT)
      el.style.transform = `scale(${scale})`
    }

    fit()
    window.addEventListener('resize', fit)
    window.visualViewport?.addEventListener('resize', fit)
    return () => {
      window.removeEventListener('resize', fit)
      window.visualViewport?.removeEventListener('resize', fit)
    }
  }, [mode])

  return ref
}
