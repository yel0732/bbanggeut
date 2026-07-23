import { useCallback, useEffect, useRef, useState } from 'react'

type UseBgmPlayerOptions = {
  volume?: number
}

export function useBgmPlayer({ volume = 0.4 }: UseBgmPlayerOptions = {}) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)

  const playBgm = useCallback(async () => {
    const audio = audioRef.current

    if (!audio || !audio.paused) {
      return
    }

    try {
      audio.volume = volume
      await audio.play()
      setIsBgmPlaying(true)
    } catch {
      setIsBgmPlaying(false)
    }
  }, [volume])

  const toggleBgm = useCallback(async () => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    if (audio.paused) {
      await playBgm()
      return
    }

    audio.pause()
    setIsBgmPlaying(false)
  }, [playBgm])

  useEffect(() => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    audio.volume = volume

    void audio
      .play()
      .then(() => {
        setIsBgmPlaying(true)
      })
      .catch(() => {
        setIsBgmPlaying(false)
      })
  }, [volume])

  return {
    audioRef,
    isBgmPlaying,
    playBgm,
    toggleBgm,
  }
}
