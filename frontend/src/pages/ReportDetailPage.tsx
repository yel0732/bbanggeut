import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import {
  getChildReport,
  isBackendChildId,
  regenerateChildReport,
} from '@/api/parentMode'
import type { ReportDetailResponse, ReportGameResult } from '@/api/parentMode'
import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/parent-mode/background.webp'
import backButtonUrl from '@/assets/images/report/left-btn.webp'
import reportPaperUrl from '@/assets/images/report/report-paper.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import { makeDemoReportDetail } from '@/lib/reportDemo'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function ReportDetailPage() {
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const { reportDate = '' } = useParams()
  const [searchParams] = useSearchParams()
  const childId = searchParams.get('childId') ?? ''
  const childName = searchParams.get('childName') ?? '아이'
  const [report, setReport] = useState<ReportDetailResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryMessage, setRetryMessage] = useState('')
  const [isRetrying, setIsRetrying] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    async function loadReport() {
      setIsLoading(true)
      setError('')
      if (!childId || !reportDate) {
        setError('리포트를 찾기 위한 정보가 부족해요.')
        setIsLoading(false)
        return
      }
      if (!isBackendChildId(childId)) {
        setReport(makeDemoReportDetail(reportDate))
        setIsLoading(false)
        return
      }
      try {
        setReport(
          await getChildReport(Number(childId), reportDate, controller.signal),
        )
      } catch (loadError) {
        if (controller.signal.aborted) return
        setError(
          loadError instanceof Error
            ? loadError.message
            : '리포트를 불러오지 못했어요.',
        )
      } finally {
        if (!controller.signal.aborted) setIsLoading(false)
      }
    }
    void loadReport()
    return () => controller.abort()
  }, [childId, reportDate])

  async function requestRegeneration() {
    if (!isBackendChildId(childId)) {
      setRetryMessage('리포트 재생성을 요청했어요.')
      setReport((current) =>
        current ? { ...current, status: 'PENDING', failure: null } : current,
      )
      return
    }
    setIsRetrying(true)
    setRetryMessage('')
    try {
      const response = await regenerateChildReport(Number(childId), reportDate)
      setRetryMessage(response.message || '리포트 재생성을 요청했어요.')
      setReport((current) =>
        current
          ? { ...current, status: response.status, failure: null }
          : current,
      )
    } catch (retryError) {
      setRetryMessage(
        retryError instanceof Error
          ? retryError.message
          : '재생성을 요청하지 못했어요.',
      )
    } finally {
      setIsRetrying(false)
    }
  }

  const listHref = `/reports?childId=${encodeURIComponent(childId)}&childName=${encodeURIComponent(childName)}`

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] shrink-0 overflow-hidden"
        ref={canvasRef}
      >
        <audio loop preload="auto" ref={audioRef} src={bgmUrl} />
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          src={backgroundUrl}
        />
        <Link
          aria-label="리포트 목록으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
          to={listHref}
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-106"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="absolute -top-1 right-[165px] bottom-15 left-[160px] z-5 overflow-hidden">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full object-fill"
            src={reportPaperUrl}
          />
          <div className="absolute top-[8%] right-[10%] bottom-[7%] left-[10%] [scrollbar-width:thin] [scrollbar-color:#cdbb92_transparent] overflow-y-auto px-6 pb-8">
            <header className="mb-5 text-center" style={jua}>
              <p className="text-[18px] text-[#82945a]">{childName}</p>
              <h1 className="text-[34px] text-[#4f6f35]">
                {formatLongDate(reportDate)} 일일 리포트
              </h1>
            </header>

            {isLoading ? (
              <Message>리포트를 불러오고 있어요...</Message>
            ) : error ? (
              <Message>{error}</Message>
            ) : report ? (
              <ReportContent
                isRetrying={isRetrying}
                onRetry={() => void requestRegeneration()}
                report={report}
                retryMessage={retryMessage}
              />
            ) : null}
          </div>
        </main>
      </div>
    </div>
  )
}

export function ReportPaperContent({
  childName,
  isRetrying,
  onRetry,
  report,
  retryMessage,
}: {
  childName: string
  isRetrying: boolean
  onRetry: () => void
  report: ReportDetailResponse
  retryMessage: string
}) {
  const hasGames =
    report.status === 'COMPLETED' &&
    report.dataAvailability.hasGameData &&
    report.gameResults.length > 0
  const hasConversation =
    report.status === 'COMPLETED' &&
    report.dataAvailability.hasConversationData &&
    report.conversationSummary
  const gameSlots = [
    {
      name: '그림 맞추기',
      game: report.gameResults.find(
        (game) =>
          game.gameType.toUpperCase().includes('DOBBLE') ||
          game.gameName.includes('그림 맞추기'),
      ),
    },
    {
      name: '과일 자르기',
      game: report.gameResults.find(
        (game) =>
          game.gameType.toUpperCase().includes('FRUIT') ||
          game.gameName.includes('과일 자르기'),
      ),
    },
  ]

  return (
    <div className="absolute inset-0" style={jua}>
      <header className="absolute top-[6.6%] right-[12%] left-[12%] text-center">
        <h2 className="text-[25px] leading-tight text-[#4f6f35]">
          {formatLongDate(report.reportDate)} 리포트
        </h2>
      </header>

      {report.status === 'FAILED' ? (
        <div className="absolute top-[22%] right-[13%] left-[13%] flex min-h-[230px] flex-col items-center justify-center text-center text-[19px] text-[#6a5a3a]">
          <p>{report.failure?.message || '리포트를 생성하지 못했어요.'}</p>
          {report.failure?.canRegenerate ? (
            <button
              className="mt-4 rounded-2xl bg-[#789c4a] px-6 py-2.5 text-[17px] text-white shadow-md disabled:opacity-50"
              disabled={isRetrying}
              onClick={onRetry}
              type="button"
            >
              {isRetrying ? '요청 중...' : '리포트 다시 생성하기'}
            </button>
          ) : null}
          {retryMessage ? (
            <p className="mt-3 text-[15px]">{retryMessage}</p>
          ) : null}
        </div>
      ) : report.status === 'PENDING' || report.status === 'PROCESSING' ? (
        <div className="absolute top-[22%] right-[13%] left-[13%] flex min-h-[230px] items-center justify-center text-center text-[19px] text-[#6a5a3a]">
          {retryMessage ||
            (report.status === 'PENDING'
              ? '리포트 생성을 기다리고 있어요.'
              : '리포트를 만들고 있어요.')}
        </div>
      ) : (
        <>
          {hasGames ? (
            <section className="absolute top-[15.9%] right-[8%] left-[8%] h-[34.5%] text-[#675636]">
              <h3 className="absolute top-0.5 -left-3 flex h-[11.5%] w-[31%] min-w-[230px] items-center justify-center px-3 text-[20px] text-[#55783b]">
                {childName}의 놀이 요약
              </h3>
              <div className="absolute top-[14.5%] right-0 bottom-0 left-0 grid grid-cols-2 items-stretch gap-3 overflow-hidden px-5 py-5">
                {gameSlots.map(({ game, name }) => (
                  <article
                    className="flex h-full flex-col justify-center rounded-[18px] bg-[rgba(242,233,206,.72)] px-6 py-5"
                    key={name}
                  >
                    <h3 className="text-[22px] text-[#5a4a2a]">{name}</h3>
                    {game ? (
                      <>
                        <p className="mt-2 text-[17px]">
                          플레이 시간 {formatDuration(game.playDurationSeconds)}
                        </p>
                        <p className="text-[17px]">
                          점수 {game.score}점 · 완료
                        </p>
                        {metricEntries(game.resultMetrics).map(
                          ([key, value]) => (
                            <p className="text-[15px] text-[#85744f]" key={key}>
                              {formatMetricKey(key)} {value}
                            </p>
                          ),
                        )}
                      </>
                    ) : (
                      <p className="mt-3 text-[16px] leading-6 text-[#85744f]">
                        오늘 이 게임을 플레이하지 않았어요.
                      </p>
                    )}
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          {hasConversation ? (
            <section className="absolute top-[61%] right-[8%] left-[8%] h-[32.3%] text-[#675636]">
              <h3 className="absolute top-0 left-0 flex h-[12.5%] w-[43.5%] min-w-[300px] items-center justify-center px-3 text-[20px] text-[#55783b]">
                {childName}와 캐릭터가 한 대화 요약
              </h3>
              <div className="absolute top-[16%] right-0 bottom-0 left-0 flex flex-col overflow-hidden rounded-[18px] bg-[rgba(248,242,222,.58)] px-7 py-6">
                <h4 className="text-[20px] text-[#5a4a2a]">
                  {report.conversationSummary!.diaryTitle}
                </h4>
                <p className="mt-2 min-h-0 flex-1 text-[15px] leading-6 whitespace-pre-line">
                  {report.conversationSummary!.diaryContent}
                </p>
                <div className="grid min-h-[104px] grid-cols-3 items-stretch gap-3">
                  <PaperSummaryList
                    label="주요 사건"
                    values={report.conversationSummary!.mainEvents}
                  />
                  <PaperSummaryList
                    label="감정"
                    values={report.conversationSummary!.expressedEmotions}
                  />
                  <PaperSummaryList
                    label="관심사"
                    values={report.conversationSummary!.interests}
                  />
                </div>
              </div>
            </section>
          ) : null}

          {!hasGames && !hasConversation ? (
            <div className="absolute top-[22%] right-[13%] left-[13%] text-center text-[19px] text-[#6a5a3a]">
              표시할 리포트 내용이 없어요.
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}

function PaperSummaryList({
  label,
  values,
}: {
  label: string
  values: string[]
}) {
  return (
    <div className="h-full rounded-xl bg-[rgba(233,241,216,.82)] px-3 py-2">
      <h4 className="text-[14px] text-[#55783b]">{label}</h4>
      <p className="mt-0.5 truncate text-[12px]">
        {values.length ? `• ${values.join(', ')}` : '기록 없음'}
      </p>
    </div>
  )
}

export function ReportContent({
  isRetrying,
  onRetry,
  report,
  retryMessage,
}: {
  isRetrying: boolean
  onRetry: () => void
  report: ReportDetailResponse
  retryMessage: string
}) {
  if (report.status === 'FAILED') {
    return (
      <Message>
        {report.failure?.message || '리포트를 생성하지 못했어요.'}
        {report.failure?.canRegenerate ? (
          <button
            className="mt-5 block rounded-2xl bg-[#789c4a] px-7 py-3 text-[19px] text-white shadow-md disabled:opacity-50"
            disabled={isRetrying}
            onClick={onRetry}
            type="button"
          >
            {isRetrying ? '요청 중...' : '리포트 다시 생성하기'}
          </button>
        ) : null}
        {retryMessage ? (
          <span className="mt-3 block text-[16px]">{retryMessage}</span>
        ) : null}
      </Message>
    )
  }
  if (report.status === 'PENDING' || report.status === 'PROCESSING') {
    return (
      <Message>
        {retryMessage ||
          (report.status === 'PENDING'
            ? '리포트 생성을 기다리고 있어요.'
            : '리포트를 만들고 있어요.')}
      </Message>
    )
  }

  const hasGames =
    report.dataAvailability.hasGameData && report.gameResults.length > 0
  const hasConversation =
    report.dataAvailability.hasConversationData && report.conversationSummary

  return (
    <div className="space-y-8" style={jua}>
      {hasGames ? <GameSection games={report.gameResults} /> : null}
      {hasConversation ? (
        <ConversationSection summary={report.conversationSummary!} />
      ) : null}
      {!hasGames && !hasConversation ? (
        <Message>표시할 리포트 내용이 없어요.</Message>
      ) : null}
    </div>
  )
}

function GameSection({ games }: { games: ReportGameResult[] }) {
  return (
    <section className="rounded-[24px] border-2 border-[#decda1] bg-[rgba(255,252,239,.82)] p-5">
      <h2 className="mb-3 text-[25px] text-[#55783b]">게임 결과</h2>
      <div className="grid grid-cols-2 gap-3">
        {games.map((game) => (
          <article
            className="rounded-2xl bg-[#f5ecd3] p-4 text-[#675636]"
            key={game.gameSessionId}
          >
            <h3 className="text-[21px] text-[#5a4a2a]">{game.gameName}</h3>
            <p className="mt-1 text-[16px]">
              플레이 시간 {formatDuration(game.playDurationSeconds)}
            </p>
            <p className="text-[16px]">점수 {game.score}점 · 완료</p>
            {metricEntries(game.resultMetrics).map(([key, value]) => (
              <p className="text-[14px] text-[#85744f]" key={key}>
                {formatMetricKey(key)} {value}
              </p>
            ))}
          </article>
        ))}
      </div>
    </section>
  )
}

function ConversationSection({
  summary,
}: {
  summary: NonNullable<ReportDetailResponse['conversationSummary']>
}) {
  return (
    <section className="rounded-[24px] border-2 border-[#decda1] bg-[rgba(255,252,239,.82)] p-5 text-[#675636]">
      <h2 className="text-[25px] text-[#55783b]">대화 기반 일기 요약</h2>
      <h3 className="mt-2 text-[22px] text-[#5a4a2a]">{summary.diaryTitle}</h3>
      <p className="mt-2 text-[17px] leading-7 whitespace-pre-line">
        {summary.diaryContent}
      </p>
      <div className="mt-4 grid grid-cols-3 gap-3">
        <SummaryList label="주요 사건" values={summary.mainEvents} />
        <SummaryList label="감정" values={summary.expressedEmotions} />
        <SummaryList label="관심사" values={summary.interests} />
      </div>
    </section>
  )
}

function SummaryList({ label, values }: { label: string; values: string[] }) {
  return (
    <div className="rounded-2xl bg-[#eef3df] p-3">
      <h4 className="text-[17px] text-[#55783b]">{label}</h4>
      {values.length ? (
        values.map((value) => (
          <p className="mt-1 text-[14px]" key={value}>
            • {value}
          </p>
        ))
      ) : (
        <p className="mt-1 text-[14px] text-[#95876b]">기록 없음</p>
      )}
    </div>
  )
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex min-h-[380px] flex-col items-center justify-center text-center text-[22px] text-[#6a5a3a]"
      style={jua}
    >
      {children}
    </div>
  )
}

function formatLongDate(date: string) {
  const [, month, day] = date.split('-')
  return month && day ? `${Number(month)}월 ${Number(day)}일` : date
}

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return minutes ? `${minutes}분 ${rest}초` : `${rest}초`
}

function metricEntries(metrics: Record<string, unknown> | null) {
  if (!metrics) return []
  return Object.entries(metrics)
    .filter(([key]) => key !== 'rulesVersion')
    .filter(([, value]) =>
      ['string', 'number', 'boolean'].includes(typeof value),
    )
    .slice(0, 3)
    .map(([key, value]) => [key, String(value)] as const)
}

function formatMetricKey(key: string) {
  const labels: Record<string, string> = {
    solvedCardCount: '맞힌 카드',
    correctCount: '정답',
    wrongCount: '오답',
    slicedFruitCount: '자른 과일',
    totalFruitCount: '전체 과일 수',
  }
  return `${labels[key] ?? key}:`
}
