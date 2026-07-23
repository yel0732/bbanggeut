import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import signupBoardUrl from '@/assets/images/auth/signup-board.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '512 / 682',
  width: 'min(512px, calc(100vw - 32px), calc((100vh - 32px) * 512 / 682))',
} as const
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const MIN_PASSWORD_LENGTH = 8
const MIN_SIGNUP_AGE = 19

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

export function EmailSignupPage() {
  const navigate = useNavigate()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()

  const [email, setEmail] = useState('')
  const [emailChecked, setEmailChecked] = useState(false)
  const [emailAvailable, setEmailAvailable] = useState(false)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [birth, setBirth] = useState('')
  const maxBirthDate = useMemo(latestAllowedBirthDate, [])

  const emailValid = EMAIL_PATTERN.test(email)
  const passwordValid = password.length >= MIN_PASSWORD_LENGTH
  const age = useMemo(() => ageOf(birth), [birth])
  const ageValid = age !== null && age >= MIN_SIGNUP_AGE
  const canSubmit = emailChecked && emailAvailable && passwordValid && ageValid

  const checkEmail = () => {
    if (!emailValid) {
      setEmailChecked(false)
      setEmailAvailable(false)
      window.alert('올바른 이메일 형식을 입력해요.')
      return
    }

    const taken = email.trim().toLowerCase() === 'taken@bbanggut.com'
    setEmailChecked(true)
    setEmailAvailable(!taken)
  }

  const submit = () => {
    if (!emailChecked || !emailAvailable) {
      window.alert('이메일 중복 확인을 해주세요.')
      return
    }
    if (!passwordValid) {
      window.alert('비밀번호는 8자 이상이어야 해요.')
      return
    }
    if (!ageValid) {
      window.alert('만 19세 이상만 가입할 수 있어요.')
      return
    }
    navigate('/profiles')
  }

  const emailMessage = emailChecked
    ? emailAvailable
      ? '사용할 수 있는 이메일이에요!'
      : '이미 사용 중인 이메일이에요.'
    : ''
  const passwordMessage = password
    ? passwordValid
      ? '안전한 비밀번호예요!'
      : '8자 이상 입력해요.'
    : ''
  return (
    <div className="relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)] p-4 text-white">
      <audio
        aria-label="초록 모험 시작 배경 음악"
        autoPlay
        loop
        preload="auto"
        ref={audioRef}
        src={bgmUrl}
      />

      <img
        alt=""
        className="absolute inset-0 z-0 h-full w-full object-cover"
        decoding="async"
        fetchPriority="high"
        src={backgroundUrl}
      />
      <div className="pointer-events-none absolute inset-0 z-10 bg-[linear-gradient(180deg,rgba(10,26,15,0.28)_0%,rgba(10,26,15,0.46)_100%)]" />

      <Link
        aria-label="뒤로"
        className="absolute top-4 left-[clamp(16px,6vw,78px)] z-30 grid h-[58px] w-[58px] place-items-center rounded-full border-2 border-white/70 bg-black/35 drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105"
        to="/"
      >
        <svg
          aria-hidden="true"
          className="ml-[-2px] size-6"
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={3}
          viewBox="0 0 24 24"
        >
          <path d="M15 19l-7-7 7-7" />
        </svg>
      </Link>

      <button
        aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
        className="absolute bottom-6 left-[clamp(16px,6vw,78px)] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
        onClick={() => {
          void toggleBgm()
        }}
        type="button"
      >
        <img
          alt=""
          className="block h-full w-full object-contain"
          decoding="async"
          src={isBgmPlaying ? musicOnUrl : musicOffUrl}
        />
      </button>

      <div className="relative z-20" style={boardSize}>
        <div className="absolute top-[12%] left-[10%] h-[76%] w-[80%] rounded-[40px] bg-[#f5df9f] shadow-[0_20px_48px_rgba(24,45,20,0.34),inset_0_0_0_5px_rgba(117,92,42,0.22)]" />
        <img
          alt=""
          className="pointer-events-none absolute inset-0 z-10 h-full w-full object-contain"
          decoding="async"
          src={signupBoardUrl}
        />

        <img
          alt="빵긋"
          className="pointer-events-none absolute top-[14%] left-1/2 z-20 w-[43%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
          decoding="async"
          src={logoUrl}
        />
        <span
          className="pointer-events-none absolute top-[27%] left-1/2 z-20 -translate-x-1/2 text-[clamp(15px,4vw,20px)] whitespace-nowrap text-[#5a7d3a]"
          style={jua}
        >
          이메일로 등록하기
        </span>

        <input
          className="absolute top-[36%] left-[17.5%] z-20 h-[7%] w-[44%] border-none bg-transparent px-[3%] text-[clamp(13px,3.8vw,18px)] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
          onChange={(event) => {
            setEmail(event.target.value)
            setEmailChecked(false)
            setEmailAvailable(false)
          }}
          placeholder="이메일"
          style={jua}
          type="email"
          value={email}
        />
        <button
          className="absolute top-[36%] left-[65%] z-20 h-[7%] w-[19%] rounded-full border-none text-[clamp(11px,3vw,15px)] text-white"
          onClick={checkEmail}
          style={{
            ...jua,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '2.5px #4a7d1e',
          }}
          type="button"
        >
          중복확인
        </button>
        <div
          className="absolute top-[44%] left-[17.5%] z-20 text-[clamp(11px,2.8vw,13px)]"
          style={{ ...jua, color: emailAvailable ? '#3a7d2c' : '#c7563a' }}
        >
          {emailMessage}
        </div>

        <input
          className="absolute top-[50.5%] left-[17.5%] z-20 h-[7%] w-[56%] border-none bg-transparent px-[3%] text-[clamp(13px,3.8vw,18px)] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
          onChange={(event) => setPassword(event.target.value)}
          placeholder="비밀번호 (8자 이상)"
          style={jua}
          type={showPassword ? 'text' : 'password'}
          value={password}
        />
        <button
          aria-label="비밀번호 보기"
          className="absolute top-[50.5%] right-[18%] z-20 h-[7%] w-[10%] border-none bg-none text-[clamp(11px,3vw,15px)] leading-none text-[#6f8a5e]"
          onClick={() => setShowPassword((value) => !value)}
          type="button"
        >
          {showPassword ? '숨김' : '보기'}
        </button>
        <div
          className="absolute top-[58%] left-[17.5%] z-20 text-[clamp(11px,2.8vw,13px)]"
          style={{ ...jua, color: passwordValid ? '#3a7d2c' : '#c7563a' }}
        >
          {passwordMessage}
        </div>

        <div
          className="absolute top-[65%] left-[17.5%] z-20 h-[7%] w-[66.5%] overflow-hidden rounded-full pr-2"
          style={jua}
        >
          <BirthDatePicker
            maxDate={maxBirthDate}
            onChange={setBirth}
            value={birth}
          />
        </div>

        <button
          className="absolute top-[75.5%] left-[17%] z-20 h-[9%] w-[66%] rounded-full border-none text-[clamp(20px,6vw,27px)] text-white transition active:translate-y-0.5"
          onClick={submit}
          style={{
            ...jua,
            cursor: canSubmit ? 'pointer' : 'not-allowed',
            opacity: canSubmit ? 1 : 0.55,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '4px #4a7d1e',
          }}
          type="button"
        >
          가입하기
        </button>
      </div>
    </div>
  )
}
