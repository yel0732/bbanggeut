import { Navigate, useNavigate, useParams } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import fruitBgmUrl from '@/assets/audio/fruit-game/fruit-forest-rhythm.mp3'
import guideBackButtonUrl from '@/assets/images/game-entry/guide-back-btn.webp'
import guideBoardUrl from '@/assets/images/game-entry/guide-board.webp'
import guideStartButtonUrl from '@/assets/images/game-entry/guide-start-btn.webp'
import backgroundUrl from '@/assets/images/game-entry/game-select-bg.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { DobbleTutorialDemo } from '@/features/games/dobble/DobbleTutorialDemo'
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
          src={game.id === 'fruit' ? fruitBgmUrl : bgmUrl}
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

        {/* 나무 보드 패널 (Image 11) */}
        <div className="absolute top-1/2 left-1/2 h-[700px] w-[836px] -translate-x-1/2 -translate-y-1/2">
          <img
            alt=""
            className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_20px_40px_rgba(15,40,12,0.42)]"
            decoding="async"
            draggable={false}
            src={guideBoardUrl}
          />

          <div className="absolute top-[12%] right-[11%] bottom-[9%] left-[11%] flex flex-col items-center">
            <h2
              className="text-[40px] leading-none text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.5)]"
              style={jua}
            >
              게임 방법
            </h2>

            {game.id === 'dobble' ? (
              <div className="mt-5 flex w-full flex-1">
                <DobbleTutorialDemo />
              </div>
            ) : (
              <div
                className="mt-5 flex w-full flex-1 items-center justify-center rounded-[24px] border-[3px] border-dashed border-[#c39a5d] bg-[#fffaec]/70 text-[28px] text-[#9a6a34] shadow-[inset_0_2px_10px_rgba(120,80,30,0.12)]"
                style={jua}
              >
                게임 플레이 예시 영상
              </div>
            )}

            <p
              className="mt-5 max-w-[640px] text-center text-[25px] leading-snug text-[#6f4a1f]"
              style={jua}
            >
              {game.howTo}
            </p>

            <div className="mt-6 flex items-center justify-center gap-10">
              <GuideButton
                imageUrl={guideBackButtonUrl}
                label="다른 게임 할래요"
                labelClassName="text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.4)]"
                onClick={backToSelect}
              />
              <GuideButton
                imageUrl={guideStartButtonUrl}
                label="시작할래요"
                labelClassName="text-white [text-shadow:0_2px_0_#3f7d1e,0_5px_8px_rgba(20,50,15,0.3)]"
                onClick={startGame}
              />
            </div>
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

function GuideButton({
  imageUrl,
  label,
  labelClassName,
  onClick,
}: {
  imageUrl: string
  label: string
  labelClassName: string
  onClick: () => void
}) {
  return (
    <button
      className="relative h-[96px] w-[236px] border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98]"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-contain drop-shadow-[0_8px_12px_rgba(55,30,10,0.28)]"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className={`absolute inset-0 flex items-center justify-center pb-1 text-[24px] leading-none ${labelClassName}`}
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
