import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import {
  completeGoogleSignup,
  consumeNewSignup,
  googleAuthenticate,
  linkGoogleAccount,
  markNewSignup,
  storeLoginSession,
} from '@/api/auth'
import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import leftButtonUrl from '@/assets/images/auth/left-btn.webp'
import signupCompleteFrameUrl from '@/assets/images/auth/signup-complete-frame.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'

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
          renderButton: (element: HTMLElement, config: GoogleButtonConfig) => void
        }
      }
    }
  }
}

type Stage = 'signin' | 'birthdate' | 'complete' | 'link'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '1 / 1',
  width: 'min(460px, calc(100vw - 32px), calc(100vh - 32px))',
} as const
const MIN_SIGNUP_AGE = 19
const GOOGLE_SCRIPT_ID = 'google-identity-services'
const DEFAULT_GOOGLE_CLIENT_ID =
  '737186420019-trrstol7c5cvf919t15l4o4g866hpp77.apps.googleusercontent.com'

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
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const googleButtonRef = useRef<HTMLDivElement>(null)
  const [stage, setStage] = useState<Stage>('signin')
  const [birth, setBirth] = useState('')
  const [password, setPassword] = useState('')
  const [signupToken, setSignupToken] = useState('')
  const [linkToken, setLinkToken] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [isBusy, setIsBusy] = useState(false)
  const maxBirthDate = useMemo(latestAllowedBirthDate, [])

  const finishLogin = useCallback(
    (tokens: Parameters<typeof storeLoginSession>[0]) => {
      storeLoginSession(tokens, true)
      navigate(consumeNewSignup(tokens.email) ? '/profiles/new' : '/profiles', {
        replace: true,
      })
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
          finishLogin(result.tokens)
          return
        }
        if (result.status === 'SIGNUP_REQUIRED' && result.signupToken) {
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
      existingScript.addEventListener('load', renderGoogleButton, { once: true })
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
      finishLogin(tokens)
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
    <div className="relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)] p-4 text-white">
      <audio autoPlay loop preload="auto" ref={audioRef} src={bgmUrl} />
      <img alt="" className="absolute inset-0 size-full object-cover" src={backgroundUrl} />
      <div className="pointer-events-none absolute inset-0 z-10 bg-[rgba(10,26,15,.4)]" />

      <Link
        aria-label="뒤로"
        className="absolute top-[25px] left-[25px] z-30 block size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
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
        <img alt="" className="size-full object-contain" src={isBgmPlaying ? musicOnUrl : musicOffUrl} />
      </button>

      <main className="relative z-20" style={boardSize}>
        <img alt="" className="pointer-events-none absolute inset-0 size-full object-contain" src={signupCompleteFrameUrl} />
        <img alt="빵긋" className="absolute top-[12%] left-1/2 w-[46%] -translate-x-1/2" src={logoUrl} />

        <h1 className="absolute top-[34%] right-0 left-0 text-center text-[24px] text-[#5a7d3a]" style={jua}>
          {stage === 'signin'
            ? '구글로 들어가기'
            : stage === 'birthdate'
              ? '생년월일을 알려주세요'
              : stage === 'complete'
                ? '회원가입 완료!'
                : '기존 계정과 연결하기'}
        </h1>

        {stage === 'signin' ? (
          <div className="absolute top-[49%] right-0 left-0 flex flex-col items-center gap-4">
            <div ref={googleButtonRef} />
            {isBusy ? <p className="text-[#6f8a5e]" style={jua}>확인하고 있어요...</p> : null}
          </div>
        ) : null}

        {stage === 'birthdate' ? (
          <>
            <p className="absolute top-[43%] right-0 left-0 text-center text-[14px] text-[#8a7a55]" style={jua}>
              {email || '구글 계정'}
            </p>
            <div className="absolute top-[52%] left-[18.5%] z-20 h-[9%] w-[63%] rounded-2xl border-2 border-[#e4c98a] bg-[#fdf6e0]">
              <BirthDatePicker maxDate={maxBirthDate} onChange={setBirth} value={birth} />
            </div>
            <ActionButton disabled={isBusy} label={isBusy ? '가입 중...' : '가입 완료'} onClick={() => void completeSignup()} />
          </>
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
                setError('')
                setStage('signin')
              }}
            />
          </>
        ) : null}

        {stage === 'link' ? (
          <>
            <p className="absolute top-[43%] right-[14%] left-[14%] text-center text-[14px] text-[#8a7a55]" style={jua}>
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
            <ActionButton disabled={isBusy} label={isBusy ? '연결 중...' : '계정 연결'} onClick={() => void completeLink()} />
          </>
        ) : null}

        {error ? (
          <p className="absolute top-[66%] right-[12%] left-[12%] text-center text-[14px] text-[#b75245]" role="alert" style={jua}>
            {error}
          </p>
        ) : null}
      </main>
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
      className="absolute top-[76%] left-1/2 z-20 flex h-[10%] w-[62%] -translate-x-1/2 items-center justify-center rounded-full text-[22px] text-white disabled:opacity-65"
      disabled={disabled}
      onClick={onClick}
      style={{
        ...jua,
        background: 'linear-gradient(180deg,#7ec850,#5aa838)',
        boxShadow: '0 8px 0 #3f7d1e',
      }}
      type="button"
    >
      {label}
    </button>
  )
}
