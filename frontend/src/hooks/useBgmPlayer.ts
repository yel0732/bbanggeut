import { useCallback, useEffect, useRef, useState } from 'react'

type UseBgmPlayerOptions = {
  volume?: number
}

const playbackPositions = new Map<string, number>()

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

    const sourceKey = audio.src
    const restorePlaybackPosition = () => {
      const savedPosition = playbackPositions.get(sourceKey)
      if (savedPosition === undefined || !Number.isFinite(savedPosition)) return
      if (Math.abs(audio.currentTime - savedPosition) > 0.25) {
        audio.currentTime = savedPosition
      }
    }

    audio.volume = volume
    restorePlaybackPosition()
    audio.addEventListener('loadedmetadata', restorePlaybackPosition, {
      once: true,
    })

    void audio
      .play()
      .then(() => {
        setIsBgmPlaying(true)
      })
      .catch(() => {
        setIsBgmPlaying(false)
      })

    return () => {
      audio.removeEventListener('loadedmetadata', restorePlaybackPosition)
      if (Number.isFinite(audio.currentTime)) {
        playbackPositions.set(sourceKey, audio.currentTime)
      }
    }
  }, [volume])

  return {
    audioRef,
    isBgmPlaying,
    playBgm,
    toggleBgm,
  }
}
