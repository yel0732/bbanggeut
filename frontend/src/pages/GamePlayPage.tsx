import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'

import cutSoundUrl from '@/assets/audio/fruit-game/cut-fruit.mp3'
import fruitBgmUrl from '@/assets/audio/fruit-game/fruit-forest-rhythm.mp3'
import backgroundUrl from '@/assets/images/fruit-game/background.webp'
import playFrameUrl from '@/assets/images/fruit-game/play-frame.webp'
import {
  CameraErrorOverlay,
  CameraPermissionView,
  PauseButton,
  PauseOverlay,
  QuitButton,
} from '@/components/game/PlayOverlays'
import {
  getActiveFruitNinjaRule,
  nowAsLocalDateTime,
  saveCompletedGameSession,
} from '@/api/gameSessions'
import type { FruitNinjaRule } from '@/api/gameSessions'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import { FRUIT_GAME_CONFIG } from '@/features/games/fruit/fruitGameConfig'
import {
  useFruitGame,
  type FruitGameResult,
  type FruitGameState,
} from '@/features/games/fruit/useFruitGame'
import { getGameById } from '@/features/games/games'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandTracking } from '@/hooks/useHandTracking'

const jua = { fontFamily: "'Jua', sans-serif" } as const

const PRAISES = ['멋져요!', '최고예요!', '슝!', '좋아요!', '와아!', '나이스!']
/** 콤보가 이어질수록 더 크게 칭찬한다. */
const COMBO_PRAISES = ['좋아요!', '연속으로!', '대단해요!', '엄청나요!']

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

export function GamePlayPage() {
  const navigate = useNavigate()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef } = useBgmPlayer({ volume: 0.35 })
  const { videoRef, status, requestCamera } = useCameraStream()
  const hand = useHandTracking(videoRef, status === 'granted')
  const selectedAtRef = useRef(nowAsLocalDateTime())
  const playStartedAtRef = useRef<string | null>(null)
  const clientPlayIdRef = useRef(crypto.randomUUID())
  const [fruitRule, setFruitRule] = useState<FruitNinjaRule | null>(null)
  const [ruleError, setRuleError] = useState('')

  const game = getGameById(gameId)

  const loadFruitRule = useCallback(() => {
    setRuleError('')
    void getActiveFruitNinjaRule()
      .then(setFruitRule)
      .catch((error: unknown) => {
        setRuleError(
          error instanceof Error
            ? error.message
            : '과일 게임 규칙을 불러오지 못했어요.',
        )
      })
  }, [])

  useEffect(loadFruitRule, [loadFruitRule])

  const handleEnd = (result: FruitGameResult) => {
    // MGAME-21/22: 정합성 통과한 정상 결과만 결과 화면으로. 잘못되면 폐기.
    if (!result.valid) {
      navigate('/games', { replace: true })
      return
    }
    const completedAt = nowAsLocalDateTime()
    const playStartedAt = playStartedAtRef.current ?? selectedAtRef.current
    void saveCompletedGameSession({
      clientPlayId: clientPlayIdRef.current,
      completedAt,
      gameId: 'fruit',
      fruitNinjaResult: {
        fruitResults: result.fruitResults,
        rulesVersion: result.ruleVersion,
        slicedFruitCount: result.slicedFruitCount,
        totalFruitCount: result.totalFruitCount,
      },
      playDurationSeconds: result.activePlaySec,
      playStartedAt,
      score: result.score,
      selectedAt: selectedAtRef.current,
    })
      .then(() => {
        navigate(`/games/${gameId}/result`, {
          replace: true,
          state: { ...result, recordSaved: true },
        })
      })
      .catch((error: unknown) => {
        navigate(`/games/${gameId}/result`, {
          replace: true,
          state: {
            ...result,
            recordError:
              error instanceof Error
                ? error.message
                : '게임 기록을 저장하지 못했어요.',
            recordSaved: false,
          },
        })
      })
  }

  // 자르기 효과음 풀 (연속 베기 시 겹쳐 재생)
  const cutPoolRef = useRef<HTMLAudioElement[]>([])
  const cutIndexRef = useRef(0)
  useEffect(() => {
    const pool = Array.from({ length: 4 }, () => {
      const audio = new Audio(cutSoundUrl)
      audio.volume = 0.6
      return audio
    })
    cutPoolRef.current = pool
    return () => {
      pool.forEach((audio) => audio.pause())
      cutPoolRef.current = []
    }
  }, [])

  // 긍정 성공 문구 (MGAME-19)
  const [praise, setPraise] = useState<string | null>(null)
  const praiseTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(praiseTimerRef.current), [])

  const handleSlice = useCallback((combo: number) => {
    const pool = cutPoolRef.current
    if (pool.length > 0) {
      const audio = pool[cutIndexRef.current % pool.length]
      cutIndexRef.current += 1
      audio.currentTime = 0
      // 콤보가 이어질수록 음을 조금씩 높여 신나게 들리도록 한다.
      audio.playbackRate = Math.min(1.35, 1 + (combo - 1) * 0.06)
      void audio.play().catch(() => {})
    }
    // 콤보 중에는 콤보 전용 문구를 써서 이어 베고 있다는 게 드러나게 한다.
    const words = combo >= 2 ? COMBO_PRAISES : PRAISES
    const index =
      combo >= 2
        ? Math.min(COMBO_PRAISES.length - 1, combo - 2)
        : Math.floor(Math.random() * PRAISES.length)
    setPraise(words[index])
    clearTimeout(praiseTimerRef.current)
    praiseTimerRef.current = setTimeout(() => setPraise(null), 750)
  }, [])

  const fruitGame = useFruitGame(
    hand.playHandRef,
    (status === 'granted' || status === 'error') && fruitRule !== null,
    handleEnd,
    handleSlice,
    hand.redesignate,
    status === 'error',
    fruitRule ?? undefined,
  )

  useEffect(() => {
    if (fruitGame.phase === 'playing' && !playStartedAtRef.current) {
      playStartedAtRef.current = nowAsLocalDateTime()
    }
  }, [fruitGame.phase])

  const restartGame = useCallback(() => {
    selectedAtRef.current = nowAsLocalDateTime()
    playStartedAtRef.current = null
    clientPlayIdRef.current = crypto.randomUUID()
    fruitGame.restart()
  }, [fruitGame])

  // 피버 진입을 소리와 BGM 변화로 확실하게 알린다. 게임 규칙과 8초 타이머는 건드리지 않는다.
  useEffect(() => {
    const bgm = audioRef.current
    if (bgm) {
      bgm.playbackRate = fruitGame.fever ? 1.08 : 1
      bgm.volume = fruitGame.fever ? 0.48 : 0.35
    }
    if (!fruitGame.fever) return

    const context = new AudioContext()
    const master = context.createGain()
    master.gain.setValueAtTime(0.0001, context.currentTime)
    master.gain.exponentialRampToValueAtTime(0.18, context.currentTime + 0.02)
    master.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.72)
    master.connect(context.destination)

    ;[523.25, 659.25, 783.99, 1046.5].forEach((frequency, index) => {
      const oscillator = context.createOscillator()
      oscillator.type = index === 3 ? 'triangle' : 'sine'
      oscillator.frequency.value = frequency
      oscillator.connect(master)
      oscillator.start(context.currentTime + index * 0.1)
      oscillator.stop(context.currentTime + 0.3 + index * 0.1)
    })

    const closeTimer = window.setTimeout(() => void context.close(), 900)
    return () => window.clearTimeout(closeTimer)
  }, [audioRef, fruitGame.fever])

  const lastCountdownRef = useRef<number | null>(null)
  useEffect(() => {
    if (fruitGame.phase !== 'countdown') {
      lastCountdownRef.current = null
      return
    }
    if (lastCountdownRef.current === fruitGame.countdown) return
    lastCountdownRef.current = fruitGame.countdown

    const AudioContextClass = window.AudioContext
    const context = new AudioContextClass()
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = fruitGame.countdown === 1 ? 880 : 660
    gain.gain.setValueAtTime(0.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.22, context.currentTime + 0.01)
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.16)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + 0.17)
    oscillator.addEventListener('ended', () => void context.close())
  }, [fruitGame.countdown, fruitGame.phase])

  // MGAME-02: 게임 화면 진입 시 카메라를 시작한다. (종료는 훅 언마운트에서 처리)
  useEffect(() => {
    if (game?.playable) {
      void requestCamera()
    }
  }, [game?.playable, requestCamera])

  if (!game) {
    return <Navigate replace to="/games" />
  }
  if (!game.playable) {
    return <Navigate replace to={`/games/${game.id}/guide`} />
  }

  const leaveGame = () => navigate('/games')

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden text-white"
      >
        <audio autoPlay loop preload="auto" ref={audioRef} src={fruitBgmUrl} />
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />

        {/* 자기 모습 미리보기 (구석, 위치 확인용) — 동작 인식도 이 영상에서 수행 */}
        {status === 'granted' ? (
          <video
            autoPlay
            className="pointer-events-none absolute right-[30px] bottom-[100px] z-[6] h-[150px] w-[200px] scale-x-[-1] rounded-2xl border-[3px] border-white/70 object-cover shadow-[0_6px_16px_rgba(0,0,0,0.35)]"
            muted
            playsInline
            ref={videoRef}
          />
        ) : null}

        {/* 정글 프레임 (가운데 투명, 테두리 장식) */}
        <img
          alt=""
          className="pointer-events-none absolute inset-0 z-[5] h-full w-full object-cover"
          decoding="async"
          draggable={false}
          src={playFrameUrl}
        />

        {status === 'granted' || status === 'error' ? (
          <>
            <PlayField
              game={fruitGame}
              handModelReady={hand.modelReady}
              praise={praise}
            />

            <PlayHud
              onPause={fruitGame.pause}
              onQuit={leaveGame}
              phase={fruitGame.phase}
              remainingSec={fruitGame.remainingSec}
              score={fruitGame.score}
              title={game.label}
            />

            {fruitGame.phase === 'paused' ? (
              <PauseOverlay
                onQuit={leaveGame}
                onRestart={restartGame}
                onResume={fruitGame.resume}
              />
            ) : null}

            {status === 'error' ? (
              <CameraErrorOverlay
                onQuit={leaveGame}
                onRetry={() => {
                  void requestCamera()
                }}
              />
            ) : null}
          </>
        ) : (
          <CameraPermissionView
            onQuit={leaveGame}
            onRetry={() => {
              void requestCamera()
            }}
            status={status}
            title={game.label}
          />
        )}

        {ruleError ? (
          <ParentNoticeDialog
            compact
            confirmLabel="다시 시도"
            message={ruleError}
            onConfirm={loadFruitRule}
            title="게임 준비에 문제가 생겼어요"
          />
        ) : null}
      </div>
    </div>
  )
}

function PlayField({
  game,
  handModelReady,
  praise,
}: {
  game: FruitGameState
  handModelReady: boolean
  praise: string | null
}) {
  const { phase, distance, countdown, combo, fever } = game

  // 카메라 거리 안내 (MGAME 친화적 안내)
  // 거리는 어깨너비로 잰다. 손이 아니라 몸을 움직이라고 안내해야 맞다.
  let distanceHint: string | null = null
  if (distance === 'near') distanceHint = '한 걸음만 뒤로 물러나 볼까요?'
  else if (distance === 'far') distanceHint = '한 걸음만 앞으로 와 볼까요?'

  return (
    <div className="absolute inset-0 z-10">
      {/* 피버 타임: 화면 가장자리를 달궈 과일이 쏟아지는 구간임을 알린다. */}
      {fever ? (
        <>
          <div
            className="pointer-events-none absolute inset-0"
            style={{
              background:
                'radial-gradient(100% 88% at 50% 50%, rgba(255,240,120,0.08) 35%, rgba(255,146,20,0.58) 100%)',
              boxShadow:
                'inset 0 0 50px 14px rgba(255,224,75,.78), inset 0 0 120px 35px rgba(255,82,10,.45)',
              animation: 'fruit-fever-glow 520ms ease-in-out infinite',
            }}
          />
          <div
            className="pointer-events-none absolute -inset-[30%] opacity-30"
            style={{
              background:
                'repeating-conic-gradient(from 0deg at 50% 50%, rgba(255,241,130,.7) 0deg 7deg, transparent 7deg 20deg)',
              animation: 'fruit-fever-rays 5s linear infinite',
            }}
          />
          <div
            className="pointer-events-none absolute inset-[12px] rounded-[34px] border-[7px] border-[#ffe86c]"
            style={{ animation: 'fruit-fever-border 520ms ease-in-out infinite' }}
          />
        </>
      ) : null}

      {/* 과일·칼·트레일 — 매 프레임 바뀌므로 캔버스에 직접 그린다. */}
      <canvas
        className="pointer-events-none absolute inset-0 h-full w-full"
        height={FRUIT_GAME_CONFIG.canvasHeight}
        ref={game.canvasRef}
        width={FRUIT_GAME_CONFIG.canvasWidth}
      />

      {fever ? (
        <div className="absolute top-[132px] left-1/2 -translate-x-1/2 text-center">
          <span
            className="inline-block text-[56px] leading-none text-[#fff0a0] [text-shadow:0_4px_0_#c9450f,0_10px_18px_rgba(0,0,0,0.45)]"
            style={{
              ...jua,
              animation: 'fruit-fever-bob 620ms ease-in-out infinite',
            }}
          >
            피버 타임!
          </span>
          <p
            className="mt-2 text-[25px] leading-none text-white [text-shadow:0_3px_0_#b84a13,0_6px_10px_rgba(0,0,0,.45)]"
            style={jua}
          >
            8초 동안 과일이 쏟아져요!
          </p>
        </div>
      ) : null}

      {/* 콤보 — 2연속부터 표시 */}
      {combo >= 2 && phase === 'playing' ? (
        <div
          className="absolute top-[86px] left-1/2 -translate-x-1/2 text-center"
          // 콤보가 갱신될 때마다 다시 마운트해 튀어오르는 연출을 반복한다.
          key={combo}
          style={{ animation: 'fruit-combo-pop 260ms ease-out' }}
        >
          <span
            className="text-[52px] leading-none text-[#ffd75e] [text-shadow:0_4px_0_#a8541a,0_8px_14px_rgba(0,0,0,0.4)]"
            style={jua}
          >
            {combo} 콤보
          </span>
        </div>
      ) : null}

      {/* 거리 안내 (손이 보이고 적당 거리가 아닐 때) */}
      {distanceHint && phase !== 'ended' ? (
        <div className="absolute top-[96px] left-1/2 -translate-x-1/2">
          <p
            className="rounded-full bg-[rgba(255,153,0,0.85)] px-6 py-2 text-[24px] leading-none text-white [text-shadow:0_2px_4px_rgba(0,0,0,0.35)]"
            style={jua}
          >
            {distanceHint}
          </p>
        </div>
      ) : null}

      {/* 긍정 성공 문구 (MGAME-19) */}
      {praise && phase === 'playing' ? (
        <div className="absolute top-[34%] left-1/2 -translate-x-1/2">
          <span
            className="text-[64px] leading-none text-[#fff2a8] [text-shadow:0_4px_0_#e08a2e,0_8px_14px_rgba(0,0,0,0.35)]"
            style={jua}
          >
            {praise}
          </span>
        </div>
      ) : null}

      {/* 카운트다운 */}
      {phase === 'countdown' ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2">
          <span
            className="text-[46px] leading-none text-white [text-shadow:0_3px_0_#4f8a30,0_6px_12px_rgba(19,50,18,0.5)]"
            style={jua}
          >
            시작하겠습니다!
          </span>
          <span
            className="text-[180px] leading-none text-white [text-shadow:0_6px_0_#4f8a30,0_12px_20px_rgba(19,50,18,0.5)]"
            style={jua}
          >
            {countdown}
          </span>
        </div>
      ) : null}

      {/* 손 대기 안내 */}
      {phase === 'waiting-hand' ? (
        <div className="absolute inset-0 grid place-items-center">
          <p
            className="rounded-[28px] bg-[rgba(14,30,15,0.55)] px-8 py-5 text-center text-[32px] leading-snug whitespace-pre-line text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            {handModelReady
              ? '일어서서 상체가 다 보이게 물러난 뒤\n한 손을 번쩍 들어 주세요'
              : '동작 인식 준비 중...'}
          </p>
        </div>
      ) : null}

      {/* 자동 일시정지 (손 사라짐) 안내 (MGAME-24) */}
      {phase === 'interrupted' ? (
        <div className="absolute inset-0 grid place-items-center bg-[rgba(14,30,15,0.35)]">
          <p
            className="rounded-[28px] bg-[rgba(14,30,15,0.6)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            손이 안 보여요!
            <br />한 손을 다시 들어 주세요
          </p>
        </div>
      ) : null}
    </div>
  )
}

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
  phase: FruitGameState['phase']
  remainingSec: number
  score: number
  title: string
}) {
  const canPause =
    phase === 'playing' || phase === 'countdown' || phase === 'interrupted'

  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <div className="absolute top-[25px] left-[25px]">
        <QuitButton onClick={onQuit} />
      </div>

      <h1
        className="absolute top-[40px] left-[90px] text-[30px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_7px_10px_rgba(19,50,18,0.28)]"
        style={jua}
      >
        {title}
      </h1>

      {/* 진행 중 현재 점수만 표시 (MGAME-18) */}
      <div className="absolute top-[30px] left-1/2 -translate-x-1/2 rounded-full bg-[rgba(14,30,15,0.5)] px-8 py-2 text-center">
        <span
          className="text-[40px] leading-none text-[#ffe27a] [text-shadow:0_3px_0_#8a5a1a,0_6px_10px_rgba(0,0,0,0.4)]"
          style={jua}
        >
          {score}
        </span>
      </div>

      <div className="absolute top-[35px] right-[100px] rounded-full bg-[rgba(14,30,15,0.5)] px-5 py-1.5">
        <span className="text-[28px] leading-none text-white" style={jua}>
          {formatTime(remainingSec)}
        </span>
      </div>

      <div className="absolute top-[25px] right-[30px] size-[62px]">
        <PauseButton disabled={!canPause} onClick={onPause} />
      </div>
    </div>
  )
}
