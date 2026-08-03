import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import backgroundUrl from '@/assets/images/child-home/background.webp'
import bubbleBigUrl from '@/assets/images/child-home/bubble-big.webp'
import bubbleMediumUrl from '@/assets/images/child-home/bubble-medium.webp'
import bubbleSmallUrl from '@/assets/images/child-home/bubble-small.webp'
import hoverCaveUrl from '@/assets/images/child-home/cave-hover-new.webp'
import caveUrl from '@/assets/images/child-home/cave-new.webp'
import playHoverUrl from '@/assets/images/child-home/play-hover.webp'
import playNoHoverUrl from '@/assets/images/child-home/play-no-hover.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { ChildProfileSwitchButton } from '@/components/ChildProfileSwitchButton'
import { DiaryBookButton } from '@/components/DiaryBookButton'
import { DinoCharacter } from '@/components/character/DinoCharacter'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const
export function ChildHomePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()
  const [isGateHovered, setIsGateHovered] = useState(false)
  const [isCaveHovered, setIsCaveHovered] = useState(false)

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
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

        <Link
          aria-label="프로필 선택으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-40 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
          to="/profiles"
        >
          <img
            alt=""
            className="size-full object-contain"
            decoding="async"
            draggable={false}
            src={leftButtonUrl}
          />
        </Link>

        {/* 문(門): 놀자! */}
        <button
          className="absolute top-[25px] left-[30px] z-[5] h-[300.5px] w-[410px] cursor-pointer border-none bg-none p-0"
          onClick={() => navigate('/games')}
          onMouseEnter={() => setIsGateHovered(true)}
          onMouseLeave={() => setIsGateHovered(false)}
          type="button"
        >
          <div className="absolute inset-0 origin-[50%_90%] [animation:child-home-leaf-sway_5s_ease-in-out_infinite,child-home-asset-bob_3s_ease-in-out_infinite]">
            <img
              alt="문"
              className={`absolute inset-0 h-full w-full [animation:child-home-edge-glow_2.2s_ease-in-out_infinite] object-contain transition-opacity duration-300 ease-in-out ${isGateHovered ? 'opacity-0' : 'opacity-100'}`}
              decoding="async"
              src={playNoHoverUrl}
            />
            <img
              alt=""
              aria-hidden="true"
              className={`absolute inset-0 h-full w-full [animation:child-home-edge-glow_2.2s_ease-in-out_infinite] object-contain transition-opacity duration-300 ease-in-out ${isGateHovered ? 'opacity-100' : 'opacity-0'}`}
              decoding="async"
              src={playHoverUrl}
            />
          </div>
          <div className="absolute top-0 left-[320px]">
            <div
              className={`relative flex h-[94px] w-[132px] items-center justify-center transition duration-[220ms] ${isGateHovered ? '-translate-y-1 scale-[1.08] brightness-110 drop-shadow-[0_8px_10px_rgba(255,229,134,0.38)]' : ''}`}
            >
              <img
                alt=""
                className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
                decoding="async"
                src={bubbleSmallUrl}
              />
              <span
                className="relative z-[1] mb-1 text-[28px] whitespace-nowrap text-[#3a7d2c]"
                style={jua}
              >
                놀자!
              </span>
            </div>
          </div>
        </button>

        {/* 동굴 집: 대화 할래! */}
        <button
          className="absolute top-[25px] right-[75px] z-[5] h-[255px] w-[340px] cursor-pointer border-none bg-none p-0"
          onClick={() => navigate('/conversation')}
          onMouseEnter={() => setIsCaveHovered(true)}
          onMouseLeave={() => setIsCaveHovered(false)}
          type="button"
        >
          <div className="absolute top-[5px] right-[-10px] bottom-[-13px] left-[-10px]">
            <div className="absolute inset-0 [animation:child-home-asset-bob_3.4s_ease-in-out_infinite]">
              <img
                alt="동굴 집"
                className={`absolute inset-0 h-full w-full [animation:child-home-edge-glow_2.6s_ease-in-out_infinite] object-contain transition-opacity duration-300 ease-in-out ${isCaveHovered ? 'opacity-0' : 'opacity-100'}`}
                decoding="async"
                src={caveUrl}
              />
              <img
                alt=""
                aria-hidden="true"
                className={`absolute inset-0 h-full w-full [animation:child-home-edge-glow_2.6s_ease-in-out_infinite] object-contain transition-opacity duration-300 ease-in-out ${isCaveHovered ? 'opacity-100' : 'opacity-0'}`}
                decoding="async"
                src={hoverCaveUrl}
              />
            </div>
          </div>
          <div className="absolute top-0 left-[-150px]">
            <div
              className={`relative flex h-[96px] w-[178px] items-center justify-center transition duration-[220ms] ${isCaveHovered ? '-translate-y-1 scale-[1.08] brightness-110 drop-shadow-[0_8px_10px_rgba(255,212,117,0.38)]' : ''}`}
            >
              <img
                alt=""
                className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
                decoding="async"
                src={bubbleMediumUrl}
              />
              <span
                className="relative z-[1] mb-1.5 text-[28px] whitespace-nowrap text-[#c96a2e]"
                style={jua}
              >
                대화 할래!
              </span>
            </div>
          </div>
        </button>

        {/* 공룡 캐릭터 */}
        <div className="absolute top-[280px] left-1/2 h-[300px] w-[336px] -translate-x-1/2">
          <div className="absolute top-[-105px] left-[148px] h-[122px] w-[270px] -translate-x-1/2">
            <img
              alt=""
              className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
              decoding="async"
              src={bubbleBigUrl}
            />
            <span
              className="absolute top-[46%] right-0 left-0 -translate-y-1/2 text-center text-[32px] whitespace-nowrap text-[#2f6d3a]"
              style={jua}
            >
              오늘은 뭘 할까?
            </span>
          </div>

          <div className="absolute top-[30px] left-[8px] h-[320px] w-[320px]">
            <DinoCharacter
              className="absolute bottom-0 left-1/2 -translate-x-1/2"
              size={320}
            />
          </div>
        </div>

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

        <ChildProfileSwitchButton />

        <DiaryBookButton className="absolute bottom-[100px] left-[30px] z-50 size-[62px]" />
      </div>
    </div>
  )
}
