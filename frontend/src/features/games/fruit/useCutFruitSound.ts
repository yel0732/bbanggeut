import { useCallback, useEffect, useRef } from 'react'

import cutSoundUrl from '@/assets/audio/fruit-game/cut-fruit.mp3'

/** 연속으로 벨 때 소리가 겹칠 수 있도록 미리 만들어 두는 개수. */
const POOL_SIZE = 4

/**
 * 과일 자르는 소리.
 *
 * `<audio>` 하나로는 이어 벨 때 앞 소리가 끊긴다. 몇 개를 미리 만들어 두고
 * 돌려 쓰면 소리가 겹쳐 울려 실제로 여러 개를 벤 느낌이 난다.
 *
 * 콤보가 이어질수록 음을 조금씩 높여 신나게 들리도록 한다. 콤보를 세지 않는
 * 곳(튜토리얼 연습)은 그냥 부르면 기본 음으로 난다.
 */
export function useCutFruitSound() {
  const poolRef = useRef<HTMLAudioElement[]>([])
  const indexRef = useRef(0)

  useEffect(() => {
    const pool = Array.from({ length: POOL_SIZE }, () => {
      const audio = new Audio(cutSoundUrl)
      audio.volume = 0.6
      return audio
    })
    poolRef.current = pool
    return () => {
      pool.forEach((audio) => audio.pause())
      poolRef.current = []
    }
  }, [])

  return useCallback((combo = 1) => {
    const pool = poolRef.current
    if (pool.length === 0) return
    const audio = pool[indexRef.current % pool.length]
    indexRef.current += 1
    audio.currentTime = 0
    audio.playbackRate = Math.min(1.35, 1 + (combo - 1) * 0.06)
    void audio.play().catch(() => {})
  }, [])
}
