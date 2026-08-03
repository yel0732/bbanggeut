import {
  memo,
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type RefObject,
} from 'react'
import { Navigate, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/dobble-game/dobble-bgm.mp3'
import boardBgUrl from '@/assets/images/dobble-game/board-bg.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import {
  CameraErrorOverlay,
  CameraPermissionView,
  PauseButton,
  PauseOverlay,
  QuitButton,
} from '@/components/game/PlayOverlays'
import {
  cardArea,
  type CardSide,
  type PlacedSymbol,
} from '@/features/games/dobble/dobbleCards'
import { DOBBLE_GAME_CONFIG as CFG } from '@/features/games/dobble/dobbleGameConfig'
import {
  playGrabSfx,
  playMatchSfx,
  playRetrySfx,
} from '@/features/games/dobble/dobbleSfx'
import { useCountdownBeep } from '@/features/games/useCountdownBeep'
import {
  getDobbleSymbol,
  preloadDobbleSymbols,
} from '@/features/games/dobble/dobbleSymbols'
import {
  useDobbleGame,
  type DobbleGameResult,
  type DobbleGameState,
  type JudgeKind,
} from '@/features/games/dobble/useDobbleGame'
import { getGameById } from '@/features/games/games'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import {
  nowAsLocalDateTime,
  saveCompletedGameSession,
} from '@/api/gameSessions'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandGestures, type HandSlot } from '@/hooks/useHandGestures'

const jua = { fontFamily: "'Jua', sans-serif" } as const

/** 두 손을 구분하는 색. 화면에 손 이름은 쓰지 않고 색으로만 구분한다. */
const HAND_COLOR: Record<HandSlot, string> = {
  a: '#37cfb4',
  b: '#ffab26',
}

/**
 * 힌트 연출 색·크기.
 *
 * 배경이 따뜻한 나무색이라 금색 하나로는 대비가 부족하다. 밝은 금색 코어와
 * 진한 주황 테두리를 겹쳐 명도·채도 양쪽으로 배경과 갈라 놓는다. 눈에 덜
 * 띈다면 이 세 값만 조절하면 된다.
 */
const HINT_CORE = 'rgba(255,206,42,0.95)'
const HINT_EDGE = 'rgba(255,124,10,0.72)'
const HINT_GLOW_SCALE = 3.3

const PRAISES = [
  '똑같은 그림이에요!',
  '눈이 반짝반짝!',
  '정말 잘 찾았어요!',
  '멋져요!',
]

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

export function DobblePlayPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { videoRef, status, requestCamera } = useCameraStream()
  const selectedAtRef = useRef(nowAsLocalDateTime())
  const playStartedAtRef = useRef<string | null>(null)
  const clientPlayIdRef = useRef(crypto.randomUUID())
  // 효과음이 묻히지 않도록 배경 음악은 낮게 깐다.
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer({ volume: 0.22 })
  const { gesturesRef, modelReady } = useHandGestures(
    videoRef,
    status === 'granted',
  )

  const game = getGameById('dobble')

  // 라운드가 바뀔 때 그림이 늦게 뜨지 않도록 20장을 미리 디코딩해 둔다.
  useEffect(() => {
    void preloadDobbleSymbols()
  }, [])

  useEffect(() => {
    void requestCamera()
  }, [requestCamera])

  const handleEnd = useCallback(
    (result: DobbleGameResult) => {
      // 정합성이 깨진 결과는 저장/표시하지 않고 게임 선택으로 되돌린다.
      if (!result.valid) {
        navigate('/games', { replace: true })
        return
      }
      const completedAt = nowAsLocalDateTime()
      const playStartedAt = playStartedAtRef.current ?? selectedAtRef.current
      void saveCompletedGameSession({
        cardResults: result.cardResults,
        clientPlayId: clientPlayIdRef.current,
        completedAt,
        gameId: 'dobble',
        playDurationSeconds: result.activePlaySec,
        playStartedAt,
        score: result.score,
        selectedAt: selectedAtRef.current,
      })
        .then(() => {
          navigate('/games/dobble/result', {
            replace: true,
            state: { ...result, recordSaved: true },
          })
        })
        .catch((error: unknown) => {
          navigate('/games/dobble/result', {
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

  const [praise, setPraise] = useState<string | null>(null)
  const praiseTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(praiseTimerRef.current), [])

  const handleGrab = useCallback(() => {
    playGrabSfx()
  }, [])

  const handleJudge = useCallback((kind: JudgeKind) => {
    if (kind !== 'correct') {
      playRetrySfx()
      setPraise(null)
      return
    }
    playMatchSfx()
    setPraise(PRAISES[Math.floor(Math.random() * PRAISES.length)])
    clearTimeout(praiseTimerRef.current)
    praiseTimerRef.current = setTimeout(
      () => setPraise(null),
      CFG.correctFeedbackMs,
    )
  }, [])

  const dobble = useDobbleGame(
    gesturesRef,
    status === 'granted' || status === 'error',
    handleEnd,
    handleJudge,
    handleGrab,
    status === 'error',
  )

  useCountdownBeep(dobble.phase === 'countdown', dobble.countdown)

  useEffect(() => {
    if (dobble.phase === 'playing' && !playStartedAtRef.current) {
      playStartedAtRef.current = nowAsLocalDateTime()
    }
  }, [dobble.phase])

  const restartGame = useCallback(() => {
    selectedAtRef.current = nowAsLocalDateTime()
    playStartedAtRef.current = null
    clientPlayIdRef.current = crypto.randomUUID()
    dobble.restart()
  }, [dobble])

  if (!game) {
    return <Navigate replace to="/games" />
  }

  const leaveGame = () => navigate('/games')

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        {/* 3.4MB라 전체를 미리 받지 않고 재생하면서 스트리밍한다. */}
        <audio
          aria-label="그림 맞추기 배경 음악"
          loop
          preload="metadata"
          ref={audioRef}
          src={bgmUrl}
        />

        <img
          alt=""
          className="absolute top-[-30px] left-0 h-[750px] w-full object-cover"
          decoding="async"
          draggable={false}
          fetchPriority="high"
          src={boardBgUrl}
        />

        {/* 자기 모습 미리보기 — 그림이 놓이는 자리와 겹치지 않는 오른쪽 아래. */}
        {status === 'granted' ? (
          <video
            autoPlay
            className="pointer-events-none absolute right-[30px] bottom-[30px] z-[6] h-[120px] w-[160px] scale-x-[-1] rounded-2xl border-[3px] border-white/70 object-cover shadow-[0_6px_16px_rgba(0,0,0,0.35)]"
            muted
            playsInline
            ref={videoRef}
          />
        ) : null}

        {status === 'granted' || status === 'error' ? (
          <>
            <PlayField
              game={dobble}
              handModelReady={modelReady}
              praise={praise}
            />

            <PlayHud
              matchedCount={dobble.matchedCount}
              onPause={dobble.pause}
              onQuit={leaveGame}
              phase={dobble.phase}
              remainingSec={dobble.remainingSec}
              score={dobble.score}
              title={game.label}
            />

            {dobble.phase === 'paused' ? (
              <PauseOverlay
                onQuit={leaveGame}
                onRestart={restartGame}
                onResume={dobble.resume}
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
  praise,
}: {
  game: DobbleGameState
  handModelReady: boolean
  praise: string | null
}) {
  const { phase, round, selection, hover, feedback, hintSide, pointerRefs } =
    game

  const matchedSymbol =
    feedback?.kind === 'correct'
      ? getDobbleSymbol(feedback.symbolId)
      : undefined

  /** 이 쪽에 올라온 손이 조준 중인 그림 id (없으면 null) */
  const hoverOn = (side: CardSide, slot: HandSlot): string | null => {
    const current = hover[slot]
    return current && current.side === side ? current.symbolId : null
  }

  return (
    <div className="absolute inset-0 z-10 -translate-y-[30px]">
      {(['left', 'right'] as const).map((side) => (
        <BoardSide
          feedbackKind={feedback?.kind ?? null}
          grabbedBy={selection[side]?.by ?? null}
          grabbedSymbolId={selection[side]?.symbolId ?? null}
          // 힌트가 켜진 면에만 정답 동물 id를 넘긴다. 반대쪽은 알려주지 않는다.
          hintSymbolId={hintSide === side ? round.commonSymbolId : null}
          hoverAId={hoverOn(side, 'a')}
          hoverBId={hoverOn(side, 'b')}
          matchedSymbolId={matchedSymbol?.id ?? null}
          // 라운드가 바뀌면 새로 마운트해 등장 애니메이션을 다시 재생한다.
          key={`${side}-${round.index}`}
          side={side}
          symbols={round[side]}
        />
      ))}

      {/* 손 포인터 — 좌표/각도/주먹 상태는 엔진이 이 노드에 직접 쓴다. */}
      {(['a', 'b'] as const).map((slot) => (
        <HandCursor key={slot} nodeRef={pointerRefs[slot]} slot={slot} />
      ))}

      {/* 정답 연출: 맞춘 그림을 가운데에 크게 */}
      {matchedSymbol ? (
        <div className="pointer-events-none absolute top-[330px] left-1/2 z-[25] [animation:dobble-match-burst_1.1s_ease-out_forwards]">
          <img
            alt={matchedSymbol.name}
            className="block h-[176px] w-auto drop-shadow-[0_0_28px_rgba(255,236,150,0.95)]"
            decoding="sync"
            draggable={false}
            src={matchedSymbol.imageUrl}
          />
        </div>
      ) : null}

      {praise && phase === 'playing' ? (
        <p
          className="absolute bottom-[74px] left-1/2 z-[25] -translate-x-1/2 text-[52px] leading-none text-[#fff2a8] [text-shadow:0_4px_0_#e08a2e,0_8px_14px_rgba(0,0,0,0.35)]"
          style={jua}
        >
          {praise}
        </p>
      ) : null}

      {feedback?.kind === 'wrong' ? (
        <p
          className="absolute bottom-[78px] left-1/2 z-[25] -translate-x-1/2 rounded-full bg-[rgba(255,153,0,0.92)] px-8 py-3 text-[30px] leading-none text-white [text-shadow:0_2px_4px_rgba(0,0,0,0.35)]"
          style={jua}
        >
          괜찮아요! 같은 그림을 다시 찾아볼까요?
        </p>
      ) : null}

      {/* 힌트 안내 — 한쪽 면 정답을 반짝이게 하고 무엇을 하라고 알려 준다. */}
      {hintSide && phase === 'playing' && !feedback ? (
        <p
          className="absolute bottom-[24px] left-1/2 -translate-x-1/2 rounded-full bg-[rgba(230,168,56,0.92)] px-7 py-2 text-[24px] leading-none text-white [text-shadow:0_2px_4px_rgba(0,0,0,0.3)]"
          style={jua}
        >
          반짝이는 동물이랑 똑같은 그림을 찾아볼까요?
        </p>
      ) : null}

      {/* 플레이 중 조작 안내 */}
      {phase === 'playing' && !feedback && !hintSide ? (
        <p
          className="absolute bottom-[50px] left-1/2 -translate-x-1/2 rounded-full bg-[rgba(40,26,10,0.4)] px-6 py-2 text-[22px] leading-none text-white [text-shadow:0_2px_6px_rgba(20,50,20,0.5)]"
          style={jua}
        >
          손바닥을 올리고 주먹을 쥐면 그 동물을 잡아요
        </p>
      ) : null}

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
            {game.countdown}
          </span>
        </div>
      ) : null}

      {phase === 'waiting-hands' ? (
        <div className="absolute inset-0 z-20 grid place-items-center bg-[rgba(30,20,8,0.45)]">
          <p
            className="rounded-[28px] bg-[rgba(30,20,8,0.66)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            {handModelReady
              ? '손바닥을 펴서 카메라에 보여 주세요'
              : '동작 인식 준비 중...'}
          </p>
        </div>
      ) : null}

      {phase === 'interrupted' ? (
        <div className="absolute inset-0 z-20 grid place-items-center bg-[rgba(30,20,8,0.45)]">
          <p
            className="rounded-[28px] bg-[rgba(30,20,8,0.66)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            손이 안 보여요! 손바닥을 펴서 보여 주세요
          </p>
        </div>
      ) : null}
    </div>
  )
}

/**
 * 나무판 한쪽에 놓인 그림들.
 *
 * 배치는 라운드가 바뀔 때만 변하므로 props를 모두 원시값으로 받고 memo로 감싸
 * 손이 움직이는 동안에는 다시 그리지 않는다.
 */
const BoardSide = memo(function BoardSide({
  feedbackKind,
  grabbedBy,
  grabbedSymbolId,
  hintSymbolId,
  hoverAId,
  hoverBId,
  matchedSymbolId,
  side,
  symbols,
}: {
  feedbackKind: JudgeKind | null
  grabbedBy: HandSlot | null
  grabbedSymbolId: string | null
  hintSymbolId: string | null
  hoverAId: string | null
  hoverBId: string | null
  matchedSymbolId: string | null
  side: CardSide
  symbols: PlacedSymbol[]
}) {
  const area = cardArea(side)

  return (
    // 바깥 래퍼: 라운드 등장 애니메이션 (마운트될 때 한 번)
    <div
      className="absolute [animation:dobble-side-in_0.36s_ease-out]"
      style={{ left: area.x, top: area.y, width: area.w, height: area.h }}
    >
      {/* 안쪽 래퍼: 오답일 때만 살짝 흔든다 */}
      <div
        className="absolute inset-0"
        style={{
          animation:
            feedbackKind === 'wrong'
              ? 'dobble-side-nudge 0.42s ease-in-out 2'
              : undefined,
        }}
      >
        {symbols.map((symbol) => {
          const hoveredBy: HandSlot | null =
            hoverAId === symbol.symbolId
              ? 'a'
              : hoverBId === symbol.symbolId
                ? 'b'
                : null

          return (
            <BoardSymbol
              grabbedBy={grabbedSymbolId === symbol.symbolId ? grabbedBy : null}
              hinted={hintSymbolId === symbol.symbolId}
              hoveredBy={hoveredBy}
              key={`${symbol.symbolId}-${symbol.x.toFixed(1)}`}
              matched={matchedSymbolId === symbol.symbolId}
              symbol={symbol}
            />
          )
        })}
      </div>
    </div>
  )
})

function BoardSymbol({
  grabbedBy,
  hinted,
  hoveredBy,
  matched,
  symbol,
}: {
  grabbedBy: HandSlot | null
  hinted: boolean
  hoveredBy: HandSlot | null
  matched: boolean
  symbol: PlacedSymbol
}) {
  const ringColor = grabbedBy
    ? HAND_COLOR[grabbedBy]
    : hoveredBy
      ? HAND_COLOR[hoveredBy]
      : null
  const scale = matched ? 1 : grabbedBy ? 1.12 : hoveredBy ? 1.05 : 1

  return (
    <div
      className="absolute"
      style={{
        left: symbol.x,
        top: symbol.y,
        transform: 'translate(-50%, -50%)',
      }}
    >
      {/*
        힌트: 흰 코어에서 번지는 후광 + 회전하는 점선 링 + 통통 튀기.
        배경이 따뜻한 나무색이라 금색만으로는 묻힌다. 흰 코어로 명도 대비를 주고
        바깥을 진한 주황으로 감싸 경계를 살린다. 회전 링은 색보다 먼저 눈에 들어온다.
        "틀렸다"는 표시가 아니라 "여기 봐요"로 읽히게 한다.
      */}
      {hinted ? (
        <>
          <span
            className="absolute top-1/2 left-1/2 [animation:dobble-hint-glow_1.3s_ease-in-out_infinite] rounded-full"
            style={{
              width: symbol.hitRadius * HINT_GLOW_SCALE,
              height: symbol.hitRadius * HINT_GLOW_SCALE,
              background: `radial-gradient(closest-side, rgba(255,255,255,0.96) 0%, ${HINT_CORE} 30%, ${HINT_EDGE} 62%, rgba(255,110,0,0) 100%)`,
            }}
          />
          <span
            className="absolute top-1/2 left-1/2 [animation:dobble-hint-spin_3.4s_linear_infinite] rounded-full"
            style={{
              width: symbol.hitRadius * 2.35,
              height: symbol.hitRadius * 2.35,
              border: `6px dashed ${HINT_CORE}`,
              filter: 'drop-shadow(0 0 5px rgba(110,54,0,0.6))',
            }}
          />
        </>
      ) : null}

      {/* 잡히는 순간 튀어오른다. 잡은 상태에서 계속 흔들리지는 않는다. */}
      <div
        style={{
          animation: grabbedBy
            ? 'dobble-grab-pop 0.34s ease-out'
            : hinted
              ? 'dobble-hint-bob 1.3s ease-in-out infinite'
              : undefined,
        }}
      >
        {ringColor ? (
          <span
            className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
            style={{
              width: symbol.hitRadius * 2,
              height: symbol.hitRadius * 2,
              border: `${grabbedBy ? 7 : 5}px ${grabbedBy ? 'solid' : 'dashed'} ${ringColor}`,
              boxShadow: grabbedBy
                ? `0 0 22px ${ringColor}, inset 0 0 14px ${ringColor}55`
                : 'none',
              background: grabbedBy ? `${ringColor}33` : `${ringColor}14`,
            }}
          />
        ) : null}

        <img
          alt={symbol.name}
          className="relative block will-change-transform"
          decoding="sync"
          draggable={false}
          loading="eager"
          src={symbol.imageUrl}
          style={{
            width: symbol.w,
            height: symbol.h,
            transform: `rotate(${symbol.rotation}deg) scale(${scale})`,
            transition: 'transform 180ms ease-out',
            animation: matched
              ? 'dobble-symbol-pop 0.5s ease-out forwards'
              : undefined,
            filter: matched
              ? 'drop-shadow(0 0 16px rgba(255,224,120,0.95))'
              : hinted
                ? // 동물 윤곽 자체가 빛나야 후광이 "저 동물"로 읽힌다.
                  `drop-shadow(0 0 3px rgba(255,255,255,0.95)) drop-shadow(0 0 14px ${HINT_CORE}) drop-shadow(0 6px 10px rgba(80,52,18,0.5))`
                : 'drop-shadow(0 6px 8px rgba(80,52,18,0.42))',
          }}
        />

        {/* 손을 치워도 "내가 고른 것"이 남도록 그 손 색 발자국을 붙인다. */}
        {grabbedBy ? (
          <span
            className="absolute -top-2 -right-2 grid size-[38px] [animation:dobble-paw-in_0.3s_ease-out] place-items-center rounded-full border-[3px] border-white"
            style={{
              background: HAND_COLOR[grabbedBy],
              boxShadow: '0 3px 8px rgba(60,36,10,0.4)',
            }}
          >
            <PawMark />
          </span>
        ) : null}
      </div>
    </div>
  )
}

/** 잡은 표시용 발자국 */
function PawMark() {
  return (
    <svg
      aria-hidden="true"
      className="block size-[22px]"
      fill="#fff"
      viewBox="0 0 24 24"
    >
      <ellipse cx="12" cy="15.5" rx="6.2" ry="5" />
      <circle cx="5.6" cy="9.4" r="2.5" />
      <circle cx="10" cy="6.4" r="2.6" />
      <circle cx="14.6" cy="6.4" r="2.6" />
      <circle cx="18.6" cy="9.6" r="2.5" />
    </svg>
  )
}

const CURSOR_SIZE = 96
const RING_SIZE = 118

/**
 * 손 포인터.
 *
 * 손바닥/주먹 그림 위에 조준 링이 얹힌다. 위치·손 각도·주먹 여부는 엔진이
 * `--aim`, `--fist`, `--hand-angle` CSS 변수와 transform으로 직접 쓴다.
 */
function HandCursor({
  nodeRef,
  slot,
}: {
  nodeRef: RefObject<HTMLDivElement | null>
  slot: HandSlot
}) {
  const color = HAND_COLOR[slot]

  return (
    <div
      className="pointer-events-none absolute top-0 left-0 z-30 opacity-0 will-change-transform"
      ref={nodeRef}
      style={
        {
          '--aim': 0,
          '--fist': 0,
          '--hand-angle': '0deg',
        } as CSSProperties
      }
    >
      <div className="relative -translate-x-1/2 -translate-y-1/2">
        {/*
          손 모양: 주먹을 쥐면 --fist가 1이 되어 그림이 바뀐다.
          동시에 살짝 작아지고 색이 진해져 "지금 쥐었다"가 몸으로 느껴진다.
        */}
        <div
          className="grid place-items-center"
          style={{
            width: CURSOR_SIZE,
            height: CURSOR_SIZE,
            transform:
              'rotate(calc(var(--hand-angle) + 90deg)) scale(calc(1 - var(--fist) * 0.12))',
            filter:
              'saturate(calc(1 + var(--fist) * 0.6)) brightness(calc(1 - var(--fist) * 0.1))',
            transition: 'transform 110ms ease-out, filter 110ms ease-out',
          }}
        >
          <HandShape color={color} />
        </div>

        {/* 조준 링: --aim(0~1)이 다 차면 주먹을 쥐어 잡을 수 있다. */}
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{
            width: RING_SIZE,
            height: RING_SIZE,
            background: `conic-gradient(from -90deg, ${color} calc(var(--aim) * 360deg), rgba(255,255,255,0.34) 0)`,
            maskImage:
              'radial-gradient(closest-side, transparent 80%, #000 82%)',
            WebkitMaskImage:
              'radial-gradient(closest-side, transparent 80%, #000 82%)',
            filter: 'drop-shadow(0 2px 6px rgba(0,0,0,0.4))',
          }}
        />
      </div>
    </div>
  )
}

/** 펼친 손바닥과 주먹. 두 그림을 겹쳐 두고 `--fist`로 교차 페이드한다. */
function HandShape({ color }: { color: string }) {
  const skin = '#ffd9ae'
  const line = '#c0803f'

  return (
    <svg
      aria-hidden="true"
      className="block h-full w-full drop-shadow-[0_4px_8px_rgba(60,36,10,0.42)]"
      viewBox="0 0 100 100"
    >
      {/* 손목 밴드로 두 손을 색으로 구분한다. */}
      <rect fill={color} height="10" rx="5" width="34" x="33" y="84" />

      <g
        style={{
          opacity: 'calc(1 - var(--fist))',
          transition: 'opacity 110ms',
        }}
      >
        {/* 펼친 손바닥 */}
        <path
          d="M30 52c0-6 4-9 8-9h24c4 0 8 3 8 9v18c0 9-7 16-16 16h-8c-9 0-16-7-16-16z"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
        <rect
          fill={skin}
          height="34"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="33"
          y="28"
        />
        <rect
          fill={skin}
          height="42"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="44"
          y="20"
        />
        <rect
          fill={skin}
          height="36"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="55"
          y="26"
        />
        <rect
          fill={skin}
          height="28"
          rx="5"
          stroke={line}
          strokeWidth="3"
          transform="rotate(18 68 46)"
          width="9"
          x="64"
          y="34"
        />
        <rect
          fill={skin}
          height="24"
          rx="5"
          stroke={line}
          strokeWidth="3"
          transform="rotate(-34 28 62)"
          width="10"
          x="22"
          y="52"
        />
      </g>

      <g style={{ opacity: 'var(--fist)', transition: 'opacity 110ms' }}>
        {/* 주먹 */}
        <path
          d="M28 54c0-9 7-15 16-15h13c9 0 16 6 16 15v16c0 9-7 16-16 16h-13c-9 0-16-7-16-16z"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
        <path
          d="M34 52h32M42 44v10M52 43v11M62 45v9"
          fill="none"
          stroke={line}
          strokeLinecap="round"
          strokeWidth="3"
        />
        <path
          d="M27 62c-4 1-6 4-5 8 1 4 5 5 8 4"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
      </g>
    </svg>
  )
}

function PlayHud({
  matchedCount,
  onPause,
  onQuit,
  phase,
  remainingSec,
  score,
  title,
}: {
  matchedCount: number
  onPause: () => void
  onQuit: () => void
  phase: DobbleGameState['phase']
  remainingSec: number
  score: number
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

      <div className="absolute inset-0">
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
            그림 {matchedCount}개
          </span>
        </div>

        <div
          className={`absolute top-[35px] right-[112px] rounded-full px-5 py-1.5 ${
            timeWarning
              ? 'bg-[rgba(200,60,30,0.72)]'
              : 'bg-[rgba(40,26,10,0.5)]'
          }`}
        >
          <span className="text-[28px] leading-none text-white" style={jua}>
            {formatTime(remainingSec)}
          </span>
        </div>
      </div>

      <div className="absolute top-[25px] right-[30px] size-[62px]">
        <PauseButton disabled={!canPause} onClick={onPause} />
      </div>
    </div>
  )
}
