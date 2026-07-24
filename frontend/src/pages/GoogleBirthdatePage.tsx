import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import btnCreamUrl from '@/assets/images/auth/btn-cream.webp'
import btnGoLoginUrl from '@/assets/images/auth/btn-go-login.webp'
import signupCompleteFrameUrl from '@/assets/images/auth/signup-complete-frame.webp'
import signupCompleteStarUrl from '@/assets/images/auth/signup-complete-star.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { BirthDatePicker } from '@/components/ui/BirthDatePicker'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '1 / 1',
  width: 'min(460px, calc(100vw - 32px), calc(100vh - 32px))',
} as const
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

export function GoogleBirthdatePage() {
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [birth, setBirth] = useState('')
  const [isDone, setIsDone] = useState(false)
  const maxBirthDate = useMemo(latestAllowedBirthDate, [])

  const next = () => {
    if (!birth) {
      window.alert('생년월일을 올바르게 입력해 주세요.')
      return
    }

    const age = ageOf(birth)
    if (age === null || age < MIN_SIGNUP_AGE) {
      window.alert('만 19세 이상만 가입할 수 있어요.')
      return
    }

    setIsDone(true)
  }

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
        <img
          alt=""
          className="pointer-events-none absolute inset-0 z-10 h-full w-full object-contain"
          decoding="async"
          src={signupCompleteFrameUrl}
        />

        <img
          alt="빵긋"
          className="pointer-events-none absolute top-[12%] left-1/2 z-20 w-[46%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
          decoding="async"
          src={logoUrl}
        />
        <span
          className="pointer-events-none absolute top-[34%] left-1/2 z-20 -translate-x-1/2 text-[clamp(16px,4.5vw,22px)] whitespace-nowrap text-[#5a7d3a]"
          style={jua}
        >
          구글 연동 완료
        </span>
        <span
          className="pointer-events-none absolute top-[42%] left-1/2 z-20 -translate-x-1/2 text-[clamp(13px,3.6vw,17px)] whitespace-nowrap text-[#8a7a55]"
          style={jua}
        >
          생년월일을 입력해 주세요
        </span>

        <div className="absolute top-[52%] left-[18.5%] z-20 h-[9%] w-[63%] rounded-2xl border-2 border-[#e4c98a] bg-[#fdf6e0]">
          <BirthDatePicker
            maxDate={maxBirthDate}
            onChange={setBirth}
            value={birth}
          />
        </div>

        <div
          className="absolute top-[65%] left-0 z-20 w-full text-center text-[clamp(11px,3vw,13px)] text-[#8a7a55]"
          style={jua}
        >
          🌿 회원가입을 위해 생년월일만 입력해 주세요
        </div>

        <button
          className="absolute top-[76%] left-1/2 z-20 flex h-[10%] w-[62%] -translate-x-1/2 items-center justify-center rounded-full text-[clamp(18px,5.5vw,24px)] text-white transition active:translate-y-0.5"
          onClick={next}
          style={{
            ...jua,
            background: 'linear-gradient(180deg,#7ec850,#5aa838)',
            boxShadow: '0 8px 0 #3f7d1e',
            paintOrder: 'stroke fill',
            WebkitTextStroke: '3px #4a7d1e',
          }}
          type="button"
        >
          다음
        </button>
      </div>

      {isDone ? <SignupCompletePopup /> : null}
    </div>
  )
}

function SignupCompletePopup() {
  const navigate = useNavigate()
  const [isClosed, setIsClosed] = useState(false)

  if (isClosed) return null

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(18,30,18,0.55)] p-4">
      <div
        className="relative w-full max-w-[420px]"
        onClick={(event) => event.stopPropagation()}
        style={{ aspectRatio: '1 / 1' }}
      >
        <img
          alt=""
          className="pointer-events-none absolute inset-0 h-full w-full object-contain"
          decoding="async"
          src={signupCompleteFrameUrl}
        />

        <img
          alt=""
          className="pointer-events-none absolute top-[9%] left-1/2 w-[26%] -translate-x-1/2"
          decoding="async"
          src={signupCompleteStarUrl}
        />

        <h2
          className="absolute top-[32.5%] right-0 left-0 text-center text-[clamp(22px,6vw,34px)] text-[#3a7d2c]"
          style={jua}
        >
          회원가입 완료
        </h2>
        <p
          className="absolute top-[48%] right-0 left-0 text-center text-[clamp(14px,3.6vw,17px)] text-[#5a4a2a]"
          style={jua}
        >
          회원가입이 완료되었어요!
        </p>
        <p
          className="absolute top-[54.5%] right-0 left-0 text-center text-[clamp(11px,3vw,13px)] text-[#8a7a55]"
          style={jua}
        >
          이제 로그인해서 부모 프로필을 만들 수 있어요.
        </p>

        <button
          className="absolute top-[65%] left-1/2 h-[13%] w-[72%] -translate-x-1/2 text-[clamp(15px,4vw,18px)] text-white transition active:translate-y-0.5"
          onClick={() => navigate('/', { state: { openLoginModal: true } })}
          style={{
            ...jua,
            backgroundImage: `url(${btnGoLoginUrl})`,
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
            backgroundSize: '100% 100%',
            border: 'none',
            paintOrder: 'stroke fill',
            WebkitTextStroke: '2px #4a7d1e',
          }}
          type="button"
        >
          로그인 화면으로
        </button>
        <button
          className="absolute top-[77%] left-1/2 h-[11%] w-[64%] -translate-x-1/2 text-[clamp(13px,3.6vw,16px)] text-[#5a4a2a] transition active:translate-y-0.5"
          onClick={() => setIsClosed(true)}
          style={{
            ...jua,
            backgroundImage: `url(${btnCreamUrl})`,
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
            backgroundSize: '100% 100%',
            border: 'none',
          }}
          type="button"
        >
          닫기
        </button>
      </div>
    </div>
  )
}
