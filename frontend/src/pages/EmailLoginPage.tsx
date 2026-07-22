import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import loginBoardUrl from '@/assets/images/auth/login-board.webp'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const boardSize = {
  aspectRatio: '512 / 682',
  width: 'min(512px, calc(100vw - 32px), calc((100vh - 32px) * 512 / 682))',
} as const

export function EmailLoginPage() {
  const audioRef = useRef<HTMLAudioElement>(null)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)

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

  return (
    <div className="relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)] p-4 text-white">
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
          src={loginBoardUrl}
        />

        <img
          alt="빵긋"
          className="pointer-events-none absolute top-[13%] left-1/2 z-20 w-[43%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
          decoding="async"
          src={logoUrl}
        />
        <span
          className="pointer-events-none absolute top-[25%] left-1/2 z-20 -translate-x-1/2 text-[clamp(15px,4vw,21px)] whitespace-nowrap text-[#5a7d3a]"
          style={jua}
        >
          이메일로 들어가기
        </span>

        <input
          className="absolute top-[40%] left-[19%] z-20 h-[7.4%] w-[62%] border-none bg-transparent px-[4%] text-[clamp(14px,4vw,20px)] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
          onChange={(event) => setEmail(event.target.value)}
          placeholder="이메일"
          style={jua}
          type="email"
          value={email}
        />

        <input
          className="absolute top-[51.2%] left-[19%] z-20 h-[7.4%] w-[54%] border-none bg-transparent px-[4%] text-[clamp(14px,4vw,20px)] text-[#5a4a2a] placeholder:text-[#9db089] focus:outline-none"
          onChange={(event) => setPassword(event.target.value)}
          placeholder="비밀번호"
          style={jua}
          type={showPassword ? 'text' : 'password'}
          value={password}
        />
        <button
          aria-label="비밀번호 보기"
          className="absolute top-[51.2%] right-[19.5%] z-20 h-[7.4%] w-[9.5%] border-none bg-none text-[clamp(12px,3vw,15px)] leading-none text-[#6f8a5e]"
          onClick={() => setShowPassword((value) => !value)}
          type="button"
        >
          {showPassword ? '숨김' : '보기'}
        </button>

        <button
          className="absolute top-[62.3%] left-[18.5%] z-20 flex items-center gap-2 border-none bg-none p-0 text-[clamp(12px,3.2vw,15px)] text-[#6f8a5e]"
          onClick={() => setRemember((value) => !value)}
          style={jua}
          type="button"
        >
          <span
            className="grid size-[clamp(18px,5vw,24px)] place-items-center rounded-[7px] border-[3px] text-[clamp(11px,3vw,15px)] text-white"
            style={{
              background: remember ? '#7ec850' : '#ffffff',
              borderColor: remember ? '#5aa838' : '#cfe3b6',
            }}
          >
            {remember ? '✓' : ''}
          </span>
          로그인 유지
        </button>
        <a
          className="absolute top-[62.7%] right-[19%] z-20 text-[clamp(12px,3.2vw,15px)] text-[#6f8a5e]"
          href="#"
          style={jua}
        >
          비밀번호 찾기
        </a>

        <Link
          className="absolute top-[69.3%] left-[17%] z-20 flex h-[9%] w-[66%] items-center justify-center rounded-full text-[clamp(20px,6vw,27px)] text-white transition active:translate-y-0.5"
          style={{
            ...jua,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '4px #4a7d1e',
          }}
          to="/profiles"
        >
          들어가기
        </Link>

        <div
          className="absolute top-[80.5%] right-0 left-0 z-20 text-center text-[clamp(12px,3.2vw,15px)] text-[#7a693e]"
          style={jua}
        >
          아직 계정이 없어요?{' '}
          <Link className="border-b text-[#3a7d2c]" to="/auth/signup">
            이메일로 등록하기
          </Link>
        </div>
      </div>
    </div>
  )
}
