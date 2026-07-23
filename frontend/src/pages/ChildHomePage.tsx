import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/child-home/background.webp'
import bubbleBigUrl from '@/assets/images/child-home/bubble-big.webp'
import bubbleMediumUrl from '@/assets/images/child-home/bubble-medium.webp'
import bubbleSmallUrl from '@/assets/images/child-home/bubble-small.webp'
import caveUrl from '@/assets/images/child-home/cave.webp'
import dino2Url from '@/assets/images/child-home/dino-2.webp'
import dino3Url from '@/assets/images/child-home/dino-3.webp'
import dino4Url from '@/assets/images/child-home/dino-4.webp'
import dino5Url from '@/assets/images/child-home/dino-5.webp'
import gateUrl from '@/assets/images/child-home/gate.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const DINO_FRAMES: Record<number, string> = {
  2: dino2Url,
  3: dino3Url,
  4: dino4Url,
  5: dino5Url,
}
const DINO_STEP_SEQUENCE = [3, 2, 5, 4, 5, 2, 3]
const DINO_STEP_INTERVAL_MS = 320

export function ChildHomePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [isGateHovered, setIsGateHovered] = useState(false)
  const [isCaveHovered, setIsCaveHovered] = useState(false)
  const [dinoStep, setDinoStep] = useState(0)
  const [dinoFramesReady, setDinoFramesReady] = useState(false)

  useEffect(() => {
    let cancelled = false
    const frames = Object.values(DINO_FRAMES)
    Promise.all(
      frames.map(
        (frame) =>
          new Promise<void>((resolve) => {
            const image = new Image()
            image.onload = () => {
              void image
                .decode()
                .catch(() => undefined)
                .finally(resolve)
            }
            image.onerror = () => resolve()
            image.src = frame
          }),
      ),
    ).then(() => {
      if (!cancelled) setDinoFramesReady(true)
    })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!dinoFramesReady) return
    const id = window.setInterval(() => {
      if (document.hidden) return
      setDinoStep((step) => (step + 1) % DINO_STEP_SEQUENCE.length)
    }, DINO_STEP_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [dinoFramesReady])

  const activeDinoFrame = DINO_STEP_SEQUENCE[dinoStep]

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
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

        {/* 문(門): 놀자! */}
        <button
          className="absolute top-[25px] left-[90px] z-[5] h-[277.5px] w-[370px] cursor-pointer border-none bg-none p-0"
          onClick={() => navigate('/games')}
          onMouseEnter={() => setIsGateHovered(true)}
          onMouseLeave={() => setIsGateHovered(false)}
          type="button"
        >
          <div className="absolute inset-0 origin-[50%_90%] [animation:child-home-leaf-sway_5s_ease-in-out_infinite,child-home-asset-bob_3s_ease-in-out_infinite] transition-transform duration-[180ms] hover:scale-[1.06]">
            <img
              alt="문"
              className="h-full w-full [animation:child-home-edge-glow_2.2s_ease-in-out_infinite] object-contain"
              decoding="async"
              src={gateUrl}
            />
          </div>
          <div className="absolute top-0 left-[300px]">
            <div
              className="relative flex h-[94px] w-[132px] items-center justify-center transition-opacity duration-200"
              style={{ opacity: isGateHovered ? 1 : 0 }}
            >
              {isGateHovered ? (
                <img
                  alt=""
                  className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
                  decoding="async"
                  src={bubbleSmallUrl}
                />
              ) : null}
              <span
                className="relative z-[1] mb-3.5 text-[24px] whitespace-nowrap text-[#3a7d2c]"
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
          <div className="absolute inset-0 [animation:child-home-asset-bob_3.4s_ease-in-out_infinite] transition-transform duration-[180ms] hover:scale-[1.06]">
            <img
              alt="동굴 집"
              className="h-full w-full [animation:child-home-edge-glow_2.6s_ease-in-out_infinite] object-contain"
              decoding="async"
              src={caveUrl}
            />
          </div>
          <div className="absolute top-0 left-[-145px]">
            <div
              className="relative flex h-[96px] w-[178px] items-center justify-center transition-opacity duration-200"
              style={{ opacity: isCaveHovered ? 1 : 0 }}
            >
              {isCaveHovered ? (
                <img
                  alt=""
                  className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
                  decoding="async"
                  src={bubbleMediumUrl}
                />
              ) : null}
              <span
                className="relative z-[1] mb-4 text-[21px] whitespace-nowrap text-[#c96a2e]"
                style={jua}
              >
                대화 할래!
              </span>
            </div>
          </div>
        </button>

        {/* 공룡 캐릭터 */}
        <div className="absolute top-[300px] left-1/2 h-[300px] w-[336px] -translate-x-1/2">
          <div className="absolute top-[-115px] left-1/2 h-[122px] w-[270px] -translate-x-1/2">
            <img
              alt=""
              className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_14px_rgba(0,0,0,0.14)]"
              decoding="async"
              src={bubbleBigUrl}
            />
            <span
              className="absolute top-[41%] right-0 left-0 -translate-y-1/2 text-center text-[28px] whitespace-nowrap text-[#2f6d3a]"
              style={jua}
            >
              오늘은 뭘 할까?
            </span>
          </div>

          <div
            aria-label="공룡"
            className="absolute top-[30px] left-[8px] h-[320px] w-[320px] [animation:child-home-dino-bob_3.2s_ease-in-out_infinite]"
            role="img"
          >
            <div className="absolute bottom-2 left-1/2 h-10 w-[210px] -translate-x-1/2 rounded-full bg-[radial-gradient(50%_50%_at_50%_50%,_rgba(60,110,50,0.42),_rgba(60,110,50,0)_100%)]" />
            {dinoFramesReady ? (
              Object.entries(DINO_FRAMES).map(([frameNumber, frame]) => (
                <img
                  alt=""
                  className="absolute bottom-0 left-1/2 h-[340px] w-auto max-w-none -translate-x-1/2 drop-shadow-[0_12px_12px_rgba(0,0,0,0.22)] will-change-[opacity]"
                  decoding="sync"
                  key={frame}
                  src={frame}
                  style={{
                    opacity: activeDinoFrame === Number(frameNumber) ? 1 : 0,
                  }}
                />
              ))
            ) : (
              <img
                alt=""
                className="absolute bottom-0 left-1/2 h-[340px] w-auto max-w-none -translate-x-1/2 drop-shadow-[0_12px_12px_rgba(0,0,0,0.22)]"
                decoding="async"
                src={DINO_FRAMES[activeDinoFrame]}
              />
            )}
          </div>
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[12px] left-[78px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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

        <Link
          aria-label="프로필 바꾸기"
          className="absolute top-[16px] right-[78px] z-40 rounded-full border-2 border-white/70 bg-black/35 px-4 py-2 text-sm text-white backdrop-blur-sm transition hover:scale-105"
          style={jua}
          to="/profiles"
        >
          프로필 바꾸기
        </Link>
      </div>
    </div>
  )
}
