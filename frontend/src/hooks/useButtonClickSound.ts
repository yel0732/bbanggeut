import { useEffect } from 'react'

const CLICKABLE_SELECTOR = 'button, a, [role="button"]'

export function useButtonClickSound() {
  useEffect(() => {
    let audioContext: AudioContext | null = null

    const playClickSound = () => {
      audioContext ??= new AudioContext()
      const context = audioContext
      if (context.state === 'suspended') void context.resume()

      const now = context.currentTime
      const gain = context.createGain()
      const lowNote = context.createOscillator()
      const highNote = context.createOscillator()

      lowNote.type = 'sine'
      lowNote.frequency.setValueAtTime(620, now)
      lowNote.frequency.exponentialRampToValueAtTime(760, now + 0.08)
      highNote.type = 'triangle'
      highNote.frequency.setValueAtTime(930, now + 0.025)
      highNote.frequency.exponentialRampToValueAtTime(1120, now + 0.11)

      gain.gain.setValueAtTime(0.0001, now)
      gain.gain.exponentialRampToValueAtTime(0.045, now + 0.008)
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.14)

      lowNote.connect(gain)
      highNote.connect(gain)
      gain.connect(context.destination)
      lowNote.start(now)
      highNote.start(now + 0.025)
      lowNote.stop(now + 0.14)
      highNote.stop(now + 0.14)
    }

    const handleClick = (event: MouseEvent) => {
      if (!(event.target instanceof Element)) return
      const target = event.target.closest<HTMLElement>(CLICKABLE_SELECTOR)
      if (!target) return
      if (
        (target instanceof HTMLButtonElement && target.disabled) ||
        target.getAttribute('aria-disabled') === 'true'
      ) {
        return
      }
      playClickSound()
    }

    document.addEventListener('click', handleClick, true)
    return () => {
      document.removeEventListener('click', handleClick, true)
      if (audioContext) void audioContext.close()
    }
  }, [])
}
