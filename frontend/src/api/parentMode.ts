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

export type ReportGameResult = {
  gameSessionId: string
  gameType: string
  gameName: string
  playDurationSeconds: number
  score: number
  resultMetrics: Record<string, unknown> | null
  completedAt: string
}

export type ReportConversationSummary = {
  diaryTitle: string
  diaryContent: string
  mainEvents: string[]
  expressedEmotions: string[]
  interests: string[]
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
  gameResults: ReportGameResult[]
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
  if (status >= 500) return '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'
  if (status === 0) return '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'
  return '리포트를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

async function reportFetch(input: RequestInfo | URL, init?: RequestInit) {
  try {
    return await authFetch(input, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ReportApiError(0)
  }
}

async function requireReportResponse(response: Response) {
  if (response.ok) return
  const body = (await response.json().catch(() => null)) as ReportApiErrorBody | null
  if (response.status === 401) clearLoginSession()
  throw new ReportApiError(response.status, body?.code)
}

export const PARENT_MODE_API_CAPABILITIES = {
  childReportQuery: true,
  reportNotificationStream: true,
  parentPhotoUpdate: false,
  parentPinUpdate: false,
  parentPasswordUpdate: false,
  reportNotificationPreferenceUpdate: false,
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

export async function subscribeToReportNotifications(
  onEvent: (event: ReportNotificationEvent) => void,
  signal: AbortSignal,
) {
  const response = await reportFetch(apiUrl('/api/notifications/stream'), {
    headers: {
      Accept: 'text/event-stream',
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
      (value): value is string => typeof value === 'string' && value.trim().length > 0,
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
