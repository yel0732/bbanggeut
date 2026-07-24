import { useCallback, useEffect, useRef, type CSSProperties } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'

import cutSoundUrl from '@/assets/audio/fruit-game/cut-fruit.mp3'
import playFrameUrl from '@/assets/images/fruit-game/play-frame.webp'
import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import {
  useFruitGame,
  type FruitGameResult,
  type FruitGameState,
  type HandScreen,
  type RenderFruit,
} from '@/features/games/fruit/useFruitGame'
import { getGameById } from '@/features/games/games'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'
import { useHandTracking } from '@/hooks/useHandTracking'

const jua = { fontFamily: "'Jua', sans-serif" } as const

function formatTime(totalSec: number): string {
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return `${min}:${sec.toString().padStart(2, '0')}`
}

export function GamePlayPage() {
  const navigate = useNavigate()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { videoRef, status, requestCamera } = useCameraStream()
  const hand = useHandTracking(videoRef, status === 'granted')

  // 엔진이 매 프레임 최신 손 데이터를 읽도록 ref로 전달한다.
  const handRef = useRef(hand.playHand)
  handRef.current = hand.playHand

  const game = getGameById(gameId)

  const handleEnd = (result: FruitGameResult) => {
    navigate(`/games/${gameId}/result`, { replace: true, state: result })
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

  const playCut = useCallback(() => {
    const pool = cutPoolRef.current
    if (pool.length === 0) return
    const audio = pool[cutIndexRef.current % pool.length]
    cutIndexRef.current += 1
    audio.currentTime = 0
    void audio.play().catch(() => {})
  }, [])

  const fruitGame = useFruitGame(
    handRef,
    status === 'granted',
    handleEnd,
    playCut,
  )

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
        <div className="absolute inset-0 bg-[radial-gradient(110%_90%_at_50%_38%,#cfe6a0_0%,#a9d47f_46%,#77b458_78%,#4f8f43_100%)]" />

        {/* 손 인식용 카메라 (영상은 표시하지 않음, 손만 사용) */}
        {status === 'granted' ? (
          <video
            aria-hidden
            autoPlay
            className="pointer-events-none absolute h-px w-px opacity-0"
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

        {status === 'granted' ? (
          <>
            <PlayField game={fruitGame} handModelReady={hand.modelReady} />

            <PlayHud
              onPause={fruitGame.pause}
              onQuit={leaveGame}
              phase={fruitGame.phase}
              remainingSec={fruitGame.remainingSec}
              score={fruitGame.score}
              title={game.label}
            />

            {fruitGame.phase === 'paused' ? (
              <PauseOverlay onQuit={leaveGame} onResume={fruitGame.resume} />
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
      </div>
    </div>
  )
}

function PlayField({
  game,
  handModelReady,
}: {
  game: FruitGameState
  handModelReady: boolean
}) {
  const { phase, fruits, hand, countdown } = game

  return (
    <div className="absolute inset-0 z-10">
      {/* 과일 */}
      {fruits.map((fruit) => (
        <FruitSprite fruit={fruit} key={fruit.id} />
      ))}

      {/* 손날(베는 날) + 스와이프 트레일 */}
      {hand ? <HandBlade hand={hand} /> : null}

      {/* 카운트다운 / 손 대기 안내 */}
      {phase === 'countdown' ? (
        <div className="absolute inset-0 grid place-items-center">
          <span
            className="text-[180px] leading-none text-white [text-shadow:0_6px_0_#4f8a30,0_12px_20px_rgba(19,50,18,0.5)]"
            style={jua}
          >
            {countdown}
          </span>
        </div>
      ) : null}

      {phase === 'waiting-hand' ? (
        <div className="absolute inset-0 grid place-items-center">
          <p
            className="rounded-[28px] bg-[rgba(14,30,15,0.55)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            {handModelReady ? '손을 화면에 보여 주세요' : '손 인식 준비 중...'}
          </p>
        </div>
      ) : null}
    </div>
  )
}

function FruitSprite({ fruit }: { fruit: RenderFruit }) {
  const { x, y, w, h, imageUrl, rotation, sliced } = fruit

  if (!sliced) {
    return (
      <img
        alt=""
        className="absolute select-none"
        decoding="async"
        draggable={false}
        src={imageUrl}
        style={{
          left: x,
          top: y,
          width: w,
          height: h,
          transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
          filter: 'drop-shadow(0 6px 8px rgba(20,50,20,0.4))',
        }}
      />
    )
  }

  const p = fruit.sliceProgress
  const gap = p * (h * 0.4 + 26)
  const halfBase: CSSProperties = {
    position: 'absolute',
    inset: 0,
    backgroundImage: `url(${imageUrl})`,
    backgroundSize: '100% 100%',
    filter: 'drop-shadow(0 4px 6px rgba(0,0,0,0.28))',
  }

  return (
    <>
      <div
        className="absolute"
        style={{
          left: x,
          top: y,
          width: w,
          height: h,
          opacity: 1 - p * 0.9,
          transform: `translate(-50%, -50%) rotate(${fruit.cutAngle}deg)`,
        }}
      >
        <div
          style={{
            ...halfBase,
            clipPath: 'inset(0 0 50% 0)',
            transform: `translateY(${-gap}px) rotate(${-p * 10}deg)`,
          }}
        />
        <div
          style={{
            ...halfBase,
            clipPath: 'inset(50% 0 0 0)',
            transform: `translateY(${gap}px) rotate(${p * 10}deg)`,
          }}
        />
        {p < 0.4 ? (
          <div
            className="absolute top-1/2 -left-[10%] h-[5px] w-[120%] -translate-y-1/2 rounded-full bg-white"
            style={{ opacity: (0.4 - p) / 0.4, boxShadow: '0 0 10px #fff' }}
          />
        ) : null}
      </div>
      <span
        className="absolute -translate-x-1/2 text-[34px] leading-none text-[#ffe27a]"
        style={{
          left: x,
          top: y,
          transform: `translate(-50%, ${-40 - p * 46}px)`,
          opacity: 1 - p,
          fontFamily: "'Jua', sans-serif",
          textShadow: '0 3px 0 #8a5a1a, 0 5px 8px rgba(0,0,0,0.4)',
        }}
      >
        +{fruit.score}
      </span>
    </>
  )
}

function HandBlade({ hand }: { hand: HandScreen }) {
  const { blade, trail, fast } = hand
  const dx = blade.bx - blade.ax
  const dy = blade.by - blade.ay
  const len = Math.hypot(dx, dy) || 1
  const ux = dx / len
  const uy = dy / len
  const nx = -uy
  const ny = ux

  // 손목(a) 뒤쪽에 손잡이, 손끝(b)까지 칼날인 장난감 칼
  const gripStartX = blade.ax - ux * 26
  const gripStartY = blade.ay - uy * 26
  const guardX = blade.ax + ux * len * 0.24
  const guardY = blade.ay + uy * len * 0.24

  const bladeLen = len * 0.76
  const midX = guardX + ux * bladeLen * 0.72
  const midY = guardY + uy * bladeLen * 0.72
  const hw = 16
  const hwMid = hw * 0.82

  const bladePts = [
    `${guardX + nx * hw},${guardY + ny * hw}`,
    `${midX + nx * hwMid},${midY + ny * hwMid}`,
    `${blade.bx},${blade.by}`,
    `${midX - nx * hwMid},${midY - ny * hwMid}`,
    `${guardX - nx * hw},${guardY - ny * hw}`,
  ].join(' ')

  const trailPts = trail.map((point) => `${point.x},${point.y}`).join(' ')
  const bladeFill = fast ? '#ffffff' : '#e8f0f6'
  const bladeStroke = fast ? '#ff8a5c' : '#8fa7bd'
  const guardHalf = 22

  return (
    <svg
      className="pointer-events-none absolute inset-0"
      height="100%"
      preserveAspectRatio="none"
      viewBox="0 0 1280 720"
      width="100%"
    >
      {trail.length > 1 ? (
        <polyline
          fill="none"
          opacity={0.6}
          points={trailPts}
          stroke={fast ? '#ffe27a' : 'rgba(255,255,255,0.5)'}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={fast ? 18 : 10}
        />
      ) : null}

      {/* 손잡이 */}
      <line
        stroke="#b4552b"
        strokeLinecap="round"
        strokeWidth={20}
        x1={gripStartX}
        x2={blade.ax + ux * len * 0.12}
        y1={gripStartY}
        y2={blade.ay + uy * len * 0.12}
      />
      <line
        stroke="#e07a3e"
        strokeLinecap="round"
        strokeWidth={12}
        x1={gripStartX}
        x2={blade.ax + ux * len * 0.12}
        y1={gripStartY}
        y2={blade.ay + uy * len * 0.12}
      />
      {/* 가드(십자) */}
      <line
        stroke="#ffcf4d"
        strokeLinecap="round"
        strokeWidth={11}
        x1={guardX + nx * guardHalf}
        x2={guardX - nx * guardHalf}
        y1={guardY + ny * guardHalf}
        y2={guardY - ny * guardHalf}
      />
      {/* 칼날 */}
      <polygon
        fill={bladeFill}
        points={bladePts}
        stroke={bladeStroke}
        strokeLinejoin="round"
        strokeWidth={3}
      />
      {/* 칼날 광택 라인 */}
      <line
        opacity={0.6}
        stroke="#ffffff"
        strokeLinecap="round"
        strokeWidth={3}
        x1={guardX + ux * 10}
        x2={midX}
        y1={guardY + uy * 10}
        y2={midY}
      />
    </svg>
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
  const canPause = phase === 'playing' || phase === 'countdown'

  return (
    <div className="pointer-events-none absolute inset-0 z-20">
      <button
        aria-label="게임 그만하기"
        className="pointer-events-auto absolute top-[25px] left-[45px] size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
        onClick={onQuit}
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

      <h1
        className="absolute top-[34px] left-[120px] text-[30px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_7px_10px_rgba(19,50,18,0.28)]"
        style={jua}
      >
        {title}
      </h1>

      {/* 진행 중 현재 점수만 표시 (MGAME-18) */}
      <div className="absolute top-[22px] left-1/2 -translate-x-1/2 rounded-full bg-[rgba(14,30,15,0.5)] px-8 py-2 text-center">
        <span
          className="text-[40px] leading-none text-[#ffe27a] [text-shadow:0_3px_0_#8a5a1a,0_6px_10px_rgba(0,0,0,0.4)]"
          style={jua}
        >
          {score}
        </span>
      </div>

      <div className="absolute top-[30px] right-[120px] rounded-full bg-[rgba(14,30,15,0.5)] px-5 py-1.5">
        <span className="text-[28px] leading-none text-white" style={jua}>
          {formatTime(remainingSec)}
        </span>
      </div>

      <button
        aria-label="일시정지"
        className="pointer-events-auto absolute top-[24px] right-[42px] grid size-[62px] place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] shadow-[0_6px_0_#5d8e2e,0_10px_14px_rgba(20,55,20,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e] disabled:opacity-40"
        disabled={!canPause}
        onClick={onPause}
        type="button"
      >
        <span className="flex gap-[6px]">
          <span className="h-6 w-[7px] rounded-[2px] bg-white" />
          <span className="h-6 w-[7px] rounded-[2px] bg-white" />
        </span>
      </button>
    </div>
  )
}

function CameraPermissionView({
  onQuit,
  onRetry,
  status,
  title,
}: {
  onQuit: () => void
  onRetry: () => void
  status: ReturnType<typeof useCameraStream>['status']
  title: string
}) {
  return (
    <>
      <button
        aria-label="게임 그만하기"
        className="absolute top-[25px] left-[45px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
        onClick={onQuit}
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
      <h1
        className="absolute top-[34px] left-[120px] z-30 text-[30px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_7px_10px_rgba(19,50,18,0.28)]"
        style={jua}
      >
        {title}
      </h1>

      <div className="absolute inset-0 z-10 flex items-center justify-center">
        {status === 'requesting' || status === 'idle' ? (
          <p
            className="text-[34px] leading-none text-white [text-shadow:0_3px_8px_rgba(20,50,20,0.5)]"
            style={jua}
          >
            카메라 준비 중...
          </p>
        ) : (
          <div className="flex flex-col items-center gap-4 text-center">
            <p
              className="text-[52px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_8px_12px_rgba(19,50,18,0.4)]"
              style={jua}
            >
              {status === 'unavailable'
                ? '카메라를 찾을 수 없어요'
                : '카메라 없음'}
            </p>
            <p
              className="text-[24px] leading-snug text-white/90 [text-shadow:0_2px_6px_rgba(20,50,20,0.45)]"
              style={jua}
            >
              브라우저 주소창의 카메라 아이콘에서 권한을 허용해 주세요
            </p>
            <button
              className="mt-2 grid h-[62px] w-[200px] place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] text-[26px] leading-none text-white shadow-[0_6px_0_#5d8e2e,0_10px_14px_rgba(20,55,20,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e]"
              onClick={onRetry}
              style={jua}
              type="button"
            >
              다시 시도
            </button>
          </div>
        )}
      </div>
    </>
  )
}

function PauseOverlay({
  onQuit,
  onResume,
}: {
  onQuit: () => void
  onResume: () => void
}) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(14,30,15,0.55)]"
      role="dialog"
    >
      <div className="flex w-[420px] flex-col items-center gap-6 rounded-[36px] border-[8px] border-[#8a5a2b] bg-[#f4ead0] px-10 py-9 shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <p className="text-[34px] leading-none text-[#6f4a1f]" style={jua}>
          잠깐 쉬어 갈까요?
        </p>
        <div className="flex w-full flex-col items-center gap-3">
          <button
            className="grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] text-[26px] leading-none text-white shadow-[0_6px_0_#5d8e2e] transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e]"
            onClick={onResume}
            style={jua}
            type="button"
          >
            이어하기
          </button>
          <button
            className="grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white bg-[#e6a838] text-[26px] leading-none text-white shadow-[0_6px_0_#a9761f] transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#a9761f]"
            onClick={onQuit}
            style={jua}
            type="button"
          >
            그만하기
          </button>
        </div>
      </div>
    </div>
  )
}
