import { useCallback, useEffect, useRef, useState } from 'react'

type UseBgmPlayerOptions = {
  volume?: number
}

/**
 * 재생 위치 저장 키.
 *
 * 화면을 옮길 때마다 `<audio>`가 새로 마운트되므로 위치를 어딘가에 남겨야 곡이
 * 처음부터 다시 시작하지 않는다. 모듈 변수로 두면 새로고침에 날아가서
 * sessionStorage를 쓴다. 탭을 닫으면 지워지므로 다음에 열 때는 처음부터다.
 */
const POSITION_KEY_PREFIX = 'bbanggut-bgm-position:'

function readPosition(sourceKey: string): number | null {
  try {
    const raw = window.sessionStorage.getItem(POSITION_KEY_PREFIX + sourceKey)
    if (raw === null) return null
    const value = Number(raw)
    return Number.isFinite(value) ? value : null
  } catch {
    // 사생활 보호 모드 등에서 접근이 막히면 위치 유지만 포기한다.
    return null
  }
}

function writePosition(sourceKey: string, position: number): void {
  try {
    window.sessionStorage.setItem(
      POSITION_KEY_PREFIX + sourceKey,
      String(position),
    )
  } catch {
    // 저장에 실패해도 재생 자체는 계속돼야 한다.
  }
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

    const sourceKey = audio.src
    const restorePlaybackPosition = () => {
      const savedPosition = readPosition(sourceKey)
      if (savedPosition === null) return
      if (Math.abs(audio.currentTime - savedPosition) > 0.25) {
        audio.currentTime = savedPosition
      }
    }

    audio.volume = volume
    restorePlaybackPosition()
    audio.addEventListener('loadedmetadata', restorePlaybackPosition, {
      once: true,
    })

    /**
     * 새로고침 직후에는 사용자 제스처가 없어 브라우저가 자동 재생을 막는다.
     * 조용히 넘기면 클릭할 일이 없는 화면(플레이 화면 등)에서 BGM이 영영 안
     * 나오므로, 막혔을 때는 첫 입력이 들어오는 순간 한 번 더 시도한다.
     */
    const gestureEvents = ['pointerdown', 'keydown', 'touchstart'] as const
    const retryOnGesture = () => {
      void audio.play().then(
        () => setIsBgmPlaying(true),
        () => setIsBgmPlaying(false),
      )
    }
    let gestureHooked = false
    const hookGesture = () => {
      if (gestureHooked) return
      gestureHooked = true
      gestureEvents.forEach((type) =>
        window.addEventListener(type, retryOnGesture, { once: true }),
      )
    }
    const unhookGesture = () => {
      gestureEvents.forEach((type) =>
        window.removeEventListener(type, retryOnGesture),
      )
    }

    void audio.play().then(
      () => setIsBgmPlaying(true),
      () => {
        setIsBgmPlaying(false)
        hookGesture()
      },
    )

    return () => {
      audio.removeEventListener('loadedmetadata', restorePlaybackPosition)
      unhookGesture()
      if (Number.isFinite(audio.currentTime)) {
        writePosition(sourceKey, audio.currentTime)
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
