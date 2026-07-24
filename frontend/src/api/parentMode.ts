export type ReportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type ReportListItem = {
  reportId: number
  reportDate: string
  status: ReportStatus
  dataAvailability: {
    gameDataAvailable: boolean
    conversationDataAvailable: boolean
  }
  generatedAt: string | null
  createdAt: string
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
    const token = window.localStorage.getItem(key)
    if (token) return token
  }
  return null
}

export function isBackendChildId(id: string) {
  return /^[1-9]\d*$/.test(id)
}

export async function getChildReports(
  childId: number,
  options: { page?: number; size?: number; signal?: AbortSignal } = {},
) {
  const search = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  })
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
