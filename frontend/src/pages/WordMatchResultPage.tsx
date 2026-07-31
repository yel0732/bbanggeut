import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

// 결과 배경은 게임 공통 정글 배경이라 과일 게임 에셋을 함께 쓴다.
import backgroundUrl from '@/assets/images/fruit-game/result-bg.webp'
import gameResultButtonUrl from '@/assets/images/game-entry/game-result-button.webp'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import type { WordMatchResult } from '@/features/games/wordmatch/useWordMatchGame'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const

/**
 * 낱말 맞추기 결과 화면 (WGAME-16).
 *
 * 시간이 끝나면 플레이 화면이 결과를 navigate state로 넘겨 준다. 오답 횟수와
 * 미완료 낱말은 표시하지 않는다 — 점수와 완성한 낱말 수만 긍정적으로 보여 준다.
 */
export function WordMatchResultPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying } = useAdventureBgm()

  const result = location.state as
    (WordMatchResult & { recordError?: string }) | null
  const [recordError, setRecordError] = useState(result?.recordError ?? '')

  // 정상 완료로 전달된 결과가 없으면 게임 선택으로 되돌린다.
  if (!result) {
    return <Navigate replace to="/games/more" />
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#123219_0%,_#08160d_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
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

        <div className="absolute inset-0 flex -translate-y-1 flex-col items-center justify-center gap-6">
          <p
            className="text-[50px] leading-none text-white [text-shadow:0_3px_0_#3f7d1e,0_6px_10px_rgba(0,0,0,0.4)]"
            style={jua}
          >
            낱말을 정말 잘 맞췄어요!
          </p>

          <div className="flex flex-col items-center gap-3">
            <span
              className="text-[30px] leading-none text-[#d8ffb0]"
              style={jua}
            >
              내 점수
            </span>
            <span
              className="text-[160px] leading-none text-[#ffe27a] [text-shadow:0_6px_0_#8a5a1a,0_12px_20px_rgba(0,0,0,0.5)]"
              style={jua}
            >
              {result.score.toLocaleString('ko-KR')}
            </span>
          </div>

          <p
            className="rounded-full bg-[rgba(14,30,15,0.5)] px-8 py-4 text-[33px] leading-none text-white"
            style={jua}
          >
            낱말을 {result.solvedWordCount}개나 완성했어요!
          </p>

          <div className="mt-8 flex items-center gap-6">
            <ResultButton
              label="다시하기"
              onClick={() =>
                navigate('/games/wordmatch/play', { replace: true })
              }
            />
            <ResultButton
              label="다른 게임 하기"
              onClick={() => navigate('/games/more')}
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
        src={gameResultButtonUrl}
      />
      <span
        className="absolute inset-0 flex items-center justify-center pb-0 text-[28px] leading-none text-[#5a4a2a] [text-shadow:0_1px_0_rgba(255,255,255,0.5)]"
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
