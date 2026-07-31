import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'

import {
  nowAsLocalDateTime,
  saveCompletedGameSession,
} from '@/api/gameSessions'
import bgmUrl from '@/assets/audio/recycle-game/river-recycle.mp3'
import backgroundUrl from '@/assets/images/recycle-game/background.webp'
import bbanggutGuideUrl from '@/assets/images/recycle-game/bbanggut-guide.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import {
  CameraErrorOverlay,
  CameraPermissionView,
  PauseButton,
  PauseOverlay,
  QuitButton,
} from '@/components/game/PlayOverlays'
import { getGameById } from '@/features/games/games'
import { BinRow } from '@/features/games/recycle/BinRow'
import { HandCursor } from '@/features/games/recycle/HandCursor'
import { RECYCLE_GAME_CONFIG as CFG } from '@/features/games/recycle/recycleGameConfig'
import { preloadRecycleImages } from '@/features/games/recycle/recycleItems'
import { hitRadius } from '@/features/games/recycle/recycleGrab'
import {
  playCorrectSfx,
  playGrabSfx,
  playReturnSfx,
  playWrongSfx,
} from '@/features/games/recycle/recycleSfx'
import {
  useRecycleGame,
  type RecycleGameResult,
  type RecycleGameState,
} from '@/features/games/recycle/useRecycleGame'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandGestures } from '@/hooks/useHandGestures'

const jua = { fontFamily: "'Jua', sans-serif" } as const

const PRAISES = [
  '정확해요!',
  '잘 넣었어요!',
  '숲이 깨끗해져요!',
  '멋져요!',
  '척척박사네요!',
]

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

/**
 * 분리수거 게임 플레이 화면 (RGAME).
 *
 * 물길을 가로지르는 쓰레기를 손으로 잡아 알맞은 통에 넣는다. 손 인식과 잡기는
 * 그림 맞추기와 같은 방식이라 아이가 두 게임에서 다시 배우지 않아도 된다.
 */
export function RecyclePlayPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { videoRef, status, requestCamera } = useCameraStream()
  const selectedAtRef = useRef(nowAsLocalDateTime())
  const playStartedAtRef = useRef<string | null>(null)
  const clientPlayIdRef = useRef(crypto.randomUUID())
  // 효과음이 묻히지 않도록 배경 음악은 낮게 깐다.
  const { audioRef, isBgmPlaying, playBgm, toggleBgm } = useBgmPlayer({
    volume: 0.22,
  })
  const { gesturesRef, modelReady } = useHandGestures(
    videoRef,
    status === 'granted',
  )

  const game = getGameById('recycle')

  useEffect(() => {
    void preloadRecycleImages()
  }, [])

  useEffect(() => {
    void requestCamera()
  }, [requestCamera])

  const [praise, setPraise] = useState<string | null>(null)
  const praiseTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(praiseTimerRef.current), [])

  const handleCorrect = useCallback(() => {
    playCorrectSfx()
    setPraise(PRAISES[Math.floor(Math.random() * PRAISES.length)])
    clearTimeout(praiseTimerRef.current)
    praiseTimerRef.current = setTimeout(
      () => setPraise(null),
      CFG.correctFeedbackMs,
    )
  }, [])

  const handleWrong = useCallback(() => {
    playWrongSfx()
    setPraise(null)
  }, [])

  const handleEnd = useCallback(
    (result: RecycleGameResult) => {
      // 정합성이 깨진 결과는 저장·표시하지 않고 게임 선택으로 되돌린다.
      if (!result.valid) {
        navigate('/games/more', { replace: true })
        return
      }

      const completedAt = nowAsLocalDateTime()
      const playStartedAt = playStartedAtRef.current ?? selectedAtRef.current

      void saveCompletedGameSession({
        clientPlayId: clientPlayIdRef.current,
        completedAt,
        gameId: 'recycle',
        playDurationSeconds: result.activePlaySec,
        playStartedAt,
        recyclingResult: {
          rulesVersion: result.rulesVersion,
          successfulCount: result.successCount,
          wrongCount: result.wrongCount,
        },
        score: result.score,
        selectedAt: selectedAtRef.current,
      })
        .then(() => {
          navigate('/games/recycle/result', {
            replace: true,
            state: { ...result, recordSaved: true },
          })
        })
        .catch((error: unknown) => {
          // 저장에 실패해도 결과 화면은 보여 준다(RGAME-18).
          navigate('/games/recycle/result', {
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
    },
    [navigate],
  )

  const recycle = useRecycleGame(
    gesturesRef,
    status === 'granted' || status === 'error',
    handleEnd,
    {
      onCorrect: handleCorrect,
      onGrab: playGrabSfx,
      onReturn: playReturnSfx,
      onWrong: handleWrong,
    },
    status === 'error',
  )

  useEffect(() => {
    if (recycle.phase === 'playing' && !playStartedAtRef.current) {
      playStartedAtRef.current = nowAsLocalDateTime()
    }
  }, [recycle.phase])

  /*
    화면에 들어오자마자 카메라 권한 창이 뜨면 브라우저가 자동 재생을 막는다.
    권한 창을 누르는 것은 페이지 입력이 아니라서 useBgmPlayer가 걸어 둔 "첫
    입력에 다시 시도" 장치도 깨어나지 않는다. 이 화면은 손으로만 조작해 끝까지
    누를 곳이 없으니 BGM이 영영 안 나온다.

    카운트다운까지 왔다면 카메라가 허용됐고 화면도 살아 있다는 뜻이라 여기서 한
    번 더 시도한다. playBgm은 아이가 꺼 둔 기억을 존중하므로 껐던 음악이 멋대로
    켜지지는 않는다.
  */
  useEffect(() => {
    if (recycle.phase === 'countdown' || recycle.phase === 'playing') {
      void playBgm()
    }
  }, [playBgm, recycle.phase])

  const restartGame = useCallback(() => {
    selectedAtRef.current = nowAsLocalDateTime()
    playStartedAtRef.current = null
    clientPlayIdRef.current = crypto.randomUUID()
    setPraise(null)
    recycle.restart()
  }, [recycle])

  if (!game) {
    return <Navigate replace to="/games" />
  }

  const leaveGame = () => navigate('/games/more')

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        {/* 3.6MB라 전체를 미리 받지 않고 재생하면서 스트리밍한다. */}
        <audio
          aria-label="분리수거 배경 음악"
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

        {status === 'granted' || status === 'error' ? (
          <>
            <PlayField game={recycle} handModelReady={modelReady} />

            {/* 자기 모습 미리보기 — 통과 겹치지 않는 오른쪽 아래 구석. */}
            {status === 'granted' ? (
              <video
                autoPlay
                className="pointer-events-none absolute right-[16px] bottom-[16px] z-[6] h-[105px] w-[140px] scale-x-[-1] rounded-2xl border-[3px] border-white/70 object-cover shadow-[0_6px_16px_rgba(0,0,0,0.35)]"
                muted
                playsInline
                ref={videoRef}
              />
            ) : null}

            <PlayHud
              onPause={recycle.pause}
              onQuit={leaveGame}
              phase={recycle.phase}
              praise={praise}
              remainingSec={recycle.remainingSec}
              score={recycle.score}
              successCount={recycle.successCount}
              title={game.label}
            />

            {recycle.phase === 'paused' ? (
              <PauseOverlay
                onQuit={leaveGame}
                onRestart={restartGame}
                onResume={recycle.resume}
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

function PlayField({
  game,
  handModelReady,
}: {
  game: RecycleGameState
  handModelReady: boolean
}) {
  const { phase, items, hover, binHover, guide, pointerRefs } = game
  const radius = hitRadius()

  /*
    두 손 중 한쪽이라도 통 위에 있으면 그 통을 강조한다. 양손이 서로 다른 통에
    있으면 쓰레기를 든 손을 먼저 본다 — 지금 놓을 곳이 그쪽이다.
  */
  const holdingSlot = (['a', 'b'] as const).find((slot) =>
    items.some((item) => item.heldBy === slot),
  )
  const activeSlot =
    holdingSlot && binHover[holdingSlot]
      ? holdingSlot
      : binHover.a
        ? 'a'
        : binHover.b
          ? 'b'
          : null

  return (
    <div className="absolute inset-0 z-10">
      <BinRow
        holding={activeSlot !== null && activeSlot === holdingSlot}
        hoveredBin={activeSlot ? binHover[activeSlot] : null}
      />

      {items.map((item) => {
        const hovered = hover.a === item.uid || hover.b === item.uid
        const held = item.heldBy !== null

        return (
          <div
            className="pointer-events-none absolute z-[8]"
            key={item.uid}
            style={{
              left: item.x,
              top: item.y,
              transform: 'translate(-50%, -50%)',
            }}
          >
            {/* 조준·잡기 표시. 무엇을 짚고 있는지 손 색과 무관하게 알려 준다. */}
            {hovered || held ? (
              <span
                className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                style={{
                  width: radius * 2,
                  height: radius * 2,
                  border: held
                    ? '6px solid rgba(255,206,42,0.95)'
                    : '5px dashed rgba(255,255,255,0.8)',
                  background: held
                    ? 'rgba(255,206,42,0.2)'
                    : 'rgba(255,255,255,0.1)',
                  boxShadow: held ? '0 0 22px rgba(255,206,42,0.7)' : undefined,
                }}
              />
            ) : null}
            <img
              alt={item.name}
              className="relative block object-contain will-change-transform"
              decoding="sync"
              draggable={false}
              loading="eager"
              src={item.imageUrl}
              style={{
                width: CFG.itemSize,
                height: CFG.itemSize,
                transform: held ? 'scale(1.1)' : 'scale(1)',
                transition: 'transform 160ms ease-out',
                filter: held
                  ? 'drop-shadow(0 0 14px rgba(255,224,120,0.9))'
                  : 'drop-shadow(0 6px 8px rgba(20,50,40,0.35))',
              }}
            />
          </div>
        )
      })}

      {/* 손 포인터 — 좌표/각도/주먹 상태는 엔진이 이 노드에 직접 쓴다. */}
      {(['a', 'b'] as const).map((slot) => (
        <HandCursor key={slot} nodeRef={pointerRefs[slot]} slot={slot} />
      ))}

      {phase === 'countdown' ? (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-[rgba(10,40,50,0.4)]">
          <span
            className="text-[46px] leading-none text-white [text-shadow:0_3px_0_#1c6b7a,0_6px_12px_rgba(10,40,50,0.5)]"
            style={jua}
          >
            시작하겠습니다!
          </span>
          <span
            className="text-[180px] leading-none text-white [text-shadow:0_6px_0_#1c6b7a,0_12px_20px_rgba(10,40,50,0.5)]"
            style={jua}
          >
            {game.countdown}
          </span>
        </div>
      ) : null}

      {phase === 'waiting-hands' || phase === 'interrupted' ? (
        <div className="absolute inset-0 z-20 grid place-items-center bg-[rgba(10,40,50,0.45)]">
          <p
            className="rounded-[28px] bg-[rgba(10,40,50,0.7)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            {!handModelReady
              ? '동작 인식 준비 중...'
              : phase === 'interrupted'
                ? '손이 안 보여요! 손바닥을 펴서 보여 주세요'
                : '손바닥을 펴서 카메라에 보여 주세요'}
          </p>
        </div>
      ) : null}

      {/* 오답 안내 — 빵긋이가 올바른 통을 알려 준다(RGAME-09). */}
      {guide ? <WrongGuideOverlay guide={guide} /> : null}
    </div>
  )
}

/**
 * 오답 안내 화면.
 *
 * 틀렸다고 말하지 않고 어디에 넣으면 되는지만 알려 준다. 쓰레기 그림을 같이
 * 띄워야 아이가 방금 자기가 든 것과 연결한다.
 */
function WrongGuideOverlay({
  guide,
}: {
  guide: NonNullable<RecycleGameState['guide']>
}) {
  return (
    <div
      aria-live="polite"
      className="absolute inset-0 z-30 flex items-end justify-center bg-[rgba(10,40,50,0.5)]"
    >
      <div className="mb-[40px] flex items-end gap-3">
        <img
          alt=""
          className="block h-[300px] w-auto object-contain drop-shadow-[0_14px_24px_rgba(20,50,16,0.4)]"
          decoding="async"
          draggable={false}
          src={bbanggutGuideUrl}
        />
        <div className="mb-[70px] flex items-center gap-5 rounded-[30px] border-[4px] border-[#e2caa0] bg-white/95 px-8 py-6 shadow-[0_12px_28px_rgba(20,50,16,0.35)]">
          <img
            alt=""
            className="block size-[96px] shrink-0 object-contain"
            decoding="async"
            draggable={false}
            src={guide.itemImageUrl}
          />
          <p
            className="text-center text-[34px] leading-snug break-keep text-[#6f4a1f]"
            style={jua}
          >
            다시 알아보자!
            <br />
            {guide.message}
          </p>
        </div>
      </div>
    </div>
  )
}

function PlayHud({
  onPause,
  onQuit,
  phase,
  praise,
  remainingSec,
  score,
  successCount,
  title,
}: {
  onPause: () => void
  onQuit: () => void
  phase: RecycleGameState['phase']
  praise: string | null
  remainingSec: number
  score: number
  successCount: number
  title: string
}) {
  const canPause =
    phase === 'playing' || phase === 'countdown' || phase === 'interrupted'
  const timeWarning = remainingSec <= CFG.timeWarningSec

  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <div className="absolute top-[25px] left-[25px]">
        <QuitButton onClick={onQuit} />
      </div>

      <h1
        className="absolute top-[40px] left-[100px] text-[30px] leading-none text-white [text-shadow:0_4px_0_#1c6b7a,0_7px_10px_rgba(10,40,50,0.4)]"
        style={jua}
      >
        {title}
      </h1>

      <div className="absolute top-[35px] left-1/2 flex -translate-x-1/2 items-center gap-3 rounded-full bg-[rgba(10,40,50,0.55)] px-7 py-2">
        <span
          className="text-[38px] leading-none text-[#ffe27a] [text-shadow:0_3px_0_#1c6b7a,0_6px_10px_rgba(0,0,0,0.4)]"
          style={jua}
        >
          {score}
        </span>
        <span className="text-[22px] leading-none text-white/85" style={jua}>
          쓰레기 {successCount}개
        </span>
      </div>

      <div
        className={`absolute top-[35px] right-[112px] rounded-full px-5 py-1.5 ${
          timeWarning ? 'bg-[rgba(200,60,30,0.72)]' : 'bg-[rgba(10,40,50,0.55)]'
        }`}
      >
        <span className="text-[28px] leading-none text-white" style={jua}>
          {formatTime(remainingSec)}
        </span>
      </div>

      <div className="absolute top-[25px] right-[30px] size-[62px]">
        <PauseButton disabled={!canPause} onClick={onPause} />
      </div>

      {praise && phase === 'playing' ? (
        <p
          className="absolute top-[110px] left-1/2 -translate-x-1/2 text-[52px] leading-none text-[#fff2a8] [text-shadow:0_4px_0_#1c6b7a,0_8px_14px_rgba(0,0,0,0.35)]"
          style={jua}
        >
          {praise}
        </p>
      ) : null}

      {phase === 'playing' ? (
        <p
          className="absolute bottom-[20px] left-1/2 -translate-x-1/2 rounded-full bg-[rgba(10,40,50,0.45)] px-6 py-2 text-[22px] leading-none text-white [text-shadow:0_2px_6px_rgba(10,40,50,0.5)]"
          style={jua}
        >
          쓰레기를 잡아서 알맞은 통에 넣어요
        </p>
      ) : null}
    </div>
  )
}
