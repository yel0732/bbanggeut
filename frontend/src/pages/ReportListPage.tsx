import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import {
  getChildReport,
  getChildReports,
  isBackendChildId,
  markReportReadByReportId,
  regenerateChildReport,
} from '@/api/parentMode'
import type { ReportDetailResponse, ReportListItem } from '@/api/parentMode'
import backgroundUrl from '@/assets/images/parent-mode/background.webp'
import pagerLeftUrl from '@/assets/images/parent-profile/pager-left.webp'
import pagerPageUrl from '@/assets/images/parent-profile/pager-page.webp'
import { getVisiblePageIndices } from '@/utils/pagination'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import backButtonUrl from '@/assets/images/report/left-btn.webp'
import reportCardUrl from '@/assets/images/report/report-card.webp'
import reportPaperUrl from '@/assets/images/report/report-paper.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { ReportPaperContent } from '@/features/reports/ReportPaperContent'
import { useFitScale } from '@/hooks/useFitScale'
import { useParentBgm } from '@/hooks/useParentBgm'
import { loadChildProfiles } from '@/lib/childProfiles'
import { removeReadReportArrival } from '@/lib/reportArrivals'

const PAGE_SIZE = 4
const jua = { fontFamily: "'Jua', sans-serif" } as const

export function ReportListPage() {
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useParentBgm()
  const [searchParams] = useSearchParams()
  const childId = searchParams.get('childId') ?? ''
  const maxReportDate = useMemo(() => new Date(), [])
  const [dateFilter, setDateFilter] = useState('')
  const [page, setPage] = useState(0)
  const [reports, setReports] = useState<ReportListItem[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedDate, setSelectedDate] = useState(
    () => searchParams.get('reportDate') ?? '',
  )
  const [selectedReport, setSelectedReport] =
    useState<ReportDetailResponse | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [retryMessage, setRetryMessage] = useState('')
  const [isRetrying, setIsRetrying] = useState(false)
  const child = useMemo(
    () => loadChildProfiles().find((profile) => profile.id === childId),
    [childId],
  )
  const childName = child?.name ?? searchParams.get('childName') ?? '아이'
  const childPhoto = child?.photoUrl ?? childAvatarUrl

  useEffect(() => {
    const controller = new AbortController()

    async function loadReports() {
      setIsLoading(true)
      setError('')
      if (!childId || !isBackendChildId(childId)) {
        setReports([])
        setError('아이 프로필 정보가 없어요. 부모 홈에서 다시 선택해 주세요.')
        setIsLoading(false)
        return
      }

      try {
        const response = await getChildReports(Number(childId), {
          page,
          size: PAGE_SIZE,
          startDate: dateFilter || undefined,
          endDate: dateFilter || undefined,
          signal: controller.signal,
        })
        setReports(response.reports)
        setTotalPages(Math.max(1, response.page.totalPages))
      } catch (loadError) {
        if (controller.signal.aborted) return
        setReports([])
        setError(
          loadError instanceof Error
            ? loadError.message
            : '리포트 목록을 불러오지 못했어요.',
        )
      } finally {
        if (!controller.signal.aborted) setIsLoading(false)
      }
    }

    void loadReports()
    return () => controller.abort()
  }, [childId, dateFilter, page])

  useEffect(() => {
    if (!selectedDate) return
    const controller = new AbortController()
    async function loadDetail() {
      setDetailLoading(true)
      setDetailError('')
      setRetryMessage('')
      if (!isBackendChildId(childId)) {
        setDetailError('리포트를 불러오지 못했어요.')
        setDetailLoading(false)
        return
      }
      try {
        const report = await getChildReport(
          Number(childId),
          selectedDate,
          controller.signal,
        )
        setSelectedReport(report)
        removeReadReportArrival({
          childId,
          childName,
          reportDate: selectedDate,
        })
        // 알림 목록을 거치지 않고 리포트로 바로 들어온 경우에도 읽음을 서버에 남긴다.
        void markReportReadByReportId(report.reportId).catch(() => {})
      } catch (loadError) {
        if (controller.signal.aborted) return
        setDetailError(
          loadError instanceof Error
            ? loadError.message
            : '리포트를 불러오지 못했어요.',
        )
      } finally {
        if (!controller.signal.aborted) setDetailLoading(false)
      }
    }
    void loadDetail()
    return () => controller.abort()
  }, [childId, childName, selectedDate])

  async function requestRegeneration() {
    if (!selectedDate) return
    setIsRetrying(true)
    setRetryMessage('')
    try {
      const response = await regenerateChildReport(
        Number(childId),
        selectedDate,
      )
      setRetryMessage(response.message || '리포트 재생성을 요청했어요.')
      setSelectedReport((current) =>
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
          aria-label="부모 홈으로 돌아가기"
          className="absolute top-[25px] left-[25px] z-30 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
          to="/parent"
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

        {!selectedDate ? (
          <h1
            className="absolute top-[34px] right-0 left-0 z-5 text-center text-[40px] text-white [-webkit-text-stroke:6px_#3c7d38] [paint-order:stroke_fill] text-shadow-[0_4px_10px_rgba(0,0,0,.25)]"
            style={jua}
          >
            {childName}의 리포트
          </h1>
        ) : null}
        <div
          className="absolute top-[100px] left-[125px] z-10 h-11 w-[230px] rounded-[14px] border-[3px] border-[#cdbb92] bg-[#fdf6e3] px-2"
          style={jua}
        >
          <span className="sr-only">날짜로 리포트 찾기</span>
          <BirthDatePicker
            dialogLabel="리포트 날짜 선택"
            headerLabel="리포트 날짜"
            maxDate={maxReportDate}
            onChange={(value) => {
              setDateFilter(value)
              setPage(0)
            }}
            placeholder="연도 / 월 / 일"
            value={dateFilter}
          />
        </div>

        <main
          className={
            !isLoading && !error && reports.length > 0
              ? `absolute top-[125px] bottom-[74px] left-[68px] z-5 flex flex-col overflow-hidden transition-[right] duration-300 ${selectedDate ? 'right-[760px]' : 'right-[96px]'}`
              : /*
                  상세 패널이 열려 있으면 목록이 차지할 수 있는 폭도 그만큼 줄어든다.
                  안내 문구만 남는 상태에서도 마찬가지다 — 전체 폭으로 두면 문구가
                  패널 아래에 깔려 가운데가 아닌 자리에 놓인 것처럼 보인다.
                */
                `absolute top-[165px] bottom-[145px] left-[120px] z-5 flex flex-col overflow-hidden ${selectedDate ? 'right-[760px]' : 'right-[120px]'}`
          }
        >
          {isLoading ? (
            <StatusMessage>리포트를 불러오고 있어요...</StatusMessage>
          ) : error ? (
            <StatusMessage>{error}</StatusMessage>
          ) : reports.length === 0 ? (
            <StatusMessage>
              {dateFilter
                ? '선택한 날짜에 리포트가 없어요.'
                : '아직 리포트가 없어요.'}
            </StatusMessage>
          ) : (
            <div className="flex flex-col pl-[80px]">
              {reports.map((report) => (
                <button
                  className="relative -mb-20 h-[180px] w-full shrink-0 text-left transition hover:translate-x-1"
                  key={report.reportId}
                  onClick={() => setSelectedDate(report.reportDate)}
                  type="button"
                >
                  <img
                    alt=""
                    className="pointer-events-none absolute inset-0 size-full object-fill"
                    src={reportCardUrl}
                  />
                  <img
                    alt=""
                    className="absolute top-1/2 left-[-20px] z-2 size-[84px] -translate-y-1/2 rounded-full bg-[#eaf4d8] object-cover shadow-[0_0_0_4px_#f5edcf,0_0_0_7px_#d9c79a]"
                    src={childPhoto}
                  />
                  <div
                    className={`absolute inset-0 z-1 ${selectedDate ? 'flex flex-col items-start justify-center gap-[5px] pr-[46px] pl-[80px]' : 'flex items-center gap-2 pr-[30px] pl-[80px]'}`}
                    style={jua}
                  >
                    {selectedDate ? (
                      <>
                        <span className="shrink-0 text-[21px] leading-none text-[#5a4a2a]">
                          {childName} · {formatDate(report.reportDate)}
                        </span>
                        {report.status === 'COMPLETED' ? (
                          <span className="truncate text-[17px] leading-none text-[#7a6a4a]">
                            {[
                              report.dataAvailability.hasConversationData
                                ? '대화 일기'
                                : null,
                              report.dataAvailability.hasGameData
                                ? '게임 결과'
                                : null,
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          </span>
                        ) : (
                          <span
                            className={`rounded-full px-3 py-1 text-[16px] ${statusStyle(report.status)}`}
                          >
                            {statusLabel(report.status)}
                          </span>
                        )}
                      </>
                    ) : (
                      <>
                        <span className="shrink-0 text-[24px] text-[#5a4a2a]">
                          {formatDate(report.reportDate)}
                        </span>
                        <span className="text-[#b8a778]">·</span>
                        {report.status === 'COMPLETED' ? (
                          <span className="text-[20px] text-[#7a6a4a]">
                            {[
                              report.dataAvailability.hasConversationData
                                ? '대화 일기'
                                : null,
                              report.dataAvailability.hasGameData
                                ? '게임 결과'
                                : null,
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          </span>
                        ) : (
                          <span
                            className={`rounded-full px-3 py-1 text-[17px] ${statusStyle(report.status)}`}
                          >
                            {statusLabel(report.status)}
                          </span>
                        )}
                      </>
                    )}
                  </div>
                </button>
              ))}
            </div>
          )}
        </main>

        {!selectedDate && !isLoading && !error && totalPages > 1 ? (
          <nav
            aria-label="리포트 목록 페이지"
            className="absolute right-[120px] bottom-[90px] left-[120px] z-6 flex items-center justify-center gap-2"
          >
            <PagerButton
              disabled={page === 0}
              image={pagerLeftUrl}
              label="이전 페이지"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            />
            {getVisiblePageIndices(page, totalPages).map((index) => (
              <button
                aria-current={index === page ? 'page' : undefined}
                className={`size-14 bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#3a7d2c] ${index === page ? 'opacity-100' : 'opacity-55'}`}
                key={index}
                onClick={() => setPage(index)}
                style={{ ...jua, backgroundImage: `url(${pagerPageUrl})` }}
                type="button"
              >
                {index + 1}
              </button>
            ))}
            <PagerButton
              disabled={page >= totalPages - 1}
              image={pagerRightUrl}
              label="다음 페이지"
              onClick={() =>
                setPage((current) => Math.min(totalPages - 1, current + 1))
              }
            />
          </nav>
        ) : null}

        {selectedDate ? (
          <aside className="absolute top-0 right-[42px] bottom-1 z-50 w-[714px] [scrollbar-width:thin] [scrollbar-color:#cdbb92_transparent] overflow-x-hidden overflow-y-auto pt-[40px]">
            {/*
              아래 여백을 당겨, 내용이 끝난 뒤 빈 종이만 따라 내려가는 구간을
              줄인다. 위 여백은 스크롤 영역(aside)의 padding이 맡는다 — 이 칸은
              리포트 본문이 inset-0으로 기준 삼는 상자라 크기를 건드리면 안쪽
              배치가 통째로 어긋난다.
            */}
            <div className="relative mb-[-72px] w-full">
              <img
                alt=""
                className="pointer-events-none block h-auto w-full"
                src={reportPaperUrl}
              />
              <button
                aria-label="리포트 상세 닫기"
                className="absolute top-[24px] right-0 z-10 size-[62px] bg-transparent p-0 transition hover:scale-105"
                onClick={() => {
                  setSelectedDate('')
                  setSelectedReport(null)
                  setDetailError('')
                }}
                type="button"
              >
                <img
                  alt=""
                  className="size-full object-contain"
                  src={closeButtonUrl}
                />
              </button>
              {detailLoading ? (
                <div className="absolute top-[22%] right-[12%] left-[12%]">
                  {/*
                    목록과 상세는 각자 따로 불러온다. 알림을 눌러 들어오면 둘이
                    동시에 로딩 중일 수 있어, 같은 문구를 쓰면 화면에 똑같은 말이
                    두 군데 뜬다.
                  */}
                  <DetailMessage>리포트를 여는 중이에요...</DetailMessage>
                </div>
              ) : detailError ? (
                <div className="absolute top-[22%] right-[12%] left-[12%]">
                  <DetailMessage>{detailError}</DetailMessage>
                </div>
              ) : selectedReport ? (
                <ReportPaperContent
                  childName={childName}
                  isRetrying={isRetrying}
                  onRetry={() => void requestRegeneration()}
                  report={selectedReport}
                  retryMessage={retryMessage}
                />
              ) : null}
            </div>
            <div aria-hidden="true" className="h-[120px]" />
          </aside>
        ) : null}
      </div>
    </div>
  )
}

function StatusMessage({ children }: { children: string }) {
  return (
    <div
      className="flex h-full items-center justify-center rounded-[30px] bg-[rgba(253,246,227,.74)] px-10 text-center text-[25px] text-[#6a5a3a] shadow-[0_10px_30px_rgba(48,72,36,.18)]"
      style={jua}
    >
      {children}
    </div>
  )
}

function PagerButton({
  disabled,
  image,
  label,
  onClick,
}: {
  disabled: boolean
  image: string
  label: string
  onClick: () => void
}) {
  return (
    <button
      aria-label={label}
      className="h-[50px] w-[76px] bg-contain bg-center bg-no-repeat transition hover:scale-105 disabled:cursor-default disabled:opacity-35 disabled:hover:scale-100"
      disabled={disabled}
      onClick={onClick}
      style={{ backgroundImage: `url(${image})` }}
      type="button"
    />
  )
}

function formatDate(date: string) {
  const [year, month, day] = date.split('-')
  return year && month && day ? `${year.slice(2)}/${month}/${day}` : date
}

function DetailMessage({ children }: { children: string }) {
  return (
    <div
      className="flex min-h-[420px] items-center justify-center text-center text-[21px] text-[#6a5a3a]"
      style={jua}
    >
      {children}
    </div>
  )
}

function statusLabel(status: ReportListItem['status']) {
  return {
    PENDING: '생성 대기',
    PROCESSING: '생성 중',
    COMPLETED: '완료',
    FAILED: '생성 실패',
  }[status]
}

function statusStyle(status: ReportListItem['status']) {
  return status === 'FAILED'
    ? 'bg-[#f7ddd5] text-[#a94e3a]'
    : 'bg-[#e8efd8] text-[#66824b]'
}
