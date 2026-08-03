import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { restoreSession } from '@/api/auth'
import { needsParentOnboarding } from '@/api/profiles'
import backgroundUrl from '@/assets/images/start/game-start-bg.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import greenButtonUrl from '@/assets/images/start/light-green-button.webp'
import yellowButtonUrl from '@/assets/images/start/light-yellow-button.webp'
import logoUrl from '@/assets/images/start/logo.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import whiteButtonUrl from '@/assets/images/start/light-white-button.webp'
import popupUrl from '@/assets/images/start/wood-popup.webp'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'
import { cn } from '@/lib/utils'

type StartPageLocationState = {
  openLoginModal?: boolean
}

/*
  실제로 눌리는 영역.

  버튼 그림은 넓은 네모 안에 알약 하나가 놓인 모양이고 그 바깥은 전부 투명하다.
  상자를 그대로 두면 그림이 없는 좌우 여백(각각 15~18%)을 눌러도 버튼이 눌린다.
  알파 채널의 불투명 경계까지만 남기고 잘라 낸다.

  값은 각 에셋의 불투명 경계 상자에서 뽑았다(위·오른쪽·아래·왼쪽 순). 모서리가
  둥근 만큼은 남지만, 그래도 눌리는 자리의 91%가 실제 버튼이 된다. 에셋을 바꾸면
  이 값도 다시 뽑아야 한다.
*/
const GREEN_BUTTON_CLIP = 'inset(4.55% 14.81% 6.71% 17.48%)'
const WHITE_BUTTON_CLIP = 'inset(6.11% 14.36% 14% 16.98%)'
const YELLOW_BUTTON_CLIP = 'inset(16.15% 15.53% 5.25% 18.3%)'

export function StartPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, playBgm, toggleBgm } = useAdventureBgm()
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(
    Boolean((location.state as StartPageLocationState | null)?.openLoginModal),
  )
  const [isCheckingSession, setIsCheckingSession] = useState(false)

  const enterForest = async () => {
    if (isCheckingSession) return
    setIsCheckingSession(true)
    // BGM은 기다리지 않는다. 로그인 창이 뜨는 속도와 아무 상관이 없다.
    void playBgm()

    try {
      if (await restoreSession()) {
        navigate(
          (await needsParentOnboarding()) ? '/profiles/new' : '/profiles',
        )
        return
      }
      setIsLoginModalOpen(true)
    } finally {
      setIsCheckingSession(false)
    }
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,#12241a_0%,#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <img
          alt=""
          className="absolute inset-0 z-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="pointer-events-none absolute inset-0 z-10 bg-[linear-gradient(180deg,rgba(3,16,10,0.02)_0%,rgba(3,16,10,0.34)_100%)]" />

        <div className="absolute top-[64px] right-[96px] z-20 flex w-[550px] flex-col items-center">
          <img
            alt="빵긋"
            className="block w-full drop-shadow-[0_10px_18px_rgba(0,0,0,0.28)]"
            decoding="async"
            src={logoUrl}
          />
        </div>

        {/*
          화면 크기 안내. 아이가 읽는 문장이 아니라 옆에서 열어 주는 어른이
          보는 자리라, 눈에 걸리되 로고와 시작 버튼을 가리지 않게 아래 가운데에
          둔다. 숲 배경에 글자만 얹으면 묻히므로 게임 이름표와 같은 알약을 깐다.
        */}
        <div className="pointer-events-none absolute right-0 bottom-[22px] left-0 z-20 flex justify-center">
          <p
            className="rounded-full bg-[#173618]/45 px-6 py-2 text-center text-[25px] leading-none text-white shadow-[0_5px_12px_rgba(12,38,13,0.22),inset_0_0_0_2px_rgba(255,255,255,0.12)] backdrop-blur-[1px] [text-shadow:0_2px_0_#2e6d2e,0_5px_8px_rgba(9,35,10,0.5)]"
            style={{ fontFamily: "'Jua', sans-serif" }}
          >
            데스크탑과 태블릿 전체 화면에서 가장 잘 보여요.
          </p>
        </div>

        <div className="absolute right-[120px] bottom-[80px] z-20">
          <MainStartButton
            busy={isCheckingSession}
            clipPath={GREEN_BUTTON_CLIP}
            imageUrl={greenButtonUrl}
            label="숲 속 들어가기"
            onClick={() => {
              void enterForest()
            }}
          />
        </div>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_5px_10px_rgba(0,0,0,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white"
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
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(18,30,18,0.55)]"
      onClick={onClose}
      role="dialog"
    >
      <div
        className="relative isolate"
        onClick={(event) => event.stopPropagation()}
        style={{
          aspectRatio: '1 / 1',
          width: 650,
        }}
      >
        <img
          alt=""
          className="absolute inset-0 z-10 size-full object-contain"
          decoding="async"
          src={popupUrl}
        />

        {/* 이메일 가입 창과 같이 제목 자리에 로고를 놓는다. */}
        <img
          alt="빵긋"
          className="pointer-events-none absolute top-[10.8%] left-1/2 z-20 w-[32%] -translate-x-1/2 drop-shadow-[0_5px_10px_rgba(0,0,0,0.18)]"
          decoding="async"
          src={logoUrl}
        />

        <div className="absolute top-[24%] left-[318px] z-20 flex w-[83%] -translate-x-1/2 flex-col items-center gap-1.5">
          <GoogleLoginButton />
          <ModalMenuButton
            clipPath={WHITE_BUTTON_CLIP}
            color="#5a4a2a"
            fontSize={32}
            imageUrl={whiteButtonUrl}
            label="이메일로 로그인하기"
            textLeft="28.5%"
            textTop="33%"
            to="/auth/login"
          />
          <ModalMenuButton
            className="mt-[8%]"
            clipPath={GREEN_BUTTON_CLIP}
            color="#ffffff"
            fontSize={32}
            imageUrl={greenButtonUrl}
            label="이메일로 가입하기"
            strokeColor="#3f7d1e"
            strokeWidth={2.5}
            textLeft="31%"
            textTop="35%"
            to="/auth/signup"
          />
        </div>

        <button
          aria-label="로그인 창 닫기"
          className="absolute top-[12%] right-[17%] z-30 h-[11%] w-[11%] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-white"
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

const GOOGLE_SCRIPT_ID = 'google-identity-services'
const DEFAULT_GOOGLE_CLIENT_ID =
  '737186420019-trrstol7c5cvf919t15l4o4g866hpp77.apps.googleusercontent.com'

function GoogleLoginButton() {
  const navigate = useNavigate()
  const googleButtonRef = useRef<HTMLDivElement>(null)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false
    const clientId =
      import.meta.env.VITE_GOOGLE_CLIENT_ID || DEFAULT_GOOGLE_CLIENT_ID

    const renderGoogleButton = () => {
      if (cancelled || !window.google || !googleButtonRef.current) return
      window.google.accounts.id.initialize({
        callback: ({ credential }) => {
          navigate('/auth/google', { state: { googleCredential: credential } })
        },
        client_id: clientId,
      })
      googleButtonRef.current.replaceChildren()
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        shape: 'pill',
        size: 'large',
        text: 'signin_with',
        theme: 'outline',
        width: 400,
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
        () => setLoadError('구글 로그인 화면을 불러오지 못했어요.'),
        { once: true },
      )
      document.head.appendChild(script)
    }

    return () => {
      cancelled = true
      existingScript?.removeEventListener('load', renderGoogleButton)
    }
  }, [navigate])

  return (
    <div className="relative block w-full cursor-pointer transition hover:scale-[1.04] active:scale-[0.99]">
      {/*
        누르는 자리를 그림 모양으로 자르는 칸.

        여기서 실제로 클릭을 받는 것은 구글 스크립트가 그려 넣는 투명한 버튼이고,
        그 버튼은 알약 그림보다 좌우로 더 넓게 퍼져 있다. 안내 문구는 이 칸 밖에
        둔다 — 안에 두면 잘려서 보이지 않는다.
      */}
      <div className="relative" style={{ clipPath: YELLOW_BUTTON_CLIP }}>
        <img
          alt=""
          className="pointer-events-none relative z-0 block w-full select-none"
          decoding="async"
          draggable={false}
          src={yellowButtonUrl}
        />
        <span
          className="pointer-events-none absolute top-[43%] left-[31.5%] z-10 flex w-fit items-center justify-center text-center text-[32px] leading-none whitespace-nowrap text-[#6a4a12]"
          style={{ fontFamily: "'Jua', sans-serif" }}
        >
          구글로 로그인하기
        </span>
        <div
          aria-label="Google 계정으로 로그인"
          className="absolute top-1/2 left-1/2 z-20 h-[44px] w-[400px] -translate-x-1/2 -translate-y-1/2 scale-x-[1.28] scale-y-[1.7] overflow-hidden opacity-[0.01]"
          ref={googleButtonRef}
        />
      </div>
      {loadError ? (
        <p
          className="absolute right-0 bottom-0 left-0 z-30 text-center text-[14px] text-[#b84b3a]"
          style={{ fontFamily: "'Jua', sans-serif" }}
        >
          {loadError}
        </p>
      ) : null}
    </div>
  )
}

type ModalMenuButtonProps = {
  className?: string
  clipPath: string
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
  busy,
  clipPath,
  imageUrl,
  label,
  onClick,
}: {
  /** 세션을 확인하는 동안 눌러도 반응이 없어 보이지 않게 표시한다. */
  busy: boolean
  clipPath: string
  imageUrl: string
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="relative block w-[500px] cursor-pointer appearance-none border-0 bg-transparent p-0 transition hover:scale-[1.04] focus-visible:outline-4 focus-visible:outline-offset-4 focus-visible:outline-white/85 active:scale-[0.99] disabled:cursor-default disabled:hover:scale-100"
      disabled={busy}
      onClick={onClick}
      style={{
        clipPath,
        ...(busy
          ? { animation: 'start-button-wait 900ms ease-in-out infinite' }
          : {}),
      }}
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
        {busy ? '들어가는 중...' : label}
      </span>
    </button>
  )
}

function ModalMenuButton({
  className,
  clipPath,
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
      <Link
        className={commonClassName}
        onClick={onClick}
        style={{ clipPath }}
        to={to}
      >
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
      style={{ clipPath }}
      type="button"
    >
      {content}
    </button>
  )
}
