import { Navigate, useNavigate, useParams } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import gameMenuButtonUrl from '@/assets/images/game-entry/game-menu-button.webp'
import backgroundUrl from '@/assets/images/game-entry/game-select-bg.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { getGameById } from '@/features/games/games'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function GameGuidePage() {
  const navigate = useNavigate()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()

  const game = getGameById(gameId)
  if (!game) {
    return <Navigate replace to="/games" />
  }

  const backToSelect = () => navigate('/games')
  const startGame = () => navigate(`/games/${game.id}/play`)

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden text-white"
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
          autoPlay
          loop
          preload="auto"
          ref={audioRef}
          src={bgmUrl}
        />

        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(4,18,9,0.14)_0%,rgba(4,18,9,0.05)_42%,rgba(4,18,9,0.22)_100%)]" />

        <button
          aria-label="게임 선택으로 돌아가기"
          className="absolute top-[25px] left-[45px] z-30 size-[66px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={backToSelect}
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

        <button
          aria-label="게임 방법 닫기"
          className="absolute top-[24px] right-[42px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={backToSelect}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            draggable={false}
            src={closeButtonUrl}
          />
        </button>

        <div className="absolute top-[92px] left-1/2 z-10 h-[556px] w-[936px] -translate-x-1/2 rounded-[40px] border-[10px] border-[#8a5a2b] bg-[#f4ead0] shadow-[0_22px_44px_rgba(20,48,16,0.4),inset_0_0_0_5px_rgba(154,101,49,0.28),inset_0_0_60px_rgba(155,109,54,0.14)]">
          <div className="absolute -top-[42px] left-1/2 flex h-[82px] w-[300px] -translate-x-1/2 items-center justify-center rounded-[26px] border-[6px] border-[#8a5a2b] bg-[linear-gradient(180deg,#f4c65a_0%,#e6a838_100%)] shadow-[0_10px_20px_rgba(40,70,20,0.32),inset_0_3px_0_rgba(255,255,255,0.4)]">
            <span
              className="text-[36px] leading-none text-white [text-shadow:0_3px_0_#9a6a2f,0_6px_10px_rgba(58,31,11,0.32)]"
              style={jua}
            >
              게임 방법
            </span>
          </div>

          <div className="absolute inset-x-[64px] top-[86px] bottom-[130px] flex flex-col items-center justify-center gap-8">
            <div
              className="flex h-[236px] w-full items-center justify-center rounded-[24px] border-[3px] border-dashed border-[#b98a4d] bg-[#fffaec] text-[30px] text-[#9a6a34] shadow-[inset_0_2px_10px_rgba(120,80,30,0.14)]"
              style={jua}
            >
              게임 플레이 예시 영상
            </div>
            <p
              className="max-w-[720px] text-center text-[26px] leading-snug text-[#6f4a1f]"
              style={jua}
            >
              {game.howTo}
            </p>
          </div>

          <div className="absolute inset-x-0 bottom-[34px] flex items-center justify-center gap-16">
            <WoodPlaqueButton label="다른 게임 할래요" onClick={backToSelect} />
            <WoodPlaqueButton label="시작할래요" onClick={startGame} />
          </div>
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[28px] left-[48px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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

function WoodPlaqueButton({
  label,
  onClick,
}: {
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="relative h-[74px] w-[236px] border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98]"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_12px_rgba(55,30,10,0.28)]"
        decoding="async"
        draggable={false}
        src={gameMenuButtonUrl}
      />
      <span
        className="absolute inset-0 flex items-center justify-center pb-1 text-[26px] leading-none text-white [text-shadow:0_3px_0_#744514,0_6px_9px_rgba(55,30,10,0.24)]"
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
