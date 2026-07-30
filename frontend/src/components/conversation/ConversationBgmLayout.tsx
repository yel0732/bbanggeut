import { useCallback, useRef } from 'react'
import { Outlet } from 'react-router-dom'

import conversationBgmUrl from '@/assets/audio/conversation/todak-room.mp3'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { ConversationBgmContext } from '@/hooks/useConversationBgm'

const DEFAULT_VOLUME = 0.22

/**
 * 대화 화면·일기장 전용 레이아웃.
 *
 * `<audio>`를 화면마다 두면 라우트를 옮길 때마다 엘리먼트가 새로 마운트되어
 * 재생이 끊겼다가 다시 이어지는 티가 났다. 이 두 라우트는 같은 상위 라우트
 * 아래 묶여 있어 레이아웃 자체는 이동 중에도 언마운트되지 않으므로,
 * `<audio>`를 여기서 한 번만 렌더링하고 재생 상태는 컨텍스트로 내려준다.
 *
 * 대화 화면은 빵긋이가 말하거나 들을 때 배경음을 줄이려고 볼륨을 자주
 * 바꾼다. `useBgmPlayer`의 `volume` 옵션은 바뀔 때마다 마운트 이펙트 전체를
 * 다시 돌려 재생 위치가 튈 수 있어서, `setVolume`으로 오디오 엘리먼트의
 * 볼륨만 직접 조정한다.
 *
 * `useBgmPlayer`의 `toggleBgm`은 음소거를 풀 때 이 훅에 고정된 기본 볼륨으로
 * 되돌린다. 그래서 대화 중 낮춰 둔 볼륨에서 음소거했다 풀면 소리가 커지는
 * 문제가 있었다. 마지막으로 요청받은 볼륨을 `lastVolumeRef`에 기억해 뒀다가,
 * 토글 직후 다시 그 값으로 덮어써서 페이지가 원하는 볼륨을 유지한다.
 */
export function ConversationBgmLayout() {
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer({
    volume: DEFAULT_VOLUME,
  })
  const lastVolumeRef = useRef(DEFAULT_VOLUME)

  const setVolume = useCallback(
    (volume: number) => {
      lastVolumeRef.current = volume
      const audio = audioRef.current
      if (audio) audio.volume = volume
    },
    [audioRef],
  )

  const handleToggleBgm = useCallback(async () => {
    await toggleBgm()
    const audio = audioRef.current
    if (audio) audio.volume = lastVolumeRef.current
  }, [audioRef, toggleBgm])

  return (
    <ConversationBgmContext.Provider
      value={{ isBgmPlaying, setVolume, toggleBgm: handleToggleBgm }}
    >
      <audio
        aria-label="대화방 배경 음악"
        loop
        preload="auto"
        ref={audioRef}
        src={conversationBgmUrl}
      />
      <Outlet />
    </ConversationBgmContext.Provider>
  )
}
