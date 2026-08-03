import { authFetch, clearLoginSession } from '@/api/auth'
import { apiUrl } from '@/api/config'
export { getStoredAccessToken } from '@/api/auth'

export type ReportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type ReportListItem = {
  reportId: number
  reportDate: string
  status: ReportStatus
  dataAvailability: {
    hasGameData: boolean
    hasConversationData: boolean
  }
  generatedAt: string | null
  createdAt: string
  failure?: {
    code: string
    message: string
    retryCount: number
    canRegenerate: boolean
    failedAt: string
  } | null
}

/** 판 하나의 기록. 회차별 상세가 필요할 때만 쓴다. */
export type ReportGameResult = {
  gameSessionId: string
  gameType: string
  gameName: string
  playDurationSeconds: number
  score: number
  resultMetrics: Record<string, unknown> | null
  completedAt: string
}

/*
  그날 한 게임을 묶은 기록. 화면에 내보이는 대표 값이다.

  여러 판을 해도 게임마다 한 건이고, 묶는 일은 서버가 한다. 누적 점수는 오지
  않는다 — 여러 판 하면 무조건 커지는 값이라 아이 상태를 나타내지 못한다.
*/
export type ReportGameSummary = {
  gameType: string
  gameName: string
  /** 그날 몇 판 했는지 */
  playCount: number
  /** 플레이 시간 누적 */
  totalPlayDurationSeconds: number
  highestScore: number
  /** 게임별 누적 결과 */
  resultMetrics: Record<string, unknown> | null
}

/** 아이가 보인 감정 하나와 그 까닭 */
export type ReportEmotionDetail = {
  emotion: string
  reason: string
}

/**
 * 부모가 아이와 이어서 나눌 이야깃거리.
 *
 * 제목은 서버가 보내지 않는다. 다른 섹션과 마찬가지로 화면이 정한다.
 */
export type ReportConversationGuide = {
  description?: string | null
  questions?: string[] | null
}

/*
  대화 요약.

  sourceConversationCount 말고는 전부 없을 수 있는 것으로 둔다. 있는 것만 골라
  그리는 일은 화면 쪽에서 한다.
*/
export type ReportConversationSummary = {
  diaryTitle?: string | null
  /** 오늘의 이야기 자리에 그대로 들어간다 */
  diaryContent?: string | null
  mainEvents?: string[] | null
  /** 까닭 없이 이름만 온다 */
  expressedEmotions?: string[] | null
  /** 까닭이 함께 온다 */
  emotionDetails?: ReportEmotionDetail[] | null
  interests?: string[] | null
  /** 아이가 실제로 한 말 */
  notableQuotes?: string[] | null
  conversationGuide?: ReportConversationGuide | null
  sourceConversationCount: number
  generatedAt: string
}

export type ReportDetailResponse = {
  reportId: number
  childId: number
  reportDate: string
  aggregationPeriod: {
    startedAt: string
    endedAt: string
    timezone: string
  }
  status: ReportStatus
  dataAvailability: ReportListItem['dataAvailability']
  /** 회차별 기록. 화면은 gameSummaries를 쓴다. */
  gameResults: ReportGameResult[]
  /** 묶어 보내기 전 서버에는 없던 값이라 없을 수 있다. */
  gameSummaries?: ReportGameSummary[] | null
  conversationSummary: ReportConversationSummary | null
  failure: ReportListItem['failure']
  generatedAt: string | null
  createdAt: string
  updatedAt: string
}

export type ReportRegenerationResponse = {
  reportId: number
  childId: number
  reportDate: string
  status: ReportStatus
  requestedAt: string
  message: string
}

export type ReportListResponse = {
  childId: number
  reports: ReportListItem[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
}

export type ReportNotificationEvent = {
  type: 'REPORT_COMPLETED' | 'REPORT_FAILED'
  id: string
  reportDate: string
  message: string
  childName: string
}

/** `GET /api/notifications/unread`가 돌려주는 미확인 알림 한 건 */
export type ReportNotificationItem = {
  notificationId: number
  reportId: number
  childId: number
  reportDate: string
  type: 'REPORT_COMPLETED' | 'REPORT_FAILED'
  message: string
  availableAt: string
}

export type UnreadReportNotifications = {
  unreadCount: number
  notifications: ReportNotificationItem[]
}

export type ReportNotificationPreference = {
  enabled: boolean
  time: string
  timezone: string
}

type ReportApiErrorBody = {
  code?: string
  message?: string
}

export class ReportApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, code?: string) {
    super(reportErrorMessage(status))
    this.name = 'ReportApiError'
    this.status = status
    this.code = code
  }
}

export function reportErrorMessage(status: number) {
  if (status === 401) return '로그인이 만료되었어요. 다시 로그인해 주세요.'
  if (status === 403) return '이 아이의 리포트를 볼 권한이 없어요.'
  if (status === 404) return '리포트를 찾을 수 없어요.'
  if (status >= 500)
    return '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'
  if (status === 0) return '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'
  return '리포트를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

async function reportFetch(input: RequestInfo | URL, init?: RequestInit) {
  try {
    return await authFetch(input, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError')
      throw error
    throw new ReportApiError(0)
  }
}

async function requireReportResponse(response: Response) {
  if (response.ok) return
  const body = (await response
    .json()
    .catch(() => null)) as ReportApiErrorBody | null
  if (response.status === 401) clearLoginSession()
  throw new ReportApiError(response.status, body?.code)
}

export const PARENT_MODE_API_CAPABILITIES = {
  childReportQuery: true,
  reportNotificationStream: true,
  parentPhotoUpdate: false,
  parentPinUpdate: false,
  parentPasswordUpdate: false,
  reportNotificationPreferenceUpdate: true,
} as const

export function isBackendChildId(id: string) {
  return /^[1-9]\d*$/.test(id)
}

export async function getChildReports(
  childId: number,
  options: {
    page?: number
    size?: number
    startDate?: string
    endDate?: string
    status?: ReportStatus
    signal?: AbortSignal
  } = {},
) {
  const search = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  })
  if (options.startDate) search.set('startDate', options.startDate)
  if (options.endDate) search.set('endDate', options.endDate)
  if (options.status) search.set('status', options.status)
  const response = await reportFetch(
    apiUrl(`/api/children/${childId}/reports?${search}`),
    {
      signal: options.signal,
    },
  )
  await requireReportResponse(response)
  return (await response.json()) as ReportListResponse
}

export async function getChildReport(
  childId: number,
  reportDate: string,
  signal?: AbortSignal,
) {
  const response = await reportFetch(
    apiUrl(`/api/children/${childId}/reports/${reportDate}`),
    { signal },
  )
  await requireReportResponse(response)
  return (await response.json()) as ReportDetailResponse
}

export async function regenerateChildReport(
  childId: number,
  reportDate: string,
) {
  const response = await reportFetch(
    apiUrl(`/api/children/${childId}/reports/${reportDate}/regenerate`),
    {
      method: 'POST',
      headers: {
        'Idempotency-Key': crypto.randomUUID(),
      },
    },
  )
  await requireReportResponse(response)
  return (await response.json()) as ReportRegenerationResponse
}

/**
 * 아직 확인하지 않은 리포트 알림을 서버에서 가져온다.
 *
 * SSE는 부모 모드 화면이 열려 있는 동안에만 구독되므로, 그 밖의 시간에 도착한
 * 알림은 이 조회로만 화면에 올릴 수 있다.
 */
export async function getUnreadReportNotifications(signal?: AbortSignal) {
  const response = await reportFetch(apiUrl('/api/notifications/unread'), {
    signal,
  })
  await requireReportResponse(response)
  return (await response.json()) as UnreadReportNotifications
}

/** 알림 하나를 읽음 처리한다. 목록에서 알림을 직접 눌렀을 때 쓴다. */
export async function markReportNotificationRead(notificationId: number) {
  const response = await reportFetch(
    apiUrl(`/api/notifications/${notificationId}/read`),
    { method: 'PATCH' },
  )
  await requireReportResponse(response)
}

/**
 * 리포트를 열어봤으므로 그 리포트의 알림을 읽음 처리한다.
 *
 * 알림 목록을 거치지 않고 리포트로 바로 들어오는 경로가 있어, 알림 id가 아니라
 * 리포트 id로도 읽음을 반영할 수 있어야 한다.
 */
export async function markReportReadByReportId(reportId: number) {
  const response = await reportFetch(
    apiUrl(`/api/notifications/reports/${reportId}/read`),
    { method: 'PATCH' },
  )
  await requireReportResponse(response)
}

export async function getReportNotificationPreference(signal?: AbortSignal) {
  const response = await reportFetch(apiUrl('/api/notifications/preferences'), {
    signal,
  })
  await requireReportResponse(response)
  return (await response.json()) as ReportNotificationPreference
}

export async function updateReportNotificationPreference(
  preference: ReportNotificationPreference,
) {
  const response = await reportFetch(apiUrl('/api/notifications/preferences'), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(preference),
  })
  await requireReportResponse(response)
  return (await response.json()) as ReportNotificationPreference
}

export async function subscribeToReportNotifications(
  onEvent: (event: ReportNotificationEvent) => void,
  signal: AbortSignal,
  lastEventId?: string,
) {
  const response = await reportFetch(apiUrl('/api/notifications/stream'), {
    headers: {
      Accept: 'text/event-stream',
      ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
    },
    signal,
  })
  await requireReportResponse(response)
  if (!response.body) throw new ReportApiError(0)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (!signal.aborted) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const parsed = parseSseBlock(block)
      if (parsed) onEvent(parsed)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function parseSseBlock(block: string): ReportNotificationEvent | null {
  let id = ''
  let type = ''
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (line.startsWith('id:')) id = line.slice(3).trim()
    if (line.startsWith('event:')) type = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (type !== 'REPORT_COMPLETED' && type !== 'REPORT_FAILED') return null

  try {
    const data = JSON.parse(dataLines.join('\n')) as {
      reportDate?: unknown
      message?: unknown
      childNickname?: unknown
      childName?: unknown
      nickname?: unknown
      name?: unknown
    }
    if (
      typeof data.reportDate !== 'string' ||
      typeof data.message !== 'string'
    ) {
      return null
    }
    const explicitChildName = [
      data.name,
      data.childName,
      data.childNickname,
      data.nickname,
    ].find(
      (value): value is string =>
        typeof value === 'string' && value.trim().length > 0,
    )
    const childName =
      explicitChildName?.trim() ??
      data.message.match(/^(.+?)의 일일 리포트/)?.[1]?.trim() ??
      ''
    return {
      id,
      type,
      reportDate: data.reportDate,
      message: data.message,
      childName,
    }
  } catch {
    return null
  }
}
