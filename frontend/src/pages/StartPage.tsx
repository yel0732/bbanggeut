import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import greenButtonUrl from '@/assets/images/start/light-green-button.webp'
import yellowButtonUrl from '@/assets/images/start/light-yellow-button.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import whiteButtonUrl from '@/assets/images/start/light-white-button.webp'
import popupUrl from '@/assets/images/start/wood-popup.webp'

const menuButtonTextStyle = {
  alignItems: 'center',
  color: '#315f2b',
  display: 'flex',
  inset: '0',
  justifyContent: 'center',
  lineHeight: '1',
  pointerEvents: 'none',
  position: 'absolute',
  WebkitTextStroke: '1px rgba(247, 255, 240, 0.82)',
} as const

export function StartPage() {
  const navigate = useNavigate()
  const audioRef = useRef<HTMLAudioElement>(null)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false)

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

  const playBgm = async () => {
    const audio = audioRef.current

    if (!audio || !audio.paused) {
      return
    }

    try {
      audio.volume = 0.4
      await audio.play()
      setIsBgmPlaying(true)
    } catch {
      setIsBgmPlaying(false)
    }
  }

  const toggleBgm = async () => {
    const audio = audioRef.current

    if (!audio) {
      return
    }

    if (audio.paused) {
      await playBgm()
      return
    }

    audio.pause()
    setIsBgmPlaying(false)
  }

  const enterForest = async () => {
    await playBgm()

    if (isAuthenticated()) {
      navigate('/profiles')
      return
    }

    setIsLoginModalOpen(true)
  }

  return (
    <main
      className="relative h-screen w-full overflow-hidden bg-[#07160f] text-white"
      style={{ height: '100dvh' }}
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
      <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(3,16,10,0.02)_0%,rgba(3,16,10,0.34)_100%)]" />

      <img
        alt="빵긋"
        className="absolute z-10 h-auto drop-shadow-[0_14px_22px_rgba(45,29,11,0.22)]"
        decoding="async"
        src={logoUrl}
        style={{
          right: 'clamp(1.5rem, 5vw, 15rem)',
          top: 'clamp(1.5rem, 8vh, 5rem)',
          width: 'clamp(230px, 40vw, 520px)',
        }}
      />

      <nav
        className="absolute z-10 grid max-[560px]:right-3 max-[560px]:bottom-5 max-[560px]:w-[calc(100%-1.5rem)]"
        style={{
          bottom: 'clamp(2rem, 7vh, 5.5rem)',
          right: 'clamp(1rem, 11vw, 13rem)',
          width: 'clamp(320px, 28vw, 500px)',
        }}
      >
        <StartMenuButton
          imageUrl={greenButtonUrl}
          label="입장하기"
          onClick={() => {
            void enterForest()
          }}
        />
      </nav>

      <button
        aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
        className="fixed top-4 right-4 z-[999] grid size-12 place-items-center rounded-full border-2 border-white/70 bg-black/35 text-white shadow-[0_8px_22px_rgba(0,0,0,0.3)] backdrop-blur-sm transition hover:scale-105 hover:bg-black/45 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
        onClick={toggleBgm}
        type="button"
      >
        {isBgmPlaying ? (
          <svg
            aria-hidden="true"
            className="size-6"
            fill="currentColor"
            viewBox="0 0 24 24"
          >
            <rect height="14" rx="1.5" width="4.8" x="6" y="5" />
            <rect height="14" rx="1.5" width="4.8" x="13.2" y="5" />
          </svg>
        ) : (
          <svg
            aria-hidden="true"
            className="ml-0.5 size-6"
            fill="currentColor"
            viewBox="0 0 24 24"
          >
            <path d="M8 5.8c0-1.2 1.3-1.9 2.3-1.2l8.7 6.2c.8.6.8 1.8 0 2.4l-8.7 6.2c-1 .7-2.3 0-2.3-1.2V5.8Z" />
          </svg>
        )}
      </button>

      {isLoginModalOpen ? (
        <LoginModal onClose={() => setIsLoginModalOpen(false)} />
      ) : null}
    </main>
  )
}

function LoginModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      aria-modal="true"
      className="fixed inset-0 z-50 grid place-items-center bg-[#07160f]/68 px-4 backdrop-blur-[2px]"
      role="dialog"
    >
      <div className="relative aspect-square w-[min(88vw,560px)]">
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-contain"
          decoding="async"
          src={popupUrl}
        />

        <button
          aria-label="로그인 창 닫기"
          className="absolute top-[8%] right-[7%] z-20 grid size-[clamp(3.25rem,8vw,4.4rem)] place-items-center rounded-full border-[clamp(0.22rem,0.55vw,0.34rem)] border-white bg-[#cf6f4e] text-white shadow-[0_8px_16px_rgba(75,38,18,0.26)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-white"
          onClick={onClose}
          type="button"
        >
          <span className="relative block size-[58%] before:absolute before:top-1/2 before:left-0 before:h-[0.2rem] before:w-full before:-translate-y-1/2 before:rotate-45 before:rounded-full before:bg-white after:absolute after:top-1/2 after:left-0 after:h-[0.2rem] after:w-full after:-translate-y-1/2 after:-rotate-45 after:rounded-full after:bg-white" />
        </button>

        <div className="relative z-10 h-full px-[20%] text-center">
          <h2 className="absolute top-[16%] left-1/2 -translate-x-1/2 text-center text-2xl leading-none font-black tracking-normal whitespace-nowrap text-white">
            로그인
          </h2>

          <div className="absolute top-[35%] left-1/2 grid w-[60%] -translate-x-1/2 gap-[clamp(0.75rem,2vw,1.1rem)]">
            <StartMenuButton
              imageUrl={yellowButtonUrl}
              label="구글 로그인"
              to="/auth"
            />
            <StartMenuButton
              imageUrl={whiteButtonUrl}
              label="일반 로그인"
              to="/auth"
            />
            <StartMenuButton
              imageUrl={greenButtonUrl}
              label="회원가입"
              to="/profiles"
            />
          </div>
        </div>
      </div>
    </div>
  )
}

function isAuthenticated() {
  return ['accessToken', 'authToken', 'token'].some((key) =>
    Boolean(localStorage.getItem(key)),
  )
}

type StartMenuButtonProps = {
  imageUrl: string
  label: string
  onClick?: () => void
  to?: string
}

function StartMenuButton({
  imageUrl,
  label,
  onClick,
  to,
}: StartMenuButtonProps) {
  const content = (
    <>
      <img
        alt=""
        className="block h-auto w-full select-none"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className="z-20 px-[14%] text-center text-[clamp(1.65rem,3vw,2.65rem)] font-black tracking-normal whitespace-nowrap"
        style={menuButtonTextStyle}
      >
        {label}
      </span>
    </>
  )

  if (to) {
    return (
      <Link
        className="relative block w-full transition hover:scale-[1.025] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white/85 active:scale-[0.99]"
        onClick={onClick}
        to={to}
      >
        {content}
      </Link>
    )
  }

  return (
    <button
      className="relative block w-full appearance-none border-0 bg-transparent p-0 transition hover:scale-[1.025] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white/85 active:scale-[0.99]"
      onClick={onClick}
      type="button"
    >
      {content}
    </button>
  )
}
