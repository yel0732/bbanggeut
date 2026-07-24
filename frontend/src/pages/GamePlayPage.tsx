import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'

import cutSoundUrl from '@/assets/audio/fruit-game/cut-fruit.mp3'
import leafSwordUrl from '@/assets/images/fruit-game/leaf-sword.webp'
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

const PRAISES = ['멋져요!', '최고예요!', '슝!', '좋아요!', '와아!', '나이스!']

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
    // MGAME-21/22: 정합성 통과한 정상 결과만 결과 화면으로. 잘못되면 폐기.
    if (!result.valid) {
      navigate('/games', { replace: true })
      return
    }
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

  // 긍정 성공 문구 (MGAME-19)
  const [praise, setPraise] = useState<string | null>(null)
  const praiseTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(praiseTimerRef.current), [])

  const handleSlice = useCallback(() => {
    const pool = cutPoolRef.current
    if (pool.length > 0) {
      const audio = pool[cutIndexRef.current % pool.length]
      cutIndexRef.current += 1
      audio.currentTime = 0
      void audio.play().catch(() => {})
    }
    setPraise(PRAISES[Math.floor(Math.random() * PRAISES.length)])
    clearTimeout(praiseTimerRef.current)
    praiseTimerRef.current = setTimeout(() => setPraise(null), 750)
  }, [])

  const fruitGame = useFruitGame(
    handRef,
    status === 'granted' || status === 'error',
    handleEnd,
    handleSlice,
    hand.redesignate,
    status === 'error',
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

        {/* 자기 모습 미리보기 (구석, 위치 확인용) — 동작 인식도 이 영상에서 수행 */}
        {status === 'granted' ? (
          <video
            autoPlay
            className="pointer-events-none absolute right-4 bottom-4 z-[6] h-[150px] w-[200px] scale-x-[-1] rounded-2xl border-[3px] border-white/70 object-cover shadow-[0_6px_16px_rgba(0,0,0,0.35)]"
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
                onRestart={fruitGame.restart}
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
  const { phase, fruits, hand, countdown } = game

  // 카메라 거리 안내 (MGAME 친화적 안내)
  let distanceHint: string | null = null
  if (hand && hand.distance === 'near')
    distanceHint = '손을 조금 뒤로 빼 볼까요?'
  else if (hand && hand.distance === 'far')
    distanceHint = '손을 조금 앞으로 내밀어 볼까요?'

  return (
    <div className="absolute inset-0 z-10">
      {/* 과일 */}
      {fruits.map((fruit) => (
        <FruitSprite fruit={fruit} key={fruit.id} />
      ))}

      {/* 손날(베는 날) + 스와이프 트레일 */}
      {hand ? <HandBlade hand={hand} /> : null}

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
            className="rounded-[28px] bg-[rgba(14,30,15,0.55)] px-8 py-5 text-center text-[32px] leading-snug text-white [text-shadow:0_3px_8px_rgba(0,0,0,0.5)]"
            style={jua}
          >
            {handModelReady
              ? '몸이 보이게 뒤로 물러나 한 손을 번쩍 들어 주세요'
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

// leaf-sword.webp 내 손잡이 끝/칼끝 앵커 (이미지 비율 좌표)
const SWORD_SIZE = 360
const SWORD_HANDLE = { x: 0.8, y: 0.82 }
const SWORD_TIP = { x: 0.14, y: 0.16 }

function HandBlade({ hand }: { hand: HandScreen }) {
  const { blade, trail, fast } = hand

  // 손목(a)→손끝(b)에 검 이미지를 맞추는 유사변환(회전+스케일). 단일 img +
  // GPU 합성 transform이라 빠르게 움직여도 가볍다.
  const hix = SWORD_HANDLE.x * SWORD_SIZE
  const hiy = SWORD_HANDLE.y * SWORD_SIZE
  const vix = (SWORD_TIP.x - SWORD_HANDLE.x) * SWORD_SIZE
  const viy = (SWORD_TIP.y - SWORD_HANDLE.y) * SWORD_SIZE
  const viLen = Math.hypot(vix, viy) || 1
  const vtx = blade.bx - blade.ax
  const vty = blade.by - blade.ay
  const vtLen = Math.hypot(vtx, vty) || 1
  const scale = Math.max(vtLen / viLen, 0.62)
  const rot = Math.atan2(vty, vtx) - Math.atan2(viy, vix)
  const cos = Math.cos(rot)
  const sin = Math.sin(rot)
  // 이미지 손잡이 앵커를 손목 좌표로 보내는 오프셋 (회전·스케일 반영)
  const originX = blade.ax - scale * (cos * hix - sin * hiy)
  const originY = blade.ay - scale * (sin * hix + cos * hiy)

  const trailPts = trail.map((point) => `${point.x},${point.y}`).join(' ')

  return (
    <div className="pointer-events-none absolute inset-0">
      {trail.length > 1 ? (
        <svg
          className="absolute inset-0"
          height="100%"
          preserveAspectRatio="none"
          viewBox="0 0 1280 720"
          width="100%"
        >
          <polyline
            fill="none"
            opacity={0.55}
            points={trailPts}
            stroke={fast ? '#d6f56a' : 'rgba(255,255,255,0.45)'}
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={fast ? 18 : 10}
          />
        </svg>
      ) : null}
      <img
        alt=""
        className="absolute top-0 left-0 will-change-transform"
        decoding="async"
        draggable={false}
        src={leafSwordUrl}
        style={{
          width: SWORD_SIZE,
          height: SWORD_SIZE,
          transformOrigin: '0 0',
          transform: `translate(${originX}px, ${originY}px) rotate(${rot}rad) scale(${scale})`,
          filter: fast
            ? 'drop-shadow(0 0 12px rgba(214,245,106,0.9))'
            : 'drop-shadow(0 4px 6px rgba(0,0,0,0.3))',
        }}
      />
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

function CameraErrorOverlay({
  onQuit,
  onRetry,
}: {
  onQuit: () => void
  onRetry: () => void
}) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(14,30,15,0.6)]"
      role="dialog"
    >
      <div className="flex w-[440px] flex-col items-center gap-6 rounded-[36px] border-[8px] border-[#8a5a2b] bg-[#f4ead0] px-10 py-9 shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <p
          className="text-center text-[32px] leading-snug text-[#6f4a1f]"
          style={jua}
        >
          카메라에 문제가 생겼어요
        </p>
        <div className="flex w-full flex-col items-center gap-3">
          <button
            className="grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] text-[26px] leading-none text-white shadow-[0_6px_0_#5d8e2e] transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e]"
            onClick={onRetry}
            style={jua}
            type="button"
          >
            다시 시도
          </button>
          <button
            className="grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white bg-[#e6a838] text-[26px] leading-none text-white shadow-[0_6px_0_#a9761f] transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#a9761f]"
            onClick={onQuit}
            style={jua}
            type="button"
          >
            메인으로
          </button>
        </div>
      </div>
    </div>
  )
}

function PauseOverlay({
  onQuit,
  onRestart,
  onResume,
}: {
  onQuit: () => void
  onRestart: () => void
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
            className="grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white bg-[#f0c94b] text-[26px] leading-none text-white shadow-[0_6px_0_#b6892a] transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#b6892a]"
            onClick={onRestart}
            style={jua}
            type="button"
          >
            처음부터 다시
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
