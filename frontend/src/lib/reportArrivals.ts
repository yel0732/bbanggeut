export type ReportArrivalNotification = {
  id: string
  /** 서버 알림 id. 읽음 처리를 서버에 반영할 때 쓴다. */
  notificationId?: number
  /** 서버 리포트 id. 리포트를 열어 읽음 처리할 때 쓴다. */
  reportId?: number
  childId?: string
  childName: string
  reportDate: string
  read: boolean
}

export const REPORT_ARRIVALS_KEY = 'bbanggut-report-arrivals'

function normalizeArrival(value: unknown): ReportArrivalNotification | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Partial<ReportArrivalNotification>
  if (
    typeof item.id !== 'string' ||
    typeof item.childName !== 'string' ||
    typeof item.reportDate !== 'string'
  ) {
    return null
  }
  return {
    id: item.id,
    notificationId:
      typeof item.notificationId === 'number' ? item.notificationId : undefined,
    reportId: typeof item.reportId === 'number' ? item.reportId : undefined,
    childId: typeof item.childId === 'string' ? item.childId : undefined,
    childName: item.childName,
    reportDate: item.reportDate,
    read: item.read ?? false,
  }
}

export function loadReportArrivals() {
  try {
    const value = JSON.parse(
      window.localStorage.getItem(REPORT_ARRIVALS_KEY) ?? '[]',
    )
    if (!Array.isArray(value)) return []
    return value
      .map(normalizeArrival)
      .filter((item): item is ReportArrivalNotification => Boolean(item))
      .filter((item) => !item.read)
  } catch {
    return []
  }
}

export function saveReportArrivals(arrivals: ReportArrivalNotification[]) {
  window.localStorage.setItem(
    REPORT_ARRIVALS_KEY,
    JSON.stringify(arrivals.filter((item) => !item.read).slice(0, 30)),
  )
}

export function upsertReportArrival(
  arrivals: ReportArrivalNotification[],
  arrival: ReportArrivalNotification,
) {
  return [
    { ...arrival, read: false },
    ...arrivals.filter((item) => item.id !== arrival.id && !item.read),
  ].slice(0, 30)
}

export function removeReadReportArrival({
  childId,
  childName,
  reportDate,
}: {
  childId?: string
  childName?: string
  reportDate: string
}) {
  const normalizedChildName = childName?.trim().toLocaleLowerCase('ko-KR')
  const next = loadReportArrivals().filter((arrival) => {
    if (arrival.reportDate !== reportDate) return true
    if (childId && arrival.childId === childId) return false
    if (
      normalizedChildName &&
      arrival.childName.trim().toLocaleLowerCase('ko-KR') ===
        normalizedChildName
    ) {
      return false
    }
    return true
  })
  saveReportArrivals(next)
  window.dispatchEvent(new Event('bbanggut-report-arrivals-updated'))
}
