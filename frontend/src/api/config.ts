const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const API_BASE_URL = (
  configuredApiBaseUrl || (import.meta.env.DEV ? 'http://localhost:8080' : '')
).replace(/\/$/, '')

export function apiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}
