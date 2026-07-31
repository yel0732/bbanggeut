import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import {
  PauseButton,
  PauseOverlay,
  QuitButton,
} from '@/components/game/PlayOverlays'
import { DinoCharacter } from '@/components/character/DinoCharacter'
import type { GameInfo } from '@/features/games/games'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

/**
 * 아직 만들지 않은 게임의 플레이 화면 골격.
 *
 * 배경·BGM·시작 카운트다운·일시정지·점수 칸 자리를 미리 잡아 둔다. 판정과
 * 놀이판만 나중에 채우면 되도록, 실제 게임과 같은 위치·크기·연출을 쓴다.
 * 자리 값은 과일 자르기·그림 맞추기 HUD와 같아야 하므로 DobblePlayPage의
 * PlayHud와 나란히 두고 맞췄다.
 *
 * 남은 시간은 흐르지 않는다. 0이 되면 결과 화면으로 가야 하는데 그 화면도
 * 아직 없다. 자리와 글자 크기만 보여 주는 것이 목적이라 값을 멈춰 둔다.
 */

const jua = { fontFamily: "'Jua', sans-serif" } as const

/** 카운트다운 시작 숫자. 기존 게임과 같다. */
const COUNTDOWN_FROM = 3
/** 카운트다운 한 칸 길이(ms) */
const COUNTDOWN_STEP_MS = 1000
/** 점수 칸에 미리 넣어 둘 값 */
const PLACEHOLDER_SCORE = 0
/** 시간 칸에 미리 넣어 둘 값(초). 게임을 만들 때 실제 제한 시간으로 바꾼다. */
const PLACEHOLDER_REMAINING_SEC = 60

type Phase = 'countdown' | 'playing' | 'paused'

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

export function UpcomingGamePlay({
  backgroundUrl,
  bgmUrl,
  game,
}: {
  backgroundUrl: string
  bgmUrl: string
  game: GameInfo
}) {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer({ volume: 0.28 })

  const [phase, setPhase] = useState<Phase>('countdown')
  const [countdown, setCountdown] = useState(COUNTDOWN_FROM)
  // 일시정지에서 돌아올 때 카운트다운으로 되돌아가지 않게 직전 단계를 기억한다.
  const phaseBeforePauseRef = useRef<Phase>('countdown')

  useEffect(() => {
    if (phase !== 'countdown') return

    if (countdown <= 0) {
      setPhase('playing')
      return
    }

    const timer = setTimeout(
      () => setCountdown((current) => current - 1),
      COUNTDOWN_STEP_MS,
    )
    return () => clearTimeout(timer)
  }, [countdown, phase])

  const pause = useCallback(() => {
    setPhase((current) => {
      if (current === 'paused') return current
      phaseBeforePauseRef.current = current
      return 'paused'
    })
  }, [])

  const resume = useCallback(() => setPhase(phaseBeforePauseRef.current), [])

  const restart = useCallback(() => {
    setCountdown(COUNTDOWN_FROM)
    setPhase('countdown')
  }, [])

  // 온 곳으로 돌려보낸다. 2페이지 게임인데 1페이지로 나가면 방금 고른 카드가
  // 사라져 아이가 길을 잃는다.
  const leaveGame = () =>
    navigate(game.page === 2 ? '/games/more' : '/games', { replace: true })

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <audio
          aria-label={`${game.label} 배경 음악`}
          loop
          preload="metadata"
          ref={audioRef}
          src={bgmUrl}
        />

        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          draggable={false}
          fetchPriority="high"
          src={backgroundUrl}
        />

        {/*
          놀이판이 들어올 자리.

          지금은 빵긋이가 서서 곧 만나자고만 한다. 아무것도 없이 비워 두면
          아이는 게임이 고장난 줄 알고, "준비 중"처럼 어른 말로 적으면 읽지
          못한다. 판정과 놀이판을 만들 때 이 블록을 통째로 바꾼다.
        */}
        {phase !== 'countdown' ? (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-4">
            <div className="rounded-[28px] border-[3px] border-[#e2caa0] bg-white/92 px-9 py-5">
              <p
                className="text-center text-[34px] leading-snug break-keep text-[#6f4a1f]"
                style={jua}
              >
                여기서 곧 같이 놀자!
              </p>
            </div>
            <DinoCharacter size={300} />
          </div>
        ) : null}

        <PlayHud
          onPause={pause}
          onQuit={leaveGame}
          phase={phase}
          remainingSec={PLACEHOLDER_REMAINING_SEC}
          score={PLACEHOLDER_SCORE}
          title={game.label}
        />

        {phase === 'countdown' ? (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-[rgba(30,20,8,0.4)]">
            <span
              className="text-[46px] leading-none text-white [text-shadow:0_3px_0_#8a5a1a,0_6px_12px_rgba(40,26,10,0.5)]"
              style={jua}
            >
              시작하겠습니다!
            </span>
            <span
              className="text-[180px] leading-none text-white [text-shadow:0_6px_0_#8a5a1a,0_12px_20px_rgba(40,26,10,0.5)]"
              style={jua}
            >
              {countdown}
            </span>
          </div>
        ) : null}

        {phase === 'paused' ? (
          <PauseOverlay
            onQuit={leaveGame}
            onRestart={restart}
            onResume={resume}
          />
        ) : null}

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
          onClick={() => {
            void toggleBgm()
          }}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            draggable={false}
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>
      </div>
    </div>
  )
}

/**
 * 플레이 중 상단 표시줄.
 *
 * 나가기·제목·점수·남은 시간·일시정지 자리와 값이 DobblePlayPage의 PlayHud와
 * 같다. 게임이 달라도 같은 자리에 있어야 아이가 화면마다 다시 찾지 않는다.
 */
function PlayHud({
  onPause,
  onQuit,
  phase,
  remainingSec,
  score,
  title,
}: {
  onPause: () => void
  onQuit: () => void
  phase: Phase
  remainingSec: number
  score: number
  title: string
}) {
  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <div className="absolute top-[25px] left-[25px]">
        <QuitButton onClick={onQuit} />
      </div>

      <h1
        className="absolute top-[40px] left-[90px] text-[30px] leading-none text-white [text-shadow:0_4px_0_#7a4d16,0_7px_10px_rgba(40,26,10,0.35)]"
        style={jua}
      >
        {title}
      </h1>

      <div className="absolute top-[35px] left-1/2 flex -translate-x-1/2 items-center gap-3 rounded-full bg-[rgba(40,26,10,0.5)] px-7 py-2">
        <span
          className="text-[38px] leading-none text-[#ffe27a] [text-shadow:0_3px_0_#8a5a1a,0_6px_10px_rgba(0,0,0,0.4)]"
          style={jua}
        >
          {score}
        </span>
        <span className="text-[22px] leading-none text-white/85" style={jua}>
          점
        </span>
      </div>

      <div className="absolute top-[35px] right-[112px] rounded-full bg-[rgba(40,26,10,0.5)] px-5 py-1.5">
        <span className="text-[28px] leading-none text-white" style={jua}>
          {formatTime(remainingSec)}
        </span>
      </div>

      <div className="absolute top-[25px] right-[30px] size-[62px]">
        <PauseButton disabled={phase === 'paused'} onClick={onPause} />
      </div>
    </div>
  )
}
