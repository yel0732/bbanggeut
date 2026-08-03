import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import {
  completeGoogleSignup,
  googleAuthenticate,
  linkGoogleAccount,
  markNewSignup,
  storeLoginSession,
} from '@/api/auth'
import { needsParentOnboarding } from '@/api/profiles'
import signupCompleteButtonUrl from '@/assets/images/auth/btn-go-login.webp'
import leftButtonUrl from '@/assets/images/auth/left-btn.webp'
import signupCompleteFrameUrl from '@/assets/images/auth/signup-complete-frame.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'

type GoogleCredentialResponse = { credential: string }
type GoogleButtonConfig = {
  shape?: string
  size?: string
  text?: string
  theme?: string
  width?: number
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: {
            callback: (response: GoogleCredentialResponse) => void
            client_id: string
          }) => void
          renderButton: (
            element: HTMLElement,
            config: GoogleButtonConfig,
          ) => void
        }
      }
    }
  }
}

type Stage = 'connecting' | 'signin' | 'birthdate' | 'complete' | 'link'
type GooglePageLocationState = { googleCredential?: string }

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '1 / 1',
  width: 460,
} as const
const MIN_SIGNUP_AGE = 19
const GOOGLE_SCRIPT_ID = 'google-identity-services'
const DEFAULT_GOOGLE_CLIENT_ID =
  '737186420019-trrstol7c5cvf919t15l4o4g866hpp77.apps.googleusercontent.com'

/*
  새로고침을 견디는 가입 진행 상태.

  구글에서 받은 자격증명은 처리하자마자 history에서 지운다 — 뒤로가기로 이 화면에
  돌아왔을 때 같은 자격증명이 또 실행되면 안 된다. 그래서 생년월일을 입력하던
  사람이 새로고침하면 처음 로그인 화면으로 튕겼다. 서버가 준 가입 토큰만 이 탭에
  남겨 두면 하던 자리로 돌아올 수 있다.

  sessionStorage를 쓴다. 탭을 닫으면 사라지고 탭끼리 섞이지 않는다.

  가입 토큰은 서버에서 10분만 유효하다(백엔드 application.yml의
  signup-token-expiry-minutes). 그래서 받은 시각을 함께 적어 두고, 지났으면 아예
  복원하지 않는다. 복원해 봐야 생년월일을 다시 고른 뒤 "가입 완료"를 누르는
  순간에야 실패해서 더 헷갈린다.

  가입을 마친 뒤의 완료 화면도 같은 방식으로 남긴다. 그 자리에서 새로고침했는데
  처음 로그인 화면이 뜨면 방금 가입한 것이 없던 일이 된 것처럼 보인다. 이때는
  토큰을 저장하지 않는다 — 이미 다 쓴 토큰이다.
*/
const SIGNUP_DRAFT_KEY = 'bbanggut-google-signup-draft'
const SIGNUP_TOKEN_TTL_MS = 10 * 60 * 1000

/** 새로고침을 넘겨 되살릴 수 있는 단계. */
type DraftStage = Extract<Stage, 'birthdate' | 'complete'>

type SignupDraft = {
  birth: string
  email: string
  issuedAt: number
  signupToken: string
  stage: DraftStage
}

/** 저장된 것이 없으면 `none`, 10분이 지났으면 `expired`. */
type RestoredDraft =
  { draft: SignupDraft; kind: 'ready' } | { kind: 'expired' } | { kind: 'none' }

function loadSignupDraft(): RestoredDraft {
  let raw: string | null = null
  try {
    raw = window.sessionStorage.getItem(SIGNUP_DRAFT_KEY)
  } catch {
    return { kind: 'none' }
  }
  if (!raw) return { kind: 'none' }

  let parsed: Partial<SignupDraft> | null = null
  try {
    parsed = JSON.parse(raw) as Partial<SignupDraft>
  } catch {
    clearSignupDraft()
    return { kind: 'none' }
  }

  const stage = parsed?.stage
  const isDraftStage = stage === 'birthdate' || stage === 'complete'
  // 생년월일 단계는 토큰이 있어야 의미가 있고, 완료 단계는 토큰이 필요 없다.
  const hasWhatItNeeds = stage === 'complete' || Boolean(parsed?.signupToken)
  if (
    !isDraftStage ||
    !hasWhatItNeeds ||
    typeof parsed?.issuedAt !== 'number'
  ) {
    clearSignupDraft()
    return { kind: 'none' }
  }
  if (Date.now() - parsed.issuedAt >= SIGNUP_TOKEN_TTL_MS) {
    clearSignupDraft()
    /*
      완료 단계가 오래된 것은 알릴 일이 아니다. 가입은 이미 끝났고 로그인만 남았을
      뿐이라, "시간이 지났다"고 하면 가입이 취소된 것처럼 읽힌다. 조용히 처음
      화면으로 둔다.
    */
    return stage === 'complete' ? { kind: 'none' } : { kind: 'expired' }
  }

  return {
    draft: {
      birth: parsed.birth ?? '',
      email: parsed.email ?? '',
      issuedAt: parsed.issuedAt,
      signupToken: parsed.signupToken ?? '',
      stage,
    },
    kind: 'ready',
  }
}

function saveSignupDraft(draft: SignupDraft) {
  try {
    window.sessionStorage.setItem(SIGNUP_DRAFT_KEY, JSON.stringify(draft))
  } catch {
    // 저장하지 못하면 새로고침 때 복원이 안 될 뿐, 가입 흐름 자체는 그대로다.
  }
}

function clearSignupDraft() {
  try {
    window.sessionStorage.removeItem(SIGNUP_DRAFT_KEY)
  } catch {
    // 지우지 못해도 만료 시각 검사에서 걸러진다.
  }
}

function latestAllowedBirthDate() {
  const today = new Date()
  return new Date(
    today.getFullYear() - MIN_SIGNUP_AGE,
    today.getMonth(),
    today.getDate(),
  )
}

function ageOf(birth: string) {
  if (!birth) return null
  const [year, month, day] = birth.split('-').map(Number)
  const birthDate = new Date(year, month - 1, day)
  const now = new Date()
  let age = now.getFullYear() - birthDate.getFullYear()
  const monthDiff = now.getMonth() - birthDate.getMonth()
  if (
    monthDiff < 0 ||
    (monthDiff === 0 && now.getDate() < birthDate.getDate())
  ) {
    age -= 1
  }
  return age
}

export function GoogleBirthdatePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const location = useLocation()
  const initialCredentialRef = useRef(
    (location.state as GooglePageLocationState | null)?.googleCredential ?? '',
  )
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()
  const googleButtonRef = useRef<HTMLDivElement>(null)
  /*
    새 자격증명을 들고 들어왔다면 이전에 하다 만 가입은 의미가 없다. 남아 있던
    것을 지우고 처음부터 시작한다.
  */
  const restoredRef = useRef<RestoredDraft>(
    initialCredentialRef.current ? { kind: 'none' } : loadSignupDraft(),
  )
  const restored =
    restoredRef.current.kind === 'ready' ? restoredRef.current.draft : null
  /** 가입 토큰을 받은 시각. 생년월일을 고칠 때마다 갱신되면 안 된다. */
  const signupIssuedAtRef = useRef(restored?.issuedAt ?? 0)
  const [stage, setStage] = useState<Stage>(() => {
    if (initialCredentialRef.current) return 'connecting'
    return restored ? restored.stage : 'signin'
  })
  const [birth, setBirth] = useState(restored?.birth ?? '')
  const [password, setPassword] = useState('')
  const [signupToken, setSignupToken] = useState(restored?.signupToken ?? '')
  const [linkToken, setLinkToken] = useState('')
  const [email, setEmail] = useState(restored?.email ?? '')
  const [error, setError] = useState(
    restoredRef.current.kind === 'expired'
      ? '가입을 마칠 시간이 지났어요. 다시 로그인해 주세요.'
      : '',
  )
  const [isBusy, setIsBusy] = useState(false)
  const maxBirthDate = useMemo(latestAllowedBirthDate, [])

  const finishLogin = useCallback(
    async (tokens: Parameters<typeof storeLoginSession>[0]) => {
      clearSignupDraft()
      storeLoginSession(tokens, true, 'google')
      navigate(
        (await needsParentOnboarding()) ? '/profiles/new' : '/profiles',
        {
          replace: true,
        },
      )
    },
    [navigate],
  )

  const handleCredential = useCallback(
    async ({ credential }: GoogleCredentialResponse) => {
      setIsBusy(true)
      setError('')
      try {
        const result = await googleAuthenticate(credential)
        if (result.status === 'LOGIN_SUCCESS' && result.tokens) {
          await finishLogin(result.tokens)
          return
        }
        if (result.status === 'SIGNUP_REQUIRED' && result.signupToken) {
          const issuedAt = Date.now()
          signupIssuedAtRef.current = issuedAt
          saveSignupDraft({
            birth: '',
            email: result.email ?? '',
            issuedAt,
            signupToken: result.signupToken,
            stage: 'birthdate',
          })
          setSignupToken(result.signupToken)
          setEmail(result.email ?? '')
          setStage('birthdate')
          return
        }
        if (result.status === 'LINK_REQUIRED' && result.linkToken) {
          setLinkToken(result.linkToken)
          setStage('link')
          return
        }
        throw new Error('구글 로그인 응답을 확인할 수 없어요.')
      } catch (credentialError) {
        clearSignupDraft()
        setStage('signin')
        setError(
          credentialError instanceof Error
            ? credentialError.message
            : '구글 로그인에 실패했어요.',
        )
      } finally {
        setIsBusy(false)
      }
    },
    [finishLogin],
  )

  useEffect(() => {
    const credential = initialCredentialRef.current
    if (!credential) return
    initialCredentialRef.current = ''
    clearSignupDraft()
    navigate('/auth/google', { replace: true, state: null })
    void handleCredential({ credential })
  }, [handleCredential, navigate])

  /*
    고르는 중인 생년월일도 함께 남긴다. 토큰만 살려 두면 새로고침한 사람이 날짜를
    다시 골라야 한다. 받은 시각은 그대로 두어 10분 시계가 밀리지 않게 한다.
  */
  useEffect(() => {
    if (stage !== 'birthdate' || !signupToken) return
    saveSignupDraft({
      birth,
      email,
      issuedAt: signupIssuedAtRef.current,
      signupToken,
      stage: 'birthdate',
    })
  }, [birth, email, signupToken, stage])

  useEffect(() => {
    if (stage !== 'signin') return
    const clientId =
      import.meta.env.VITE_GOOGLE_CLIENT_ID || DEFAULT_GOOGLE_CLIENT_ID

    let cancelled = false
    const renderGoogleButton = () => {
      if (cancelled || !window.google || !googleButtonRef.current) return
      window.google.accounts.id.initialize({
        callback: (response) => void handleCredential(response),
        client_id: clientId,
      })
      googleButtonRef.current.replaceChildren()
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        shape: 'pill',
        size: 'large',
        text: 'signin_with',
        theme: 'outline',
        width: 280,
      })
    }

    const existingScript = document.getElementById(
      GOOGLE_SCRIPT_ID,
    ) as HTMLScriptElement | null
    if (window.google) {
      renderGoogleButton()
    } else if (existingScript) {
      existingScript.addEventListener('load', renderGoogleButton, {
        once: true,
      })
    } else {
      const script = document.createElement('script')
      script.id = GOOGLE_SCRIPT_ID
      script.async = true
      script.defer = true
      script.src = 'https://accounts.google.com/gsi/client'
      script.addEventListener('load', renderGoogleButton, { once: true })
      script.addEventListener(
        'error',
        () => setError('구글 로그인 화면을 불러오지 못했어요.'),
        { once: true },
      )
      document.head.appendChild(script)
    }

    return () => {
      cancelled = true
      existingScript?.removeEventListener('load', renderGoogleButton)
    }
  }, [handleCredential, stage])

  const completeSignup = async () => {
    const age = ageOf(birth)
    if (!birth || age === null) {
      setError('생년월일을 올바르게 입력해 주세요.')
      return
    }
    if (age < MIN_SIGNUP_AGE) {
      setError('만 19세 이상만 가입할 수 있어요.')
      return
    }

    setIsBusy(true)
    setError('')
    try {
      await completeGoogleSignup(signupToken, birth)
      /*
        다 쓴 토큰은 버리고 완료 화면만 남긴다. 토큰을 그대로 두면 새로고침했을 때
        이미 끝난 생년월일 입력으로 되돌아간다.
      */
      saveSignupDraft({
        birth: '',
        email,
        issuedAt: Date.now(),
        signupToken: '',
        stage: 'complete',
      })
      markNewSignup(email)
      setStage('complete')
    } catch (signupError) {
      setError(
        signupError instanceof Error
          ? signupError.message
          : '구글 회원가입을 완료하지 못했어요.',
      )
    } finally {
      setIsBusy(false)
    }
  }

  const completeLink = async () => {
    if (!password) {
      setError('기존 계정의 비밀번호를 입력해 주세요.')
      return
    }
    setIsBusy(true)
    setError('')
    try {
      const tokens = await linkGoogleAccount(linkToken, password)
      await finishLogin(tokens)
    } catch (linkError) {
      setError(
        linkError instanceof Error
          ? linkError.message
          : '계정을 연결하지 못했어요.',
      )
    } finally {
      setIsBusy(false)
    }
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative flex h-[720px] w-[1280px] items-center justify-center overflow-hidden text-white"
      >
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 z-10 bg-[rgba(10,26,15,.4)]" />

        <Link
          aria-label="뒤로"
          className="absolute top-[25px] left-[25px] z-30 block size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          // 스스로 나간 사람에게는 다음에 들어올 때 하던 것을 들이밀지 않는다.
          onClick={clearSignupDraft}
          to="/"
        >
          <img
            alt=""
            className="block size-full object-contain"
            decoding="async"
            draggable={false}
            src={leftButtonUrl}
          />
        </Link>
        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] bg-transparent p-0 transition hover:scale-105"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="relative z-20" style={boardSize}>
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full object-contain"
            src={signupCompleteFrameUrl}
          />
          <img
            alt="빵긋"
            className="absolute top-[12%] left-1/2 w-[46%] -translate-x-1/2"
            src={logoUrl}
          />

          <h1
            className="absolute top-[34%] right-0 left-0 text-center text-[24px] text-[#5a7d3a]"
            style={jua}
          >
            {stage === 'signin'
              ? '구글로 로그인하기'
              : stage === 'connecting'
                ? '구글 계정 확인 중'
                : stage === 'birthdate'
                  ? '구글 연동 완료!'
                  : stage === 'complete'
                    ? '회원가입 완료!'
                    : '기존 계정과 연결하기'}
          </h1>

          {stage === 'signin' ? (
            <div className="absolute top-[49%] right-0 left-0 flex flex-col items-center gap-4">
              <div ref={googleButtonRef} />
              {isBusy ? (
                <p className="text-[#6f8a5e]" style={jua}>
                  확인하고 있어요...
                </p>
              ) : null}
            </div>
          ) : null}

          {stage === 'birthdate' ? (
            <>
              <p
                className="absolute top-[43%] right-0 left-0 text-center text-[14px] text-[#8a7a55]"
                style={jua}
              >
                {email || '구글 계정'} · 생년월일을 입력해 주세요
              </p>
              <div className="absolute top-[52%] left-[18.5%] z-20 h-[9%] w-[63%] rounded-2xl border-2 border-[#e4c98a] bg-[#fdf6e0]">
                <BirthDatePicker
                  maxDate={maxBirthDate}
                  onChange={setBirth}
                  value={birth}
                />
              </div>
              <ActionButton
                disabled={isBusy}
                label={isBusy ? '가입 중...' : '가입 완료'}
                onClick={() => void completeSignup()}
              />
            </>
          ) : null}

          {stage === 'connecting' ? (
            <p
              className="absolute top-[49%] right-0 left-0 text-center text-[17px] text-[#6f8a5e]"
              style={jua}
            >
              구글 로그인 정보를 확인하고 있어요...
            </p>
          ) : null}

          {stage === 'complete' ? (
            <>
              <p
                className="absolute top-[45%] right-[14%] left-[14%] text-center text-[17px] leading-relaxed text-[#6f8a5e]"
                style={jua}
              >
                구글 회원가입이 완료되었어요!
                <br />
                아래 버튼을 눌러 로그인해 주세요.
              </p>
              <ActionButton
                disabled={false}
                label="로그인하기"
                onClick={() => {
                  clearSignupDraft()
                  navigate('/', {
                    replace: true,
                    state: { openLoginModal: true },
                  })
                }}
              />
            </>
          ) : null}

          {stage === 'link' ? (
            <>
              <p
                className="absolute top-[43%] right-[14%] left-[14%] text-center text-[14px] text-[#8a7a55]"
                style={jua}
              >
                같은 이메일의 계정이 있어요. 기존 비밀번호를 입력해 주세요.
              </p>
              <input
                className="absolute top-[54%] left-[18.5%] h-[9%] w-[63%] rounded-2xl border-2 border-[#e4c98a] bg-[#fdf6e0] px-4 text-[#5a4a2a] outline-none"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="기존 계정 비밀번호"
                style={jua}
                type="password"
                value={password}
              />
              <ActionButton
                disabled={isBusy}
                label={isBusy ? '연결 중...' : '계정 연결'}
                onClick={() => void completeLink()}
              />
            </>
          ) : null}

          {error ? (
            <p
              className="absolute top-[66%] right-[12%] left-[12%] text-center text-[14px] text-[#b75245]"
              role="alert"
              style={jua}
            >
              {error}
            </p>
          ) : null}
        </main>
      </div>
    </div>
  )
}

function ActionButton({
  disabled,
  label,
  onClick,
}: {
  disabled: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="absolute top-[69%] left-1/2 z-20 flex h-[20%] w-[62%] -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-white [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] disabled:opacity-65"
      disabled={disabled}
      onClick={onClick}
      style={{
        ...jua,
        backgroundImage: `url(${signupCompleteButtonUrl})`,
      }}
      type="button"
    >
      {label}
    </button>
  )
}
