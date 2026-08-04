import { Outlet } from 'react-router-dom'

import bgmUrl from '@/assets/audio/parent-mode/parent-report-bgm.mp3'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { ParentBgmContext } from '@/hooks/useParentBgm'

/**
 * 부모 모드 화면(부모 홈·리포트 목록·리포트 상세) 전용 레이아웃.
 *
 * `<audio>`를 화면마다 두면 라우트를 옮길 때마다 엘리먼트가 새로 마운트되어
 * 재생이 끊겼다가 다시 이어지는 티가 났다. 이 세 라우트는 같은 상위 라우트
 * 아래 묶여 있어 레이아웃 자체는 이동 중에도 언마운트되지 않으므로,
 * `<audio>`를 여기서 한 번만 렌더링하고 재생 상태는 컨텍스트로 내려준다.
 */
export function ParentBgmLayout() {
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()

  return (
    <ParentBgmContext.Provider value={{ isBgmPlaying, toggleBgm }}>
      <audio
        aria-label="부모 모드 배경 음악"
        loop
        preload="metadata"
        ref={audioRef}
        src={bgmUrl}
      />
      <Outlet />
    </ParentBgmContext.Provider>
  )
}
