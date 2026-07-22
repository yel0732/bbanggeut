import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import greenButtonUrl from '@/assets/images/start/light-green-button.webp'
import yellowButtonUrl from '@/assets/images/start/light-yellow-button.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import whiteButtonUrl from '@/assets/images/start/light-white-button.webp'
import popupUrl from '@/assets/images/start/wood-popup.webp'
import { useFitScale } from '@/hooks/useFitScale'
import { cn } from '@/lib/utils'

export function StartPage() {
  const navigate = useNavigate()
  const audioRef = useRef<HTMLAudioElement>(null)
  const canvasRef = useFitScale<HTMLDivElement>()
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
        <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(3,16,10,0.02)_0%,rgba(3,16,10,0.34)_100%)]" />

        <div className="absolute top-[70px] right-[96px] flex w-[520px] flex-col items-center">
          <img
            alt="빵긋"
            className="block w-[440px] drop-shadow-[0_10px_18px_rgba(0,0,0,0.28)]"
            decoding="async"
            src={logoUrl}
          />
        </div>

        <div className="absolute right-0 bottom-[60px] left-0 flex flex-col items-center gap-1.5 pl-[520px]">
          <StartMenuButton
            className="w-[320px]"
            color="#ffffff"
            fontSize={34}
            imageUrl={greenButtonUrl}
            label="숲 속 들어가기"
            onClick={() => {
              void enterForest()
            }}
            strokeColor="#3f7d1e"
            strokeWidth={5}
          />
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[12px] left-[78px] z-40 h-[62px] w-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
          onClick={toggleBgm}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        {isLoginModalOpen ? (
          <LoginModal onClose={() => setIsLoginModalOpen(false)} />
        ) : null}
      </div>
    </div>
  )
}

function LoginModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-30 flex items-center justify-center bg-[rgba(18,30,18,0.55)]"
      onClick={onClose}
      role="dialog"
    >
      <div
        className="relative h-[540px] w-[540px]"
        onClick={(event) => event.stopPropagation()}
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-contain"
          decoding="async"
          src={popupUrl}
        />

        <h2
          className="absolute top-[70px] right-0 left-0 text-center text-[30px] leading-none text-white [text-shadow:0_2px_4px_rgba(30,80,20,0.5)]"
          style={{ fontFamily: "'Jua', sans-serif" }}
        >
          들어갈 문
        </h2>

        <div className="absolute top-[130px] left-1/2 flex w-[83%] -translate-x-1/2 flex-col items-center gap-1.5">
          <StartMenuButton
            color="#6a4a12"
            fontSize={26}
            imageUrl={yellowButtonUrl}
            label="구글로 들어가기"
            to="/profiles"
          />
          <StartMenuButton
            color="#5a4a2a"
            fontSize={26}
            imageUrl={whiteButtonUrl}
            label="이메일로 들어가기"
            to="/auth/login"
          />
          <StartMenuButton
            className="mt-11"
            color="#ffffff"
            fontSize={26}
            imageUrl={greenButtonUrl}
            label="이메일로 등록하기"
            strokeColor="#3f7d1e"
            strokeWidth={4}
            to="/auth/signup"
          />
        </div>

        <button
          aria-label="로그인 창 닫기"
          className="absolute top-[48px] right-[78px] z-20 h-[60px] w-[60px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-white"
          onClick={onClose}
          type="button"
        >
          <img
            alt=""
            className="block h-full w-full object-contain"
            decoding="async"
            src={closeButtonUrl}
          />
        </button>
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
  className?: string
  color: string
  fontSize: number
  imageUrl: string
  label: string
  onClick?: () => void
  strokeColor?: string
  strokeWidth?: number
  to?: string
}

function StartMenuButton({
  className,
  color,
  fontSize,
  imageUrl,
  label,
  onClick,
  strokeColor,
  strokeWidth,
  to,
}: StartMenuButtonProps) {
  const textStyle: CSSProperties = {
    color,
    fontFamily: "'Jua', sans-serif",
    fontSize: `${fontSize}px`,
    ...(strokeColor && strokeWidth
      ? {
          paintOrder: 'stroke fill',
          WebkitTextStroke: `${strokeWidth}px ${strokeColor}`,
        }
      : {}),
  }

  const content = (
    <>
      <img
        alt=""
        className="block w-full select-none"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className="absolute inset-0 top-[47%] flex -translate-y-1/2 items-center justify-center text-center whitespace-nowrap"
        style={textStyle}
      >
        {label}
      </span>
    </>
  )

  const commonClassName = cn(
    'relative block w-full transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white/85 active:scale-[0.99]',
    className,
  )

  if (to) {
    return (
      <Link className={commonClassName} onClick={onClick} to={to}>
        {content}
      </Link>
    )
  }

  return (
    <button
      className={cn(
        commonClassName,
        'appearance-none border-0 bg-transparent p-0',
      )}
      onClick={onClick}
      type="button"
    >
      {content}
    </button>
  )
}
