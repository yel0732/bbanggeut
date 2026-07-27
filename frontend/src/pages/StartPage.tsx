import { useState, type CSSProperties } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { restoreSession } from '@/api/auth'
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
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { cn } from '@/lib/utils'

type StartPageLocationState = {
  openLoginModal?: boolean
}

export function StartPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { audioRef, isBgmPlaying, playBgm, toggleBgm } = useBgmPlayer()
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(
    Boolean((location.state as StartPageLocationState | null)?.openLoginModal),
  )
  const [isCheckingSession, setIsCheckingSession] = useState(false)

  const enterForest = async () => {
    if (isCheckingSession) return
    setIsCheckingSession(true)
    await playBgm()

    try {
      if (await restoreSession()) {
        navigate('/profiles')
        return
      }
      setIsLoginModalOpen(true)
    } finally {
      setIsCheckingSession(false)
    }
  }

  return (
    <div className="relative h-screen w-screen overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)] text-white">
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
      <div className="pointer-events-none absolute inset-0 z-10 bg-[linear-gradient(180deg,rgba(3,16,10,0.02)_0%,rgba(3,16,10,0.34)_100%)]" />

      <div className="absolute top-[clamp(44px,9vh,70px)] right-[clamp(24px,7.5vw,96px)] z-20 flex w-[550px] flex-col items-center">
        <img
          alt="빵긋"
          className="block w-full drop-shadow-[0_10px_18px_rgba(0,0,0,0.28)]"
          decoding="async"
          src={logoUrl}
        />
      </div>

      <div className="absolute right-30 bottom-12 z-20">
        <MainStartButton
          imageUrl={greenButtonUrl}
          label={isCheckingSession ? '확인하고 있어요...' : '숲 속 들어가기'}
          onClick={() => {
            void enterForest()
          }}
        />
      </div>

      <button
        aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
        className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_5px_10px_rgba(0,0,0,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white max-[420px]:h-[68px] max-[420px]:w-[68px]"
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
  )
}

function LoginModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(18,30,18,0.55)]"
      onClick={onClose}
      role="dialog"
    >
      <div
        className="relative isolate"
        onClick={(event) => event.stopPropagation()}
        style={{
          aspectRatio: '1 / 1',
          width: 'min(700px, calc(100vw - 32px), calc(100vh - 32px))',
        }}
      >
        <div className="absolute inset-[12%] z-0 rounded-[42px] bg-[#f4c079] shadow-[0_18px_42px_rgba(24,45,20,0.36),inset_0_0_0_5px_rgba(118,73,31,0.28)]" />
        <img
          alt=""
          className="absolute inset-0 z-10 size-full object-contain"
          decoding="async"
          src={popupUrl}
        />

        <p
          className="absolute top-[14%] right-0 left-0 z-20 text-center text-4xl leading-none text-white [text-shadow:0_2px_4px_rgba(30,80,20,0.5)]"
          style={{ fontFamily: "'Jua', sans-serif" }}
        >
          들어갈 문
        </p>

        <div className="absolute top-[24%] left-1/2 z-20 flex w-[83%] -translate-x-1/2 flex-col items-center gap-1.5">
          <ModalMenuButton
            color="#6a4a12"
            fontSize={32}
            imageUrl={yellowButtonUrl}
            label="구글로 들어가기"
            textLeft="35%"
            textTop="44%"
            to="/auth/google"
          />
          <ModalMenuButton
            color="#5a4a2a"
            fontSize={32}
            imageUrl={whiteButtonUrl}
            label="이메일로 들어가기"
            textLeft="33%"
            textTop="35%"
            to="/auth/login"
          />
          <ModalMenuButton
            className="mt-[8%]"
            color="#ffffff"
            fontSize={32}
            imageUrl={greenButtonUrl}
            label="이메일로 등록하기"
            strokeColor="#3f7d1e"
            strokeWidth={2.5}
            textLeft="33%"
            textTop="36%"
            to="/auth/signup"
          />
        </div>

        <button
          aria-label="로그인 창 닫기"
          className="absolute top-[9%] right-[14%] z-30 h-[11%] w-[11%] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-white"
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

type ModalMenuButtonProps = {
  className?: string
  color: string
  fontSize: number
  imageUrl: string
  label: string
  onClick?: () => void
  strokeColor?: string
  strokeWidth?: number
  textLeft?: string
  textTop?: string
  to?: string
}

function MainStartButton({
  imageUrl,
  label,
  onClick,
}: {
  imageUrl: string
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="relative block w-[min(500px,calc(100vw-40px))] cursor-pointer appearance-none border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white/85 active:scale-[0.99]"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="relative z-0 block w-full select-none"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className="absolute z-10 flex w-fit translate-x-2/7 -translate-y-1/2 items-center justify-center text-center text-[38px] leading-none whitespace-nowrap text-white"
        style={{
          fontFamily: "'Jua', sans-serif",
          left: '19%',
          paintOrder: 'stroke fill',
          right: '19%',
          top: '48%',
          WebkitTextStroke: '3.5px #3f7d1e',
        }}
      >
        {label}
      </span>
    </button>
  )
}

function ModalMenuButton({
  className,
  color,
  fontSize,
  imageUrl,
  label,
  onClick,
  strokeColor,
  strokeWidth,
  textLeft = '21%',
  textTop = '48%',
  to,
}: ModalMenuButtonProps) {
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
        className="relative z-0 block w-full select-none"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className="absolute z-10 flex w-fit items-center justify-center text-center leading-none whitespace-nowrap"
        style={{
          ...textStyle,
          left: textLeft,
          top: textTop,
        }}
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
