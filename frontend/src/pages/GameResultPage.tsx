import { useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/fruit-game/result-bg.webp'
import gameMenuButtonUrl from '@/assets/images/game-entry/game-menu-button.webp'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import type { FruitGameResult } from '@/features/games/fruit/useFruitGame'
import { getGameById } from '@/features/games/games'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function GameResultPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { gameId } = useParams()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying } = useBgmPlayer()

  const game = getGameById(gameId)
  const result = location.state as
    | (FruitGameResult & { recordError?: string })
    | null
  const [recordError, setRecordError] = useState(result?.recordError ?? '')

  // 정상 완료로 전달된 결과가 없으면 안내 화면으로 되돌린다.
  if (!game || !result) {
    return <Navigate replace to="/games" />
  }

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
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
        {/* isBgmPlaying는 재생 상태 추적용 (음소거 버튼은 이 화면에서 생략) */}
        <span className="sr-only">
          {isBgmPlaying ? '음악 켜짐' : '음악 꺼짐'}
        </span>

        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[rgba(10,26,12,0.42)]" />

        <div className="absolute inset-0 flex -translate-y-8 flex-col items-center justify-center gap-6">
          <p
            className="text-[40px] leading-none text-white [text-shadow:0_3px_0_#3f7d1e,0_6px_10px_rgba(0,0,0,0.4)]"
            style={jua}
          >
            정말 잘했어요!
          </p>

          {/* 최종 점수 — 가장 크게 강조 (MGAME-23) */}
          <div className="flex flex-col items-center gap-1">
            <span
              className="text-[28px] leading-none text-[#d8ffb0]"
              style={jua}
            >
              내 점수
            </span>
            <span
              className="text-[140px] leading-none text-[#ffe27a] [text-shadow:0_6px_0_#8a5a1a,0_12px_20px_rgba(0,0,0,0.5)]"
              style={jua}
            >
              {result.score}
            </span>
          </div>

          {/* 자른 과일 수 — 긍정적 표현 (등장/놓친 수는 표시하지 않음) */}
          <p
            className="rounded-full bg-[rgba(14,30,15,0.5)] px-8 py-3 text-[30px] leading-none text-white"
            style={jua}
          >
            과일을 {result.slicedFruitCount}개나 슝슝 잘랐어요!
          </p>

          {/* 최고 콤보 — 이어 벤 적이 있을 때만 자랑거리로 보여 준다. */}
          {result.maxCombo >= 2 ? (
            <p
              className="rounded-full bg-[rgba(255,153,0,0.75)] px-7 py-2 text-[26px] leading-none text-white"
              style={jua}
            >
              한 번에 {result.maxCombo}개 연속! 최고 콤보예요
            </p>
          ) : null}

          <div className="mt-4 flex items-center gap-6">
            <ResultButton
              label="다시하기"
              onClick={() =>
                navigate(`/games/${game.id}/play`, { replace: true })
              }
            />
            <ResultButton
              label="다른 게임 하기"
              onClick={() => navigate('/games')}
            />
            <ResultButton
              label="대화하기"
              onClick={() => navigate('/conversation')}
            />
            <ResultButton label="그만하기" onClick={() => navigate('/child')} />
          </div>
        </div>
        {recordError ? (
          <ParentNoticeDialog
            compact
            confirmLabel="확인"
            message={recordError}
            onConfirm={() => setRecordError('')}
            title="게임 기록을 저장하지 못했어요"
          />
        ) : null}
      </div>
    </div>
  )
}

function ResultButton({
  label,
  onClick,
}: {
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="relative h-[74px] w-[210px] border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white active:scale-[0.98]"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-fill drop-shadow-[0_8px_12px_rgba(55,30,10,0.28)]"
        decoding="async"
        draggable={false}
        src={gameMenuButtonUrl}
      />
      <span
        className="absolute inset-0 flex items-center justify-center pb-1 text-[24px] leading-none text-white [text-shadow:0_3px_0_#744514,0_6px_9px_rgba(55,30,10,0.24)]"
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
