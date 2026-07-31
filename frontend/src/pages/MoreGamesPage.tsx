import { useNavigate } from 'react-router-dom'

import backgroundUrl from '@/assets/images/game-entry/game-select-bg-2.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { ChildProfileSwitchButton } from '@/components/ChildProfileSwitchButton'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'

/**
 * 게임 선택 두 번째 화면.
 *
 * 첫 번째 화면(GameEntryPage)의 오른쪽 화살표로 들어온다. 이 화면에 들어갈
 * 새 게임 두 개는 아직 만들어지지 않아, 지금은 배경·뒤로가기·음소거·프로필
 * 바꾸기만 연결해 둔다.
 */
export function MoreGamesPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(4,18,9,0.08)_0%,rgba(4,18,9,0)_42%,rgba(4,18,9,0.12)_100%)]" />

        <button
          aria-label="이전 화면으로"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={() => navigate('/games')}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            draggable={false}
            src={leftButtonUrl}
          />
        </button>

        <ChildProfileSwitchButton />

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
          onClick={() => {
            void toggleBgm()
          }}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>
      </div>
    </div>
  )
}
