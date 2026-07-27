const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(
  /\/$/,
  '',
)

export type LoginResponse = {
  parentId: number
  email: string
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

type ApiErrorResponse = {
  message?: string
}

export async function loginWithEmail({
  autoLogin,
  email,
  password,
}: {
  autoLogin: boolean
  email: string
  password: string
}) {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    body: JSON.stringify({ email, password, autoLogin }),
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })

  if (!response.ok) {
    const error = (await response
      .json()
      .catch(() => null)) as ApiErrorResponse | null
    throw new Error(
      error?.message ?? '로그인에 실패했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  return (await response.json()) as LoginResponse
}

export function storeLoginSession(response: LoginResponse, remember: boolean) {
  window.localStorage.removeItem('accessToken')
  window.sessionStorage.removeItem('accessToken')
  const storage = remember ? window.localStorage : window.sessionStorage
  storage.setItem('accessToken', response.accessToken)
  storage.setItem('parentId', String(response.parentId))
  storage.setItem('parentEmail', response.email)
}
