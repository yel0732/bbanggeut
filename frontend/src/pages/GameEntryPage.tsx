import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import dinoUrl from '@/assets/images/child-home/dino-2.webp'
import backgroundUrl from '@/assets/images/game-entry/game-select-bg.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import gameMenuButtonUrl from '@/assets/images/game-entry/game-menu-button.webp'
import gameMenuPanelUrl from '@/assets/images/game-entry/game-menu-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { GAMES } from '@/features/games/games'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function GameEntryPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [hoveredGameId, setHoveredGameId] = useState<string>()

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
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(4,18,9,0.08)_0%,rgba(4,18,9,0)_42%,rgba(4,18,9,0.12)_100%)]" />

        <button
          aria-label="이전 화면으로"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={() => navigate('/child')}
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
          className="absolute top-[16px] right-[78px] z-40 rounded-full border-2 border-white/70 bg-black/35 px-4 py-2 text-sm text-white backdrop-blur-sm transition hover:scale-105"
          onClick={() => navigate('/profiles')}
          style={jua}
          type="button"
        >
          프로필 바꾸기
        </button>

        {GAMES.map((game) => {
          const isFlipped = hoveredGameId === game.id

          return (
            <div
              className={`absolute z-20 flex w-[270px] [animation:child-home-asset-bob_3.5s_ease-in-out_infinite] flex-col items-center gap-7 transition duration-200 focus-within:-translate-y-2 focus-within:scale-[1.03] hover:-translate-y-2 hover:scale-[1.03] ${game.floatClassName} ${game.positionClassName}`}
              key={game.id}
              onMouseEnter={() => setHoveredGameId(game.id)}
              onMouseLeave={() => setHoveredGameId(undefined)}
            >
              <span className="relative h-[300px] w-[270px] [perspective:900px]">
                <span
                  className={`absolute inset-0 transition-transform duration-700 [transform-style:preserve-3d] ${isFlipped ? '[transform:rotateY(180deg)]' : ''}`}
                >
                  <button
                    aria-label={`${game.label} 게임 선택`}
                    className={`absolute inset-0 grid place-items-center border-0 bg-transparent p-0 [backface-visibility:hidden] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98] ${isFlipped ? 'pointer-events-none' : ''}`}
                    onClick={() => setHoveredGameId(game.id)}
                    onFocus={() => setHoveredGameId(game.id)}
                    type="button"
                  >
                    <span className="absolute inset-[12px] rounded-[38px] bg-[#f9e4a6]/32 shadow-[0_14px_30px_rgba(31,70,22,0.28),inset_0_0_0_4px_rgba(91,63,32,0.24)]" />
                    <img
                      alt=""
                      className="relative z-[1] h-full w-full [animation:child-home-edge-glow_3s_ease-in-out_infinite] object-contain drop-shadow-[0_16px_22px_rgba(30,60,22,0.34)]"
                      decoding="async"
                      src={game.cardImageUrl}
                    />
                  </button>
                  <div
                    aria-hidden={!isFlipped}
                    className={`absolute inset-0 flex [transform:rotateY(180deg)] items-center justify-center [backface-visibility:hidden] ${isFlipped ? 'pointer-events-auto' : 'pointer-events-none'}`}
                  >
                    <span className="relative h-[324px] w-[237px]">
                      <img
                        alt=""
                        className="absolute inset-0 h-full w-full object-contain drop-shadow-[0_18px_28px_rgba(22,50,16,0.35)]"
                        decoding="async"
                        src={gameMenuPanelUrl}
                      />
                      <span
                        className="absolute top-[48px] right-6 left-6 text-center text-[22px] leading-none text-white [text-shadow:0_3px_0_#724216,0_6px_10px_rgba(58,31,11,0.22)]"
                        style={jua}
                      >
                        {game.label}
                      </span>
                      <span className="absolute top-[108px] right-0 left-0 flex flex-col items-center gap-4">
                        <GameMenuButton
                          label="게임 시작"
                          onClick={() => navigate(`/games/${game.id}/play`)}
                          size="card"
                        />
                        <GameMenuButton
                          label="게임 방법"
                          onClick={() => navigate(`/games/${game.id}/guide`)}
                          size="card"
                        />
                      </span>
                    </span>
                  </div>
                </span>
              </span>
              <span
                className="relative z-20 rounded-full bg-[#173618]/45 px-5 py-2 text-[28px] leading-none text-white shadow-[0_5px_12px_rgba(12,38,13,0.22),inset_0_0_0_2px_rgba(255,255,255,0.12)] backdrop-blur-[1px] [text-shadow:0_3px_0_#2e6d2e,0_6px_8px_rgba(9,35,10,0.52)]"
                style={jua}
              >
                {game.label}
              </span>
            </div>
          )
        })}

        <div className="absolute top-[200px] left-1/2 z-20 -translate-x-1/2">
          <div className="relative flex h-[88px] w-[258px] items-center justify-center rounded-[28px] bg-white px-8 text-center shadow-[0_10px_22px_rgba(35,80,35,0.22)] after:absolute after:right-[94px] after:bottom-[-24px] after:h-0 after:w-0 after:border-x-[18px] after:border-t-[26px] after:border-x-transparent after:border-t-white">
            <span
              className="text-[28px] leading-none whitespace-nowrap text-[#526d4c]"
              style={jua}
            >
              무슨 게임 할까?
            </span>
          </div>
        </div>

        <div
          aria-label="공룡"
          className="absolute bottom-[90px] left-1/2 z-10 h-[310px] w-[330px] -translate-x-1/2 [animation:child-home-dino-bob_3.2s_ease-in-out_infinite]"
          role="img"
        >
          <div className="absolute bottom-0 left-1/2 h-12 w-[230px] -translate-x-1/2 rounded-full bg-[radial-gradient(50%_50%_at_50%_50%,rgba(45,90,35,0.32),rgba(45,90,35,0)_100%)]" />
          <img
            alt=""
            className="absolute bottom-0 left-1/2 h-[330px] w-auto max-w-none -translate-x-1/2 drop-shadow-[0_16px_18px_rgba(25,54,25,0.26)]"
            decoding="async"
            src={dinoUrl}
          />
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[85px] left-[25px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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

function GameMenuButton({
  label,
  onClick,
  size = 'panel',
}: {
  label: string
  onClick: () => void
  size?: 'card' | 'panel'
}) {
  const isCardSize = size === 'card'

  return (
    <button
      className={`${isCardSize ? 'h-[50px] w-[168px]' : 'h-[82px] w-[272px]'} relative border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98]`}
      onClick={(event) => {
        event.stopPropagation()
        onClick()
      }}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_12px_rgba(55,30,10,0.28)]"
        decoding="async"
        src={gameMenuButtonUrl}
      />
      <span
        className={`${isCardSize ? 'text-[20px] [text-shadow:0_2px_0_#744514,0_5px_8px_rgba(55,30,10,0.22)]' : 'text-[32px] [text-shadow:0_4px_0_#744514,0_7px_10px_rgba(55,30,10,0.22)]'} absolute inset-0 flex items-center justify-center pb-1 leading-none text-white`}
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
