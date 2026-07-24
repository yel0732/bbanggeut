import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'

import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import { getGameById } from '@/features/games/games'
import { useCameraStream } from '@/hooks/useCameraStream'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function GamePlayPage() {
  const navigate = useNavigate()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { videoRef, status, requestCamera } = useCameraStream()
  const [isPaused, setIsPaused] = useState(false)

  const game = getGameById(gameId)

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
        {/* 깔끔한 초록 필드 배경 (과일이 잘 보이도록 중앙은 밝게) */}
        <div className="absolute inset-0 bg-[radial-gradient(110%_90%_at_50%_38%,#cfe6a0_0%,#a9d47f_46%,#77b458_78%,#4f8f43_100%)]" />
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(20,55,25,0.32)_0%,rgba(20,55,25,0)_26%,rgba(20,55,25,0)_74%,rgba(20,55,25,0.28)_100%)]" />

        <button
          aria-label="게임 그만하기"
          className="absolute top-[25px] left-[45px] z-30 size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={leaveGame}
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
          className="absolute top-[34px] left-[120px] z-30 text-[34px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_7px_10px_rgba(19,50,18,0.28)]"
          style={jua}
        >
          {game.label}
        </h1>

        <button
          aria-label="일시정지"
          className="absolute top-[24px] right-[42px] z-30 grid size-[62px] place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] shadow-[0_6px_0_#5d8e2e,0_10px_14px_rgba(20,55,20,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e]"
          onClick={() => setIsPaused(true)}
          type="button"
        >
          <span className="flex gap-[6px]">
            <span className="h-6 w-[7px] rounded-[2px] bg-white" />
            <span className="h-6 w-[7px] rounded-[2px] bg-white" />
          </span>
        </button>

        <div className="absolute inset-0 z-10 flex items-center justify-center">
          <CameraStage
            onRetry={() => {
              void requestCamera()
            }}
            status={status}
            videoRef={videoRef}
          />
        </div>

        {isPaused ? (
          <PauseOverlay
            onQuit={leaveGame}
            onResume={() => setIsPaused(false)}
          />
        ) : null}
      </div>
    </div>
  )
}

function CameraStage({
  onRetry,
  status,
  videoRef,
}: {
  onRetry: () => void
  status: ReturnType<typeof useCameraStream>['status']
  videoRef: ReturnType<typeof useCameraStream>['videoRef']
}) {
  if (status === 'granted') {
    return (
      <div className="relative h-[440px] w-[600px] overflow-hidden rounded-[28px] border-[5px] border-white/80 bg-black/30 shadow-[0_18px_36px_rgba(15,45,15,0.4)]">
        <video
          autoPlay
          className="h-full w-full scale-x-[-1] object-cover"
          muted
          playsInline
          ref={videoRef}
        />
        <p
          className="absolute inset-x-0 bottom-0 bg-[linear-gradient(0deg,rgba(10,30,12,0.6),transparent)] pt-8 pb-4 text-center text-[24px] leading-none text-white [text-shadow:0_2px_6px_rgba(0,0,0,0.5)]"
          style={jua}
        >
          손을 화면에 보여 주세요
        </p>
      </div>
    )
  }

  if (status === 'requesting' || status === 'idle') {
    return (
      <p
        className="text-[34px] leading-none text-white [text-shadow:0_3px_8px_rgba(20,50,20,0.5)]"
        style={jua}
      >
        카메라 준비 중...
      </p>
    )
  }

  const title =
    status === 'unavailable' ? '카메라를 찾을 수 없어요' : '카메라 없음'

  return (
    <div className="flex flex-col items-center gap-4 text-center">
      <p
        className="text-[52px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_8px_12px_rgba(19,50,18,0.4)]"
        style={jua}
      >
        {title}
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
