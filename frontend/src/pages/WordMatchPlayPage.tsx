import { useCallback, useEffect, useRef } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'

import {
  nowAsLocalDateTime,
  saveCompletedGameSession,
} from '@/api/gameSessions'
import bgmUrl from '@/assets/audio/wordmatch-game/alphabet-gather.mp3'
import answerSlotUrl from '@/assets/images/wordmatch-game/answer-slot.webp'
import backgroundUrl from '@/assets/images/wordmatch-game/background.webp'
import letterCardUrl from '@/assets/images/wordmatch-game/letter-card.webp'
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
import { useCountdownBeep } from '@/features/games/useCountdownBeep'
import { HandCursor } from '@/features/games/recycle/HandCursor'
import { WORD_MATCH_CONFIG as CFG } from '@/features/games/wordmatch/wordMatchConfig'
import {
  hitRadius,
  pictureCenter,
  slotCenter,
  slotSize,
  tileCenter,
  tileSize,
} from '@/features/games/wordmatch/wordMatchGrab'
import { preloadWordImages } from '@/features/games/wordmatch/wordMatchProblems'
import {
  playCorrectSfx,
  playGrabSfx,
  playPlaceSfx,
  playWrongSfx,
} from '@/features/games/wordmatch/wordMatchSfx'
import {
  useWordMatchGame,
  type WordMatchGameState,
  type WordMatchResult,
} from '@/features/games/wordmatch/useWordMatchGame'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandGestures } from '@/hooks/useHandGestures'

const jua = { fontFamily: "'Jua', sans-serif" } as const

/**
 * 판정 안내 띠 위쪽 y(px).
 *
 * 그림(y 85~295)과 정답 칸(y 369부터) 사이에 띄운다. 그림에 붙이면 글자가 그림
 * 위에 겹쳐 무엇을 맞히는 문제인지 가려 버린다.
 */
const JUDGE_BANNER_Y = 298

/*
  ⭐ 자기 모습 미리보기(웹캠) 자리와 크기 — 이 네 숫자로 조절한다.

  지금은 일시정지 버튼(y 18~72) 바로 아래에 두고, 그 아래로 남은 시간·점수가
  이어진다. 화면 아래쪽은 글자 카드 띠가 가로로 꽉 차서 놓을 자리가 없다.

  `right`는 화면 오른쪽 끝에서의 거리다. 왼쪽에 두려면 `WEBCAM.right`를 지우고
  아래 style에서 `right` 대신 `left`를 쓰면 된다.
*/
const WEBCAM = {
  top: 115,
  right: 30,
  width: 116,
  height: 86,
} as const

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

/**
 * 낱말 맞추기 플레이 화면 (WGAME).
 *
 * 위쪽 판에 그림과 정답 칸을, 아래쪽 나무 띠에 글자 카드를 늘어놓는다. 손 인식과
 * 잡기는 분리수거와 같은 방식이라 아이가 다시 배우지 않아도 된다.
 *
 * HUD는 좌우 잔디 쪽에 둔다. 다른 게임처럼 가운데 위에 점수를 놓으면 그림 판을
 * 덮는다.
 */
export function WordMatchPlayPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { videoRef, status, requestCamera } = useCameraStream()
  const selectedAtRef = useRef(nowAsLocalDateTime())
  const playStartedAtRef = useRef<string | null>(null)
  const clientPlayIdRef = useRef(crypto.randomUUID())
  // 음량은 훅 기본값(0.4)을 그대로 쓴다. 게임 방법 화면도 기본값이라, 낮춰 두면
  // 튜토리얼에서 넘어오는 순간 음악이 작아진다.
  const { audioRef, isBgmPlaying, playBgm, toggleBgm } = useBgmPlayer()
  const { gesturesRef, modelReady } = useHandGestures(
    videoRef,
    status === 'granted',
    // 한 손만 쓴다(WGAME-04).
    { maxHands: 1, smoothing: CFG.handSmoothing },
  )

  const game = getGameById('wordmatch')

  useEffect(() => {
    void preloadWordImages()
  }, [])

  useEffect(() => {
    void requestCamera()
  }, [requestCamera])

  const handleEnd = useCallback(
    (result: WordMatchResult) => {
      const goResult = (extra: Record<string, unknown>) =>
        navigate('/games/wordmatch/result', {
          replace: true,
          state: { ...result, ...extra },
        })

      /*
        정합성이 깨진 결과는 서버에 보내지 않는다. 서버도 같은 값을 검증하므로
        어차피 거부된다. 그래도 결과 화면은 보여 준다 — 우리 계산이 틀린 것을
        아이가 책임질 일은 아니다.
      */
      if (!result.valid) {
        goResult({
          recordError: '게임 기록을 저장하지 못했어요.',
          recordSaved: false,
        })
        return
      }

      void saveCompletedGameSession({
        clientPlayId: clientPlayIdRef.current,
        completedAt: nowAsLocalDateTime(),
        gameId: 'wordmatch',
        playDurationSeconds: result.activePlaySec,
        playStartedAt: playStartedAtRef.current ?? selectedAtRef.current,
        score: result.score,
        selectedAt: selectedAtRef.current,
        wordMatchResult: {
          hintUsedCount: result.hintUsedCount,
          incompleteWordCount: result.incompleteWordCount,
          problemResults: result.problemResults,
          rulesVersion: result.rulesVersion,
          solvedWordCount: result.solvedWordCount,
          wrongCount: result.wrongCount,
        },
      })
        .then(() => goResult({ recordSaved: true }))
        .catch((error: unknown) => {
          // 저장에 실패해도 결과 화면은 보여 준다(WGAME-19).
          goResult({
            recordError:
              error instanceof Error
                ? error.message
                : '게임 기록을 저장하지 못했어요.',
            recordSaved: false,
          })
        })
    },
    [navigate],
  )

  const word = useWordMatchGame(
    gesturesRef,
    status === 'granted' || status === 'error',
    handleEnd,
    {
      onCorrect: playCorrectSfx,
      onGrab: playGrabSfx,
      onPlace: playPlaceSfx,
      onWrong: playWrongSfx,
    },
    status === 'error',
  )

  useEffect(() => {
    if (word.phase === 'playing' && !playStartedAtRef.current) {
      playStartedAtRef.current = nowAsLocalDateTime()
    }
  }, [word.phase])

  useCountdownBeep(word.phase === 'countdown', word.countdown)

  /*
    카메라 권한 창이 뜨는 동안 자동 재생이 막히면, 이 화면은 손으로만 조작해서
    다시 눌러 줄 곳이 없다. 게임이 시작되는 순간 한 번 더 시도한다.
  */
  useEffect(() => {
    if (word.phase === 'countdown' || word.phase === 'playing') {
      void playBgm()
    }
  }, [playBgm, word.phase])

  const restartGame = useCallback(() => {
    selectedAtRef.current = nowAsLocalDateTime()
    playStartedAtRef.current = null
    clientPlayIdRef.current = crypto.randomUUID()
    word.restart()
  }, [word])

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
        <audio
          aria-label="낱말 맞추기 배경 음악"
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
            <PlayField game={word} handModelReady={modelReady} />

            {/*
              자기 모습 미리보기 — 일시정지 버튼 바로 아래.

              화면 아래쪽은 글자 카드 띠가 가로로 꽉 차서 놓을 자리가 없다.
              오른쪽 위 잔디에 세로로 쌓아 둔다(일시정지 → 미리보기 → 남은 시간).
            */}
            {status === 'granted' ? (
              <video
                autoPlay
                className="pointer-events-none absolute z-[6] scale-x-[-1] rounded-xl border-[3px] border-white/70 object-cover shadow-[0_6px_16px_rgba(0,0,0,0.35)]"
                muted
                playsInline
                ref={videoRef}
                style={{
                  top: WEBCAM.top,
                  right: WEBCAM.right,
                  width: WEBCAM.width,
                  height: WEBCAM.height,
                }}
              />
            ) : null}

            <PlayHud
              onPause={word.pause}
              onQuit={leaveGame}
              phase={word.phase}
              remainingSec={word.remainingSec}
              score={word.score}
              solvedWordCount={word.solvedWordCount}
              title={game.label}
            />

            {word.phase === 'paused' ? (
              <PauseOverlay
                onQuit={leaveGame}
                onRestart={restartGame}
                onResume={word.resume}
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
          className="absolute bottom-[18px] left-[18px] z-40 size-[54px] border-0 bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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
  game: WordMatchGameState
  handModelReady: boolean
}) {
  const { phase, problem, tiles, judge, hint, hover, slotHover, pointerRefs } =
    game
  const wordLength = problem ? problem.answer.length : 0
  const picture = pictureCenter()
  const slot = slotSize()
  const radius = hitRadius()

  return (
    <div className="absolute inset-0 z-10">
      {problem ? (
        <>
          {/* 문제 그림 — 정답을 맞히면 크게 튀어오른다(WGAME-08). */}
          <img
            alt=""
            className="pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 object-contain"
            decoding="sync"
            draggable={false}
            key={problem.code}
            src={problem.imageUrl}
            style={{
              left: picture.x,
              top: picture.y,
              height: CFG.picture.height,
              animation:
                judge === 'correct'
                  ? 'dobble-grab-pop 620ms ease-out'
                  : undefined,
              filter:
                judge === 'correct'
                  ? 'drop-shadow(0 0 20px rgba(255,236,150,0.95))'
                  : 'drop-shadow(0 8px 12px rgba(90,60,20,0.28))',
            }}
          />

          {/* 정답 칸 */}
          {Array.from({ length: wordLength }, (_, index) => {
            const center = slotCenter(index, wordLength)
            const hovered = slotHover.a === index || slotHover.b === index
            const hinted = hint?.slotIndex === index

            return (
              <div
                className="pointer-events-none absolute -translate-x-1/2 -translate-y-1/2"
                key={index}
                style={{
                  left: center.x,
                  top: center.y,
                  width: slot.width,
                  height: slot.height,
                }}
              >
                <img
                  alt=""
                  className="block h-full w-full object-contain"
                  decoding="async"
                  draggable={false}
                  src={answerSlotUrl}
                  style={{
                    animation: hinted
                      ? 'wordmatch-hint-pulse 1.5s ease-in-out infinite'
                      : undefined,
                    filter: hinted
                      ? 'drop-shadow(0 0 14px rgba(255,206,42,1)) drop-shadow(0 0 26px rgba(255,170,20,0.85))'
                      : hovered
                        ? 'drop-shadow(0 0 12px rgba(255,255,255,0.95))'
                        : 'drop-shadow(0 6px 10px rgba(90,60,20,0.3))',
                    transition: 'filter 140ms ease-out',
                  }}
                />
              </div>
            )
          })}

          {/* 글자 — 카드 자리·정답 칸·손 어디에 있든 이 목록으로 그린다. */}
          {tiles.map((tile) => {
            const center = tileCenter(tile, wordLength)
            const hovered = hover.a === tile.uid || hover.b === tile.uid
            const held = tile.heldBy !== null
            const hinted = hint?.tileUid === tile.uid
            const size = tileSize(tile)

            return (
              <div
                className="pointer-events-none absolute z-[8] -translate-x-1/2 -translate-y-1/2"
                key={tile.uid}
                style={{
                  left: center.x,
                  top: center.y,
                  // 크기를 못박는다. 비워 두면 화면 가장자리에서 상자가 눌린다.
                  width: size,
                  height: size,
                  // 손에 들려 있을 때는 즉각 따라와야 한다. 놓인 뒤 자리를 옮길
                  // 때만 부드럽게 미끄러지게 한다. 크기도 같이 바뀌어 칸에
                  // 앉는 것처럼 보인다.
                  transition: held
                    ? undefined
                    : 'left 160ms ease-out, top 160ms ease-out, width 160ms ease-out, height 160ms ease-out',
                }}
              >
                {hovered && !held ? (
                  <span
                    className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                    style={{
                      width: radius * 2,
                      height: radius * 2,
                      border: '5px dashed rgba(255,255,255,0.85)',
                      background: 'rgba(255,255,255,0.12)',
                    }}
                  />
                ) : null}

                <img
                  alt=""
                  className="absolute inset-0 h-full w-full object-contain"
                  decoding="async"
                  draggable={false}
                  src={letterCardUrl}
                  style={{
                    animation: hinted
                      ? 'wordmatch-hint-pulse 1.5s ease-in-out infinite'
                      : undefined,
                    filter: held
                      ? 'drop-shadow(0 0 16px rgba(255,206,42,0.95))'
                      : hinted
                        ? 'drop-shadow(0 0 14px rgba(255,206,42,1)) drop-shadow(0 0 26px rgba(255,170,20,0.85))'
                        : tile.locked
                          ? 'drop-shadow(0 0 12px rgba(140,240,90,0.9))'
                          : 'drop-shadow(0 5px 9px rgba(90,60,20,0.3))',
                  }}
                />
                <span
                  className="absolute inset-0 flex items-center justify-center leading-none"
                  style={{
                    ...jua,
                    // 상자 크기가 바뀌므로 글꼴도 따라가야 한다. 고정하면 칸에
                    // 놓인 글자가 테두리에 닿는다.
                    fontSize: size * 0.48,
                    // 오답에서 자리가 맞아 고정된 글자는 초록으로 굳는다.
                    color: tile.locked ? '#2f7d1e' : '#5a3c14',
                    transform: held ? 'scale(1.1)' : 'scale(1)',
                    transition: 'transform 140ms ease-out',
                  }}
                >
                  {tile.letter}
                </span>
              </div>
            )
          })}
        </>
      ) : null}

      {/* 손 포인터 — 좌표/각도/주먹 상태는 엔진이 이 노드에 직접 쓴다. */}
      {(['a', 'b'] as const).map((slotKey) => (
        <HandCursor
          key={slotKey}
          nodeRef={pointerRefs[slotKey]}
          slot={slotKey}
        />
      ))}

      {phase === 'countdown' ? (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 bg-[rgba(30,24,8,0.42)]">
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

      {phase === 'waiting-hands' || phase === 'interrupted' ? (
        <div className="absolute inset-0 z-20 grid place-items-center bg-[rgba(30,24,8,0.45)]">
          <p
            className="rounded-[28px] bg-[rgba(30,24,8,0.7)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
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

      {/* 판정 안내 — 두 판 사이 잔디 띠에 띄워 판을 가리지 않는다. */}
      {judge ? (
        <p
          className="absolute left-1/2 z-[22] -translate-x-1/2 [animation:dobble-grab-pop_420ms_ease-out] rounded-full px-8 py-2 text-[34px] leading-none whitespace-nowrap text-white"
          style={{
            ...jua,
            top: JUDGE_BANNER_Y,
            background:
              judge === 'correct'
                ? 'rgba(74,158,42,0.95)'
                : 'rgba(232,146,32,0.95)',
            textShadow: '0 2px 6px rgba(0,0,0,0.3)',
          }}
        >
          {judge === 'correct'
            ? `잘했어요! ${problem?.answer ?? ''}!`
            : '괜찮아요! 다시 놓아 볼까요?'}
        </p>
      ) : null}
    </div>
  )
}

/**
 * 상단 표시줄.
 *
 * 점수와 시간을 좌우 잔디 쪽에 둔다. 가운데 위는 그림 판이 차지해서, 다른
 * 게임처럼 가운데에 점수를 놓으면 판을 덮는다.
 */
function PlayHud({
  onPause,
  onQuit,
  phase,
  remainingSec,
  score,
  solvedWordCount,
  title,
}: {
  onPause: () => void
  onQuit: () => void
  phase: WordMatchGameState['phase']
  remainingSec: number
  score: number
  solvedWordCount: number
  title: string
}) {
  const canPause =
    phase === 'playing' || phase === 'countdown' || phase === 'interrupted'
  const timeWarning = remainingSec <= CFG.timeWarningSec

  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <div className="absolute top-[18px] left-[18px]">
        <QuitButton onClick={onQuit} />
      </div>

      <h1
        className="absolute top-[30px] left-[88px] text-[26px] leading-none text-white [text-shadow:0_3px_0_#7a4d16,0_6px_10px_rgba(40,26,10,0.4)]"
        style={jua}
      >
        {title}
      </h1>

      {/*
        남은 시간과 점수를 왼쪽 잔디에 위에서부터 쌓는다.

        오른쪽은 일시정지 버튼과 자기 모습 미리보기가 쓰고, 가운데 위는 그림 판이
        차지한다. 왼쪽 한 줄에 모아 두면 아이가 볼 곳이 한 군데다.
      */}
      <div className="absolute top-[110px] left-[25px] flex w-[212px] flex-col gap-2">
        <div
          className={`flex flex-col items-center gap-1 rounded-[22px] px-4 py-3 ${
            timeWarning
              ? 'bg-[rgba(200,60,30,0.75)]'
              : 'bg-[rgba(40,26,10,0.55)]'
          }`}
        >
          <span className="text-[19px] leading-none text-white/85" style={jua}>
            남은 시간
          </span>
          <span className="text-[40px] leading-none text-white" style={jua}>
            {formatTime(remainingSec)}
          </span>
        </div>

        <div className="flex flex-col items-center gap-1 rounded-[22px] bg-[rgba(40,26,10,0.55)] px-4 py-3">
          <span className="text-[19px] leading-none text-white/85" style={jua}>
            내 점수
          </span>
          <span
            className="text-[40px] leading-none text-[#ffe27a] [text-shadow:0_3px_0_#8a5a1a,0_6px_10px_rgba(0,0,0,0.4)]"
            style={jua}
          >
            {score.toLocaleString('ko-KR')}
          </span>
          <span className="text-[19px] leading-none text-white/85" style={jua}>
            낱말 {solvedWordCount}개
          </span>
        </div>
      </div>

      <div className="absolute top-[25px] right-[25px] size-[62px]">
        <PauseButton disabled={!canPause} onClick={onPause} />
      </div>
    </div>
  )
}
