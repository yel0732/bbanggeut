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
}

export const PARENT_MODE_API_CAPABILITIES = {
  childReportQuery: true,
  reportNotificationStream: true,
  parentPhotoUpdate: false,
  parentPinUpdate: false,
  parentPasswordUpdate: false,
  reportNotificationPreferenceUpdate: false,
} as const

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(
  /\/$/,
  '',
)

export function getStoredAccessToken() {
  for (const key of ['accessToken', 'authToken', 'token']) {
    const token =
      window.localStorage.getItem(key) ?? window.sessionStorage.getItem(key)
    if (token) return token
  }
  return null
}

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
  const response = await fetch(
    `${API_BASE_URL}/api/children/${childId}/reports?${search}`,
    {
      headers: authorizationHeaders(),
      signal: options.signal,
    },
  )
  if (!response.ok) {
    throw new Error(`리포트 목록을 불러오지 못했어요. (${response.status})`)
  }
  return (await response.json()) as ReportListResponse
}

export async function getChildReport(
  childId: number,
  reportDate: string,
  signal?: AbortSignal,
) {
  const response = await fetch(
    `${API_BASE_URL}/api/children/${childId}/reports/${reportDate}`,
    { headers: authorizationHeaders(), signal },
  )
  if (!response.ok) {
    throw new Error(
      `리포트 상세 내용을 불러오지 못했어요. (${response.status})`,
    )
  }
  return (await response.json()) as ReportDetailResponse
}

export async function regenerateChildReport(
  childId: number,
  reportDate: string,
) {
  const response = await fetch(
    `${API_BASE_URL}/api/children/${childId}/reports/${reportDate}/regenerate`,
    {
      method: 'POST',
      headers: {
        ...authorizationHeaders(),
        'Idempotency-Key': crypto.randomUUID(),
      },
    },
  )
  if (!response.ok) {
    throw new Error(`리포트 재생성을 요청하지 못했어요. (${response.status})`)
  }
  return (await response.json()) as ReportRegenerationResponse
}

export async function subscribeToReportNotifications(
  onEvent: (event: ReportNotificationEvent) => void,
  signal: AbortSignal,
) {
  const response = await fetch(`${API_BASE_URL}/api/notifications/stream`, {
    headers: {
      Accept: 'text/event-stream',
      ...authorizationHeaders(),
    },
    signal,
  })
  if (!response.ok || !response.body) {
    throw new Error(`알림 스트림을 연결하지 못했어요. (${response.status})`)
  }

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

function authorizationHeaders(): HeadersInit {
  const token = getStoredAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
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
    }
    if (
      typeof data.reportDate !== 'string' ||
      typeof data.message !== 'string'
    ) {
      return null
    }
    return {
      id,
      type,
      reportDate: data.reportDate,
      message: data.message,
    }
  } catch {
    return null
  }
}
