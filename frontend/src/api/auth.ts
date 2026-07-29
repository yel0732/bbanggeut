import { apiUrl } from '@/api/config'

export type LoginResponse = {
  parentId: number
  email: string
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export type SignupResponse = {
  parentId: number
  email: string
}

type TokenRefreshResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export type GoogleAuthResponse = {
  status: 'LOGIN_SUCCESS' | 'SIGNUP_REQUIRED' | 'LINK_REQUIRED'
  tokens: LoginResponse | null
  signupToken: string | null
  email: string | null
  linkToken: string | null
}

type ApiErrorResponse = {
  message?: string
}

const SESSION_KEYS = [
  'accessToken',
  'accessTokenExpiresAt',
  'parentId',
  'parentEmail',
] as const
const REFRESH_BUFFER_MS = 60_000
/** 인증 요청 응답 대기 한도(ms) */
const REQUEST_TIMEOUT_MS = 6_000
const NEW_SIGNUP_EMAIL_KEY = 'bbanggut-new-signup-email'
const ONBOARDING_PIN_DRAFT_KEY = 'bbanggut-onboarding-parent-pin-draft'
const ONBOARDING_PIN_SAVED_KEY = 'bbanggut-onboarding-parent-pin-saved'
const ACCOUNT_LOCAL_DATA_KEYS = [
  'bbanggut-active-child-profile-id',
  'bbanggut-child-profiles',
  'bbanggut-parent-photo',
  'bbanggut-report-arrivals',
  'bbanggut-report-notification',
] as const
let refreshPromise: Promise<string | null> | null = null

export async function loginWithEmail({
  autoLogin,
  email,
  password,
}: {
  autoLogin: boolean
  email: string
  password: string
}) {
  return requestJson<LoginResponse>('/api/auth/login', {
    body: JSON.stringify({ email, password, autoLogin }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export async function checkEmailAvailability(email: string) {
  return requestJson<{ available: boolean }>(
    `/api/auth/email/check?email=${encodeURIComponent(email)}`,
    { method: 'GET' },
  )
}

export async function signupWithEmail({
  birthDate,
  email,
  password,
}: {
  birthDate: string
  email: string
  password: string
}) {
  return requestJson<SignupResponse>('/api/auth/signup', {
    body: JSON.stringify({ birthDate, email, password }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export function storeLoginSession(response: LoginResponse, remember: boolean) {
  clearLoginSession()
  const storage = remember ? window.localStorage : window.sessionStorage
  storeAccessToken(response, storage)
}

export function markNewSignup(email: string) {
  // A previous, unfinished signup must never carry its PIN into this account.
  window.sessionStorage.removeItem(ONBOARDING_PIN_DRAFT_KEY)
  window.sessionStorage.removeItem(ONBOARDING_PIN_SAVED_KEY)
  window.sessionStorage.setItem(
    NEW_SIGNUP_EMAIL_KEY,
    email.trim().toLowerCase(),
  )
}

export function consumeNewSignup(email: string) {
  const normalizedEmail = email.trim().toLowerCase()
  const signupEmail = window.sessionStorage.getItem(NEW_SIGNUP_EMAIL_KEY)
  return signupEmail === normalizedEmail
}

export function hasPendingNewSignupOnboarding() {
  return Boolean(window.sessionStorage.getItem(NEW_SIGNUP_EMAIL_KEY))
}

export function completeNewSignupOnboarding() {
  window.sessionStorage.removeItem(NEW_SIGNUP_EMAIL_KEY)
  window.sessionStorage.removeItem(ONBOARDING_PIN_DRAFT_KEY)
  window.sessionStorage.removeItem(ONBOARDING_PIN_SAVED_KEY)
}

export function getStoredAccessToken() {
  return getSessionStorage()?.getItem('accessToken') ?? null
}

export async function restoreSession() {
  const storage = getSessionStorage()
  const accessToken = storage?.getItem('accessToken')
  const expiresAt = Number(storage?.getItem('accessTokenExpiresAt'))

  if (
    accessToken &&
    Number.isFinite(expiresAt) &&
    expiresAt > Date.now() + REFRESH_BUFFER_MS
  ) {
    return true
  }

  return Boolean(await refreshAccessToken())
}

export async function authFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
) {
  const accessToken = await ensureAccessToken()
  const firstResponse = await fetchWithToken(input, init, accessToken)
  if (firstResponse.status !== 401) return firstResponse

  const refreshedToken = await refreshAccessToken()
  if (!refreshedToken) return firstResponse
  return fetchWithToken(input, init, refreshedToken)
}

export async function googleAuthenticate(idToken: string) {
  return requestJson<GoogleAuthResponse>('/api/auth/google', {
    body: JSON.stringify({ idToken }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export async function completeGoogleSignup(
  signupToken: string,
  birthDate: string,
) {
  await requestJson('/api/auth/google/complete', {
    body: JSON.stringify({ signupToken, birthDate }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export async function linkGoogleAccount(linkToken: string, password: string) {
  return requestJson<LoginResponse>('/api/auth/google/link', {
    body: JSON.stringify({ linkToken, password }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
}

export async function logout() {
  const accessToken = await ensureAccessToken()

  if (!accessToken) {
    clearLoginSession()
    return
  }

  const response = await fetch(apiUrl('/api/auth/logout'), {
    credentials: 'include',
    headers: { Authorization: `Bearer ${accessToken}` },
    method: 'POST',
  })

  if (!response.ok && response.status !== 401) {
    const error = (await response
      .json()
      .catch(() => null)) as ApiErrorResponse | null
    throw new Error(
      error?.message ?? '로그아웃에 실패했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  clearLoginSession()
}

export async function verifyParentPin(pin: string) {
  const response = await authFetch(apiUrl('/api/profile/pin/verify'), {
    body: JSON.stringify({ pin }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })
  if (!response.ok) {
    const error = (await response
      .json()
      .catch(() => null)) as ApiErrorResponse | null
    throw new Error(error?.message ?? '핀 번호가 올바르지 않아요.')
  }
}

export async function withdrawAccount(pin: string) {
  const response = await authFetch(apiUrl('/api/auth/me'), {
    body: JSON.stringify({ pin }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'DELETE',
  })
  if (!response.ok) {
    const error = (await response
      .json()
      .catch(() => null)) as ApiErrorResponse | null
    throw new Error(
      error?.message ?? '회원 탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요.',
    )
  }
  clearLoginSession()
  ACCOUNT_LOCAL_DATA_KEYS.forEach((key) => window.localStorage.removeItem(key))
  window.sessionStorage.removeItem(NEW_SIGNUP_EMAIL_KEY)
}

export async function changeAccountPassword(
  currentPassword: string,
  newPassword: string,
) {
  const response = await authFetch(apiUrl('/api/auth/password'), {
    body: JSON.stringify({
      currentPassword,
      newPassword,
      newPasswordConfirm: newPassword,
    }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'PATCH',
  })
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as ApiErrorResponse | null
    throw new Error(error?.message ?? '비밀번호를 변경하지 못했어요.')
  }
}

export function clearLoginSession() {
  for (const storage of [window.localStorage, window.sessionStorage]) {
    SESSION_KEYS.forEach((key) => storage.removeItem(key))
  }
}

async function ensureAccessToken() {
  const storage = getSessionStorage()
  const accessToken = storage?.getItem('accessToken') ?? null
  const expiresAt = Number(storage?.getItem('accessTokenExpiresAt'))
  if (
    accessToken &&
    Number.isFinite(expiresAt) &&
    expiresAt > Date.now() + REFRESH_BUFFER_MS
  ) {
    return accessToken
  }
  return refreshAccessToken()
}

function refreshAccessToken() {
  if (refreshPromise) return refreshPromise

  refreshPromise = requestJson<TokenRefreshResponse>(
    '/api/auth/token/refresh',
    {
      credentials: 'include',
      method: 'POST',
    },
  )
    .then((response) => {
      const storage = getSessionStorage() ?? window.localStorage
      storage.setItem('accessToken', response.accessToken)
      storage.setItem(
        'accessTokenExpiresAt',
        String(Date.now() + response.expiresInSeconds * 1000),
      )
      return response.accessToken
    })
    .catch(() => {
      clearLoginSession()
      return null
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

function getSessionStorage() {
  if (window.localStorage.getItem('accessToken')) return window.localStorage
  if (window.sessionStorage.getItem('accessToken')) return window.sessionStorage
  return null
}

function storeAccessToken(response: LoginResponse, storage: Storage) {
  storage.setItem('accessToken', response.accessToken)
  storage.setItem(
    'accessTokenExpiresAt',
    String(Date.now() + response.expiresInSeconds * 1000),
  )
  storage.setItem('parentId', String(response.parentId))
  storage.setItem('parentEmail', response.email)
}

function fetchWithToken(
  input: RequestInfo | URL,
  init: RequestInit,
  accessToken: string | null,
) {
  const headers = new Headers(init.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  return fetch(input, { ...init, headers })
}

async function requestJson<T = unknown>(path: string, init: RequestInit) {
  // 응답이 안 오면 무한정 기다린다. 세션 확인처럼 화면 전환을 막고 있는 요청이
  // 걸리면 아이 눈에는 버튼이 먹통이 된 것으로 보인다.
  const response = await fetch(apiUrl(path), {
    ...init,
    signal: init.signal ?? AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
  const contentType = response.headers.get('content-type') ?? ''
  if (!response.ok) {
    const error = contentType.includes('application/json')
      ? ((await response.json().catch(() => null)) as ApiErrorResponse | null)
      : null
    throw new Error(error?.message ?? `요청에 실패했어요. (${response.status})`)
  }
  if (!contentType.includes('application/json')) {
    throw new Error(
      '서버가 올바른 응답을 보내지 않았어요. API 주소를 확인해 주세요.',
    )
  }
  return (await response.json()) as T
}
