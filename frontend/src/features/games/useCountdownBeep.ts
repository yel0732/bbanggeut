import { useEffect, useRef } from 'react'

/**
 * 카운트다운 숫자가 바뀔 때마다 짧게 "삑".
 *
 * 마지막 1초만 음을 높여(660Hz → 880Hz) 곧 시작한다는 것을 귀로 알린다. 화면
 * 숫자만으로는 카메라 앞에서 손을 들고 있는 아이가 놓치기 쉽다.
 *
 * 전용 음원 없이 합성한다. 짧은 소리 하나를 위해 파일을 받게 하지 않는다.
 *
 * 같은 숫자로 여러 번 렌더되어도 한 번만 울린다 — 게임 루프가 매 프레임 상태를
 * 새로 내보내므로 이 검사가 없으면 소리가 뭉개진다.
 */
export function useCountdownBeep(active: boolean, countdown: number) {
  const lastCountdownRef = useRef<number | null>(null)

  useEffect(() => {
    if (!active) {
      lastCountdownRef.current = null
      return
    }
    if (lastCountdownRef.current === countdown) return
    lastCountdownRef.current = countdown

    const AudioContextClass =
      window.AudioContext ??
      (window as { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext
    if (!AudioContextClass) return

    const context = new AudioContextClass()
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = countdown === 1 ? 880 : 660
    gain.gain.setValueAtTime(0.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.22, context.currentTime + 0.01)
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.16)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + 0.17)
    oscillator.addEventListener('ended', () => void context.close())
  }, [active, countdown])
}
