import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { consumeNewSignup, loginWithEmail, storeLoginSession } from '@/api/auth'
import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import leftButtonUrl from '@/assets/images/auth/left-btn.webp'
import loginBoardUrl from '@/assets/images/auth/login-board.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '512 / 682',
  width: 512,
} as const

export function EmailLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const [email, setEmail] = useState(
    () => (location.state as { email?: string } | null)?.email ?? '',
  )
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSubmitting) return
    setError('')
    setIsSubmitting(true)
    try {
      const response = await loginWithEmail({
        autoLogin: remember,
        email: email.trim(),
        password,
      })
      storeLoginSession(response, remember)
      navigate(
        consumeNewSignup(response.email) ? '/profiles/new' : '/profiles',
        {
          replace: true,
        },
      )
    } catch (loginError) {
      setError(
        loginError instanceof Error
          ? loginError.message
          : '로그인에 실패했어요.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative flex h-[720px] w-[1280px] items-center justify-center overflow-hidden text-white"
      >
        <audio
          aria-label="초록 모험 시작 배경 음악"
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
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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

        <form
          className="relative z-20 -translate-y-3"
          onSubmit={(event) => void handleSubmit(event)}
          style={boardSize}
        >
          <img
            alt=""
            className="pointer-events-none absolute inset-0 z-10 h-full w-full object-contain"
            decoding="async"
            src={loginBoardUrl}
          />

          <img
            alt="빵긋"
            className="pointer-events-none absolute top-[13%] left-1/2 z-20 w-[43%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
            decoding="async"
            src={logoUrl}
          />
          <span
            className="pointer-events-none absolute top-[25%] left-1/2 z-20 -translate-x-1/2 text-[21px] whitespace-nowrap text-[#5a7d3a]"
            style={jua}
          >
            이메일로 들어가기
          </span>

          <input
            className="auth-transparent-input absolute top-[40%] left-[19%] z-20 h-[7.4%] w-[62%] appearance-none border-none bg-transparent px-[4%] text-[20px] text-[#5a4a2a] placeholder:text-[#9db089] focus:bg-transparent focus:outline-none"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="이메일"
            required
            style={jua}
            type="email"
            value={email}
          />

          <input
            className="auth-transparent-input absolute top-[51.2%] left-[19%] z-20 h-[7.4%] w-[54%] appearance-none border-none bg-transparent px-[4%] text-[20px] text-[#5a4a2a] placeholder:text-[#9db089] focus:bg-transparent focus:outline-none"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호"
            required
            style={jua}
            type={showPassword ? 'text' : 'password'}
            value={password}
          />
          <button
            aria-label="비밀번호 보기"
            className="absolute top-[51.2%] right-[19.5%] z-20 h-[7.4%] w-[9.5%] border-none bg-none text-[19px] leading-none text-[#6f8a5e]"
            onClick={() => setShowPassword((value) => !value)}
            style={jua}
            type="button"
          >
            {showPassword ? '숨김' : '보기'}
          </button>

          <button
            className="absolute top-[62.3%] left-[18.5%] z-20 flex items-center gap-2 border-none bg-none p-0 text-[15px] text-[#6f8a5e]"
            onClick={() => setRemember((value) => !value)}
            style={jua}
            type="button"
          >
            <span
              className="grid size-[24px] place-items-center rounded-[7px] border-[3px] text-[15px] text-white"
              style={{
                background: remember ? '#7ec850' : '#ffffff',
                borderColor: remember ? '#5aa838' : '#cfe3b6',
              }}
            >
              {remember ? '✓' : ''}
            </span>
            로그인 유지
          </button>

          {error ? (
            <p
              className="absolute top-[65.1%] right-[18%] left-[18%] z-20 truncate text-center text-[14px] text-[#b64b35]"
              style={jua}
            >
              {error}
            </p>
          ) : null}

          <button
            className="absolute top-[69.3%] left-[17%] z-20 flex h-[9%] w-[66%] items-center justify-center rounded-full text-[27px] text-white transition active:translate-y-0.5 disabled:cursor-wait disabled:opacity-70"
            disabled={isSubmitting}
            style={{
              ...jua,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '4px #4a7d1e',
            }}
            type="submit"
          >
            {isSubmitting ? '들어가는 중...' : '들어가기'}
          </button>

          <div
            className="absolute top-[80.5%] right-0 left-0 z-20 text-center text-[15px] text-[#7a693e]"
            style={jua}
          >
            아직 계정이 없어요?{' '}
            <Link className="border-b text-[#3a7d2c]" to="/auth/signup">
              이메일로 등록하기
            </Link>
          </div>
        </form>
      </div>
    </div>
  )
}
