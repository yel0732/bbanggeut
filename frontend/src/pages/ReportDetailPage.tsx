import { useEffect, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import {
  getChildReport,
  isBackendChildId,
  markReportReadByReportId,
  regenerateChildReport,
} from '@/api/parentMode'
import type { ReportDetailResponse, ReportGameResult } from '@/api/parentMode'
import backgroundUrl from '@/assets/images/parent-mode/background.webp'
import backButtonUrl from '@/assets/images/report/left-btn.webp'
import reportPaperUrl from '@/assets/images/report/report-paper.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { ConversationSummaryView } from '@/features/reports/ConversationSummaryView'
import {
  toConversationView,
  type ReportConversationView,
} from '@/features/reports/conversationSummary'
import { useFitScale } from '@/hooks/useFitScale'
import { useParentBgm } from '@/hooks/useParentBgm'
import { removeReadReportArrival } from '@/lib/reportArrivals'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function ReportDetailPage() {
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useParentBgm()
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
      if (!childId || !reportDate || !isBackendChildId(childId)) {
        setError('리포트를 찾기 위한 정보가 부족해요.')
        setIsLoading(false)
        return
      }
      try {
        const nextReport = await getChildReport(
          Number(childId),
          reportDate,
          controller.signal,
        )
        setReport(nextReport)
        removeReadReportArrival({ childId, childName, reportDate })
        // 알림 목록을 거치지 않고 리포트로 바로 들어온 경우에도 읽음을 서버에 남긴다.
        void markReportReadByReportId(nextReport.reportId).catch(() => {})
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
  }, [childId, childName, reportDate])

  async function requestRegeneration() {
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
  /*
    대화가 실제로 있었을 때만 대화 자리를 연다.

    서버는 대화가 0건이어도 그날 게임 기록으로 요약 본문을 채워 보낼 때가 있다.
    그대로 그리면 대화 요약 자리에 게임 이야기가 앉는다. 판단은
    toConversationView 한곳에 모아 두 화면이 같게 움직이게 한다.
  */
  const conversation = useMemo(
    () =>
      report.status === 'COMPLETED' &&
      report.dataAvailability.hasConversationData
        ? toConversationView(report.conversationSummary)
        : null,
    [report],
  )
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
          <section className="absolute top-[15.9%] right-[8%] left-[8%] h-[34.5%] text-[#675636]">
            <h3 className="absolute top-0.5 -left-2 flex h-[11.5%] w-[31%] min-w-[230px] items-center justify-center px-3 text-[22px] text-[#55783b]">
              {childName}의 게임 결과
            </h3>
            <div className="absolute top-[14.5%] right-0 bottom-0 left-0 grid grid-cols-2 items-stretch gap-3 overflow-hidden px-5 py-5">
              {hasGames ? (
                gameSlots.map(({ game, name }) => (
                  <article
                    className="flex h-full flex-col justify-start rounded-[18px] bg-[rgba(242,233,206,.72)] px-6 py-5"
                    key={name}
                  >
                    <h3 className="text-[24px] text-[#5a4a2a]">{name}</h3>
                    {game ? (
                      <>
                        <p className="mt-2 text-[19px]">
                          플레이 시간 {formatDuration(game.playDurationSeconds)}
                        </p>
                        <p className="text-[19px]">
                          점수 {game.score}점 · 완료
                        </p>
                        {metricEntries(game.resultMetrics).map(
                          ([key, value]) => (
                            <p className="text-[19px] text-[#5a4a2a]" key={key}>
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
                ))
              ) : (
                <div className="col-span-2 flex items-center justify-center rounded-[18px] bg-[rgba(242,233,206,.72)] px-6 py-5 text-[19px] text-[#85744f]">
                  오늘은 게임을 하지 않았어요.
                </div>
              )}
            </div>
          </section>

          <section className="absolute top-[61%] right-[8%] left-[8%] h-[32.3%] text-[#675636]">
            <h3 className="absolute top-0 -left-1 flex h-[12.5%] w-[43.5%] min-w-[300px] items-center justify-center px-3 text-[22px] text-[#55783b]">
              {childName}와 캐릭터의 대화 요약
            </h3>
            {/* 항목이 여럿이라 종이 안에서 넘칠 수 있다. 이 칸만 스크롤한다. */}
            <div className="absolute top-[16%] right-0 bottom-0 left-0 flex [scrollbar-width:thin] [scrollbar-color:#cdbb92_transparent] flex-col overflow-y-auto rounded-[18px] bg-[rgba(248,242,222,.58)] px-7 py-6">
              {conversation ? (
                <ConversationSummaryView compact view={conversation} />
              ) : (
                <div className="flex flex-1 items-center justify-center text-[25px] text-[#85744f]">
                  오늘은 대화를 나누지 않았어요.
                </div>
              )}
            </div>
          </section>
        </>
      )}
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
  // 판단 근거는 종이 화면과 같다. 두 화면이 다르게 보이면 안 된다.
  const conversation = report.dataAvailability.hasConversationData
    ? toConversationView(report.conversationSummary)
    : null

  return (
    <div className="space-y-8" style={jua}>
      <GameSection games={hasGames ? report.gameResults : []} />
      <ConversationSection view={conversation} />
    </div>
  )
}

function GameSection({ games }: { games: ReportGameResult[] }) {
  return (
    <section className="rounded-[24px] border-2 border-[#decda1] bg-[rgba(255,252,239,.82)] p-5">
      <h2 className="mb-3 text-[25px] text-[#55783b]">게임 결과</h2>
      {games.length ? (
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
      ) : (
        <p className="rounded-2xl bg-[#f5ecd3] p-5 text-[18px] text-[#675636]">
          오늘은 게임을 하지 않았어요.
        </p>
      )}
    </section>
  )
}

function ConversationSection({
  view,
}: {
  view: ReportConversationView | null
}) {
  return (
    <section className="rounded-[24px] border-2 border-[#decda1] bg-[rgba(255,252,239,.82)] p-5 text-[#675636]">
      <h2 className="mb-3 text-[25px] text-[#55783b]">대화 요약</h2>
      {view ? (
        <ConversationSummaryView view={view} />
      ) : (
        <p className="mt-3 rounded-2xl bg-[#eef3df] p-5 text-[18px] text-[#675636]">
          오늘은 대화를 나누지 않았어요.
        </p>
      )}
    </section>
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

/*
  게임 기록 항목 이름.

  부모가 보는 화면이라 영어 필드명이 그대로 나오면 안 된다. 서버가 보내는 키를
  모두 적어 둔다 — 여기 없는 키는 화면에서 아예 뺀다. 뜻을 모르는 값을 영어로
  보여 주느니 안 보여 주는 편이 낫고, 새 게임이 붙어 항목이 빠지면 이 목록에
  한 줄 더하면 된다.
*/
const METRIC_LABELS: Record<string, string> = {
  // 그림 맞추기
  solvedCardCount: '맞힌 카드',
  perfectCount: '아주 잘한 카드',
  excellentCount: '잘한 카드',
  goodCount: '해낸 카드',
  incompleteCardCount: '못 맞힌 카드',
  // 과일 자르기
  totalFruitCount: '전체 과일 수',
  slicedFruitCount: '자른 과일',
  // 숲 속 청소하기
  successfulCount: '알맞게 넣은 쓰레기',
  wrongCount: '잘못 넣은 쓰레기',
  // 낱말 맞추기
  solvedWordCount: '맞힌 낱말',
  incompleteWordCount: '못 맞힌 낱말',
  hintUsedCount: '힌트 쓴 횟수',
}

/** 화면에 한 게임당 보여 줄 항목 수. 칸이 좁아 넘치면 카드가 밀린다. */
const MAX_METRICS_PER_GAME = 4

function metricEntries(metrics: Record<string, unknown> | null) {
  if (!metrics) return []
  return Object.entries(metrics)
    .filter(([key]) => key in METRIC_LABELS)
    .filter(([, value]) =>
      ['string', 'number', 'boolean'].includes(typeof value),
    )
    .slice(0, MAX_METRICS_PER_GAME)
    .map(([key, value]) => [key, String(value)] as const)
}

function formatMetricKey(key: string) {
  return `${METRIC_LABELS[key]}:`
}
