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

/**
 * 음악을 껐는지 여부.
 *
 * 화면마다 `<audio>`가 새로 생기기 때문에 이 값이 없으면 껐어도 다음 화면에서
 * 다시 켜진다. 위치와 달리 localStorage에 둔다 — 껐다는 뜻은 "지금 이 탭에서만"이
 * 아니라 "시끄러우니 끄고 싶다"이므로 다음에 열 때도 유지되는 쪽이 맞다.
 */
const MUTED_KEY = 'bbanggut-bgm-muted'

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

function readMuted(): boolean {
  try {
    return window.localStorage.getItem(MUTED_KEY) === 'true'
  } catch {
    return false
  }
}

function writeMuted(muted: boolean): void {
  try {
    window.localStorage.setItem(MUTED_KEY, String(muted))
  } catch {
    // 저장에 실패해도 이번 화면에서는 껐다 켠 대로 동작한다.
  }
}

/**
 * 화면 배경 음악.
 *
 * 재생 여부는 이 훅이 혼자 결정한다. `<audio>`에 `autoPlay`를 붙이면 껐다는
 * 기억과 태그의 자동 재생이 서로 싸워서 화면을 옮길 때마다 다시 켜진다.
 */
export function useBgmPlayer({ volume = 0.4 }: UseBgmPlayerOptions = {}) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)

  const playBgm = useCallback(async () => {
    const audio = audioRef.current

    // 아이가 껐다면 다른 이유(화면 진입 등)로 다시 켜지 않는다.
    if (!audio || !audio.paused || readMuted()) {
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
      // 껐다는 기억을 먼저 지워야 playBgm이 막지 않는다.
      writeMuted(false)
      await playBgm()
      return
    }

    writeMuted(true)
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
      if (readMuted()) return
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

    /*
      재생 위치는 화면을 떠날 때만 저장하면 부족하다. 새로고침이나 탭 닫기는
      언마운트가 아니라 문서가 통째로 사라지는 것이어서 정리 함수가 돌지 않는다.
      그러면 새로고침마다 곡이 처음부터 다시 시작한다.

      `pagehide`는 새로고침·이동·탭 닫기에서 모두 불린다. 모바일에서는 앱을
      전환하면 `pagehide` 없이 문서가 버려지기도 해서 화면이 숨을 때도 같이 저장한다.
    */
    const persistPosition = () => {
      if (Number.isFinite(audio.currentTime)) {
        writePosition(sourceKey, audio.currentTime)
      }
    }
    const persistOnHide = () => {
      if (document.visibilityState === 'hidden') persistPosition()
    }
    window.addEventListener('pagehide', persistPosition)
    document.addEventListener('visibilitychange', persistOnHide)

    if (readMuted()) {
      // 껐다면 화면을 옮겨도 계속 꺼진 채로 둔다.
      setIsBgmPlaying(false)
    } else {
      void audio.play().then(
        () => setIsBgmPlaying(true),
        () => {
          setIsBgmPlaying(false)
          hookGesture()
        },
      )
    }

    return () => {
      audio.removeEventListener('loadedmetadata', restorePlaybackPosition)
      unhookGesture()
      window.removeEventListener('pagehide', persistPosition)
      document.removeEventListener('visibilitychange', persistOnHide)
      persistPosition()
    }
  }, [volume])

  return {
    audioRef,
    isBgmPlaying,
    playBgm,
    toggleBgm,
  }
}
