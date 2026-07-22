import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import signupBoardUrl from '@/assets/images/auth/signup-board.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const MIN_PASSWORD_LENGTH = 8
const MIN_SIGNUP_AGE = 19

function ageOf(birth: string) {
  if (!birth) return null
  const birthDate = new Date(birth)
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
  const audioRef = useRef<HTMLAudioElement>(null)
  const canvasRef = useFitScale<HTMLDivElement>()
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)

  const [email, setEmail] = useState('')
  const [emailChecked, setEmailChecked] = useState(false)
  const [emailAvailable, setEmailAvailable] = useState(false)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [birth, setBirth] = useState('')

  const emailValid = EMAIL_PATTERN.test(email)
  const passwordValid = password.length >= MIN_PASSWORD_LENGTH
  const age = useMemo(() => ageOf(birth), [birth])
  const ageValid = age !== null && age >= MIN_SIGNUP_AGE
  const canSubmit = emailChecked && emailAvailable && passwordValid && ageValid

  useEffect(() => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    audio.volume = 0.4

    void audio
      .play()
      .then(() => {
        setIsBgmPlaying(true)
      })
      .catch(() => {
        setIsBgmPlaying(false)
      })
  }, [])

  const toggleBgm = async () => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    if (audio.paused) {
      try {
        await audio.play()
        setIsBgmPlaying(true)
      } catch {
        setIsBgmPlaying(false)
      }
      return
    }

    audio.pause()
    setIsBgmPlaying(false)
  }

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
  const ageMessage = birth
    ? ageValid
      ? `만 ${age}세 · 가입할 수 있어요!`
      : '만 19세 이상만 가입할 수 있어요.'
    : ''

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden text-white"
      >
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
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(10,26,15,0.35)_0%,rgba(10,26,15,0.55)_100%)]" />

        <Link
          aria-label="뒤로"
          className="absolute top-[16px] left-[78px] z-30 grid h-[58px] w-[58px] place-items-center rounded-full border-2 border-white/70 bg-black/35 drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105"
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
          className="absolute bottom-[12px] left-[78px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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

        <div className="absolute top-1/2 left-1/2 h-[682px] w-[512px] -translate-x-1/2 -translate-y-1/2">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 h-full w-full object-contain"
            decoding="async"
            src={signupBoardUrl}
          />

          <img
            alt="빵긋"
            className="pointer-events-none absolute top-[14%] left-1/2 w-[220px] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
            decoding="async"
            src={logoUrl}
          />
          <span
            className="pointer-events-none absolute top-[27%] left-1/2 -translate-x-1/2 text-[20px] whitespace-nowrap text-[#5a7d3a]"
            style={jua}
          >
            이메일로 등록하기
          </span>

          <input
            className="absolute top-[36.4%] left-[17.5%] h-[7%] w-[44%] border-none bg-transparent px-4 text-[18px] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
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
            className="absolute top-[36.4%] left-[65%] h-[7%] w-[19%] border-none bg-none text-[16px] text-white"
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
            className="absolute top-[44.2%] left-[17.5%] text-[13px]"
            style={{ ...jua, color: emailAvailable ? '#3a7d2c' : '#c7563a' }}
          >
            {emailMessage}
          </div>

          <input
            className="absolute top-[50.2%] left-[17.5%] h-[7%] w-[56%] border-none bg-transparent px-4 text-[18px] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호 (8자 이상)"
            style={jua}
            type={showPassword ? 'text' : 'password'}
            value={password}
          />
          <button
            aria-label="비밀번호 보기"
            className="absolute top-[50.2%] right-[15%] h-[7%] w-11 border-none bg-none text-[20px] leading-none text-[#7f9a6c]"
            onClick={() => setShowPassword((value) => !value)}
            type="button"
          >
            {showPassword ? '🙈' : '👁'}
          </button>
          <div
            className="absolute top-[58%] left-[17.5%] text-[13px]"
            style={{ ...jua, color: passwordValid ? '#3a7d2c' : '#c7563a' }}
          >
            {passwordMessage}
          </div>

          <input
            className="absolute top-[64.2%] left-[17.5%] h-[7%] w-[66.5%] border-none bg-transparent px-4 text-[18px] text-[#5a4a2a] focus:outline-none"
            onChange={(event) => setBirth(event.target.value)}
            style={jua}
            type="date"
            value={birth}
          />
          <div
            className="absolute top-[72%] left-[17.5%] text-[13px]"
            style={{ ...jua, color: ageValid ? '#3a7d2c' : '#c7563a' }}
          >
            {ageMessage}
          </div>

          <button
            className="absolute top-[76%] left-[17%] h-[9%] w-[66%] border-none bg-none text-[27px] text-white transition active:translate-y-0.5"
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
    </div>
  )
}
