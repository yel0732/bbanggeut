import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/wordmatch-game/alphabet-gather.mp3'
import backgroundUrl from '@/assets/images/wordmatch-game/background.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { getGameById } from '@/features/games/games'
import { WordMatchBoard } from '@/features/games/wordmatch/WordMatchBoard'
import {
  WORD_MATCH_CONFIG,
  WORD_MATCH_PRACTICE,
} from '@/features/games/wordmatch/wordMatchConfig'
import { useWordMatchInteraction } from '@/features/games/wordmatch/useWordMatchInteraction'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandGestures } from '@/hooks/useHandGestures'

type Phase = 'camera' | 'ready' | 'countdown' | 'playing' | 'finished'

function formatTime(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export function WordMatchPlayPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { requestCamera, status, videoRef } = useCameraStream()
  const { audioRef, isBgmPlaying, playBgm, toggleBgm } = useBgmPlayer({
    volume: 0.24,
  })
  const gestures = useHandGestures(videoRef, status === 'granted')
  const [phase, setPhase] = useState<Phase>('camera')
  const [countdown, setCountdown] = useState<number>(
    WORD_MATCH_CONFIG.countdownSec,
  )
  const [timeLeft, setTimeLeft] = useState<number>(
    WORD_MATCH_CONFIG.durationSec,
  )

  const interaction = useWordMatchInteraction({
    active: phase === 'playing',
    gesturesRef: gestures.gesturesRef,
    problem: WORD_MATCH_PRACTICE,
  })
  const game = getGameById('wordmatch')

  useEffect(() => {
    void requestCamera()
  }, [requestCamera])

  useEffect(() => {
    if (status === 'granted') setPhase('ready')
  }, [status])

  useEffect(() => {
    if (phase !== 'ready') return
    let frameId = 0
    const checkOpenHand = () => {
      const frame = gestures.gesturesRef.current
      const hand = frame.a ?? frame.b
      if (hand && !hand.fist) {
        setPhase('countdown')
        return
      }
      frameId = requestAnimationFrame(checkOpenHand)
    }
    frameId = requestAnimationFrame(checkOpenHand)
    return () => cancelAnimationFrame(frameId)
  }, [gestures.gesturesRef, phase])

  useEffect(() => {
    if (phase !== 'countdown') return
    setCountdown(WORD_MATCH_CONFIG.countdownSec)
    void playBgm()
    const timer = window.setInterval(() => {
      setCountdown((current) => {
        if (current <= 1) {
          window.clearInterval(timer)
          setPhase('playing')
          return 0
        }
        return current - 1
      })
    }, 1000)
    return () => window.clearInterval(timer)
  }, [phase, playBgm])

  useEffect(() => {
    if (phase !== 'playing') return
    const timer = window.setInterval(() => {
      setTimeLeft((current) => {
        if (current <= 1) {
          window.clearInterval(timer)
          setPhase('finished')
          return 0
        }
        return current - 1
      })
    }, 1000)
    return () => window.clearInterval(timer)
  }, [phase])

  if (!game) return <Navigate replace to="/games" />

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[#17344f]">
      <div
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
        ref={canvasRef}
      >
        <audio loop preload="metadata" ref={audioRef} src={bgmUrl} />
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          src={backgroundUrl}
        />

        <div className="absolute inset-0">
          <WordMatchBoard
            interaction={interaction}
            problem={WORD_MATCH_PRACTICE}
          />
        </div>

        <div className="absolute top-5 left-1/2 z-50 -translate-x-1/2 rounded-full bg-[#365c78]/90 px-8 py-3 text-[38px] shadow-lg">
          {formatTime(timeLeft)}
        </div>
        <button
          className="absolute top-5 right-5 z-50 rounded-full bg-white/90 px-6 py-3 text-[24px] text-[#365c78] shadow-lg"
          onClick={() => navigate('/games/more')}
          type="button"
        >
          나가기
        </button>
        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-5 left-5 z-50 size-[62px] border-0 bg-transparent"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img alt="" src={isBgmPlaying ? musicOnUrl : musicOffUrl} />
        </button>

        {status === 'granted' ? (
          <video
            autoPlay
            className="pointer-events-none absolute right-4 bottom-4 z-50 h-[105px] w-[140px] scale-x-[-1] rounded-2xl border-[3px] border-white/70 object-cover shadow-lg"
            muted
            playsInline
            ref={videoRef}
          />
        ) : null}

        {phase !== 'playing' ? (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-[#18384b]/70">
            <div className="rounded-[36px] bg-white/95 px-16 py-10 text-center text-[#365c78] shadow-2xl">
              {phase === 'camera' ? (
                <>
                  <p className="text-[36px]">카메라를 준비하고 있어요</p>
                  <p className="mt-3 text-[22px]">
                    권한 창이 나타나면 허용을 눌러 주세요.
                  </p>
                </>
              ) : phase === 'ready' ? (
                <>
                  <p className="text-[38px]">한 손을 펴서 보여 주세요!</p>
                  <p className="mt-3 text-[24px]">
                    손을 확인하면 게임이 시작돼요.
                  </p>
                </>
              ) : phase === 'countdown' ? (
                <p className="text-[110px] text-[#ff9f1c]">{countdown}</p>
              ) : (
                <>
                  <p className="text-[48px]">게임 끝!</p>
                  <button
                    className="mt-6 rounded-full bg-[#ff9f1c] px-10 py-4 text-[26px] text-white"
                    onClick={() => navigate('/games/more')}
                    type="button"
                  >
                    게임 선택으로
                  </button>
                </>
              )}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
