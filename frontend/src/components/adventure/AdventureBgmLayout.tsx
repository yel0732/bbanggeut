import { Outlet } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { AdventureBgmContext } from '@/hooks/useAdventureBgm'

/**
 * 부모 모드·게임 플레이·대화·일기장을 뺀 나머지 화면(시작·인증·프로필·아이
 * 홈·게임 결과) 전용 레이아웃.
 *
 * `<audio>`를 화면마다 두면 라우트를 옮길 때마다 엘리먼트가 새로 마운트되어
 * 재생이 끊겼다가 다시 이어지는 티가 났다. 이 화면들은 모두 같은 상위 라우트
 * 아래 묶여 있어 레이아웃 자체는 이동 중에도 언마운트되지 않으므로,
 * `<audio>`를 여기서 한 번만 렌더링하고 재생 상태는 컨텍스트로 내려준다.
 */
export function AdventureBgmLayout() {
  const { audioRef, isBgmPlaying, playBgm, toggleBgm } = useBgmPlayer()

  return (
    <AdventureBgmContext.Provider value={{ isBgmPlaying, playBgm, toggleBgm }}>
      <audio
        aria-label="초록 모험 배경 음악"
        loop
        preload="metadata"
        ref={audioRef}
        src={bgmUrl}
      />
      <Outlet />
    </AdventureBgmContext.Provider>
  )
}
