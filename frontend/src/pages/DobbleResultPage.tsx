import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
// 결과 배경은 게임 공통 정글 배경이라 과일 게임 에셋을 함께 쓴다.
import backgroundUrl from '@/assets/images/fruit-game/result-bg.webp'
import gameMenuButtonUrl from '@/assets/images/game-entry/game-menu-button.webp'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import type { DobbleGameResult } from '@/features/games/dobble/useDobbleGame'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

/**
 * 도블(그림 맞추기) 결과 화면.
 *
 * 시간이 끝나면 플레이 화면이 결과를 navigate state로 넘겨 준다. 놓친 라운드나
 * 오답 횟수는 표시하지 않고 찾아낸 그림 수만 긍정적으로 보여 준다.
 */
export function DobbleResultPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying } = useBgmPlayer()

  const result = location.state as
    (DobbleGameResult & { recordError?: string }) | null
  const [recordError, setRecordError] = useState(result?.recordError ?? '')

  // 정상 완료로 전달된 결과가 없으면 게임 선택으로 되돌린다.
  if (!result) {
    return <Navigate replace to="/games" />
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
          loop
          preload="auto"
          ref={audioRef}
          src={bgmUrl}
        />
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
            눈썰미가 정말 좋아요!
          </p>

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

          <p
            className="rounded-full bg-[rgba(14,30,15,0.5)] px-8 py-3 text-[30px] leading-none text-white"
            style={jua}
          >
            같은 그림을 {result.matchedCount}개나 찾았어요!
          </p>

          <div className="mt-4 flex items-center gap-6">
            <ResultButton
              label="다시하기"
              onClick={() => navigate('/games/dobble/play', { replace: true })}
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
