import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { completeNewSignupOnboarding, getStoredAccessToken } from '@/api/auth'
import { setParentPin, uploadParentProfileImage } from '@/api/profiles'

import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import backgroundUrl from '@/assets/images/profile/background.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import keypadButtonUrl from '@/assets/images/parent-profile/keypad-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'
const jua = { fontFamily: "'Jua', sans-serif" } as const
const PIN_LENGTH = 4
const ONBOARDING_PIN_DRAFT_KEY = 'bbanggut-onboarding-parent-pin-draft'
const ONBOARDING_PIN_SAVED_KEY = 'bbanggut-onboarding-parent-pin-saved'

function isDataImageUrl(value: string | null): value is string {
  return typeof value === 'string' && value.startsWith('data:image/')
}

async function dataImageUrlToBlob(dataUrl: string) {
  return await fetch(dataUrl).then((response) => response.blob())
}

function loadOnboardingPinDraft() {
  try {
    const pin = window.sessionStorage.getItem(ONBOARDING_PIN_DRAFT_KEY)
    return /^\d{4}$/.test(pin ?? '') ? pin : null
  } catch {
    return null
  }
}

function isOnboardingPinSaved() {
  return window.sessionStorage.getItem(ONBOARDING_PIN_SAVED_KEY) === 'true'
}
const KEYPAD_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '←']

export function ParentProfilePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()
  const parentVideoRef = useRef<HTMLVideoElement>(null)
  const parentStreamRef = useRef<MediaStream | null>(null)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)
  const [isParentCameraOn, setIsParentCameraOn] = useState(false)
  const [pin, setPin] = useState(() => loadOnboardingPinDraft() ?? '')
  const [firstPin, setFirstPin] = useState<string | null>(() =>
    loadOnboardingPinDraft(),
  )
  const [pinMessage, setPinMessage] = useState('')
  const [isPinError, setIsPinError] = useState(false)
  const [isPinSaved, setIsPinSaved] = useState(isOnboardingPinSaved)
  const isPinConfirmed = firstPin !== null && pin === firstPin && isPinSaved

  useEffect(() => {
    return () => {
      parentStreamRef.current?.getTracks().forEach((track) => track.stop())
    }
  }, [])

  useEffect(() => {
    const video = parentVideoRef.current
    const stream = parentStreamRef.current
    if (!isParentCameraOn || !video || !stream) return

    video.srcObject = stream
    void video.play()
  }, [isParentCameraOn])

  const pressKey = useCallback(
    (key: string) => {
      if (key === '←') {
        setPin((value) => value.slice(0, -1))
        setPinMessage('')
        setIsPinError(false)
        return
      }

      if (!/^\d$/.test(key) || pin.length >= PIN_LENGTH || isPinConfirmed) {
        return
      }

      const nextPin = pin + key
      setPin(nextPin)
      setPinMessage('')
      setIsPinError(false)

      if (nextPin.length !== PIN_LENGTH) return

      if (firstPin === null) {
        window.setTimeout(() => {
          setFirstPin(nextPin)
          setPin('')
          setPinMessage('확인을 위해 한 번 더 입력해 주세요.')
        }, 180)
        return
      }

      if (nextPin === firstPin) {
        void setParentPin(nextPin)
          .then(() => {
            window.sessionStorage.setItem(ONBOARDING_PIN_DRAFT_KEY, nextPin)
            window.sessionStorage.setItem(ONBOARDING_PIN_SAVED_KEY, 'true')
            setIsPinSaved(true)
            setPinMessage('PIN 번호가 설정되었습니다.')
          })
          .catch((error: unknown) => {
            window.sessionStorage.removeItem(ONBOARDING_PIN_DRAFT_KEY)
            window.sessionStorage.removeItem(ONBOARDING_PIN_SAVED_KEY)
            setIsPinSaved(false)
            setFirstPin(null)
            setPin('')
            setIsPinError(true)
            setPinMessage(
              error instanceof Error
                ? error.message
                : 'PIN 번호를 설정하지 못했어요.',
            )
          })
        return
      }

      setIsPinError(true)
      setPinMessage('핀번호가 달라요. 처음부터 다시 입력해 주세요.')
      window.setTimeout(() => {
        setFirstPin(null)
        setPin('')
      }, 450)
    },
    [firstPin, isPinConfirmed, pin],
  )

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) pressKey(event.key)
      if (event.key === 'Backspace') pressKey('←')
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [pressKey])

  const stopParentCamera = () => {
    parentStreamRef.current?.getTracks().forEach((track) => track.stop())
    parentStreamRef.current = null
    setIsParentCameraOn(false)
  }

  const toggleParentCamera = async () => {
    if (!isParentCameraOn) {
      try {
        parentStreamRef.current = await navigator.mediaDevices.getUserMedia({
          video: { width: 480, height: 480 },
        })
        setIsParentCameraOn(true)
      } catch {
        return
      }
      return
    }

    const video = parentVideoRef.current
    if (video?.videoWidth) {
      const sourceSize = Math.min(video.videoWidth, video.videoHeight)
      const canvas = document.createElement('canvas')
      canvas.width = 300
      canvas.height = 300
      const context = canvas.getContext('2d')
      if (context) {
        context.translate(300, 0)
        context.scale(-1, 1)
        context.drawImage(
          video,
          (video.videoWidth - sourceSize) / 2,
          (video.videoHeight - sourceSize) / 2,
          sourceSize,
          sourceSize,
          0,
          0,
          300,
          300,
        )
        setPhotoUrl(canvas.toDataURL('image/png'))
      }
    }
    stopParentCamera()
  }

  const finish = async () => {
    if (!isPinConfirmed) {
      setIsPinError(true)
      setPinMessage('핀번호 설정과 확인을 완료해 주세요.')
      return
    }
    try {
      if (getStoredAccessToken() && firstPin && !isPinSaved) {
        await setParentPin(firstPin)
      }
      if (getStoredAccessToken() && isDataImageUrl(photoUrl)) {
        await uploadParentProfileImage(await dataImageUrlToBlob(photoUrl))
      }
    } catch (error) {
      setIsPinError(true)
      setPinMessage(
        error instanceof Error
          ? error.message
          : 'PIN 번호를 저장하지 못했어요.',
      )
      return
    }
    window.sessionStorage.removeItem(ONBOARDING_PIN_DRAFT_KEY)
    window.sessionStorage.removeItem(ONBOARDING_PIN_SAVED_KEY)
    completeNewSignupOnboarding()
    navigate('/profiles')
  }

  const pinLabel =
    firstPin === null
      ? '핀번호 설정'
      : isPinConfirmed
        ? '핀번호 설정 완료'
        : '핀번호 확인'

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] overflow-hidden"
        ref={canvasRef}
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          fetchPriority="high"
          src={backgroundUrl}
        />

        <Link
          aria-label="뒤로"
          className="absolute top-[25px] left-[25px] z-30 block size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105"
          to="/"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="absolute top-[-10px] left-1/2 z-10 h-[720px] w-[710px] -translate-x-1/2">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full"
            src={frameUrl}
          />

          <h1
            className="absolute top-[11.5%] left-1/2 -translate-x-1/2 text-[31px] whitespace-nowrap text-[#3a7d2c]"
            style={jua}
          >
            부모 프로필 만들기
          </h1>

          {isParentCameraOn ? (
            <video
              autoPlay
              className="absolute top-[18.5%] left-[38.5%] size-[122px] -translate-x-1/2 -scale-x-100 rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_4px_#d9c79a,0_5px_10px_rgba(0,0,0,.15)]"
              muted
              playsInline
              ref={parentVideoRef}
            />
          ) : (
            <img
              alt="부모 프로필"
              className="absolute top-[18.5%] left-[38.5%] size-[122px] -translate-x-1/2 rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_4px_#d9c79a,0_5px_10px_rgba(0,0,0,.15)]"
              src={photoUrl ?? parentAvatarUrl}
            />
          )}
          <button
            className="absolute top-[19%] left-[46.5%] h-[62px] w-[228px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] active:translate-y-0.5"
            onClick={() => void toggleParentCamera()}
            style={{ ...jua, backgroundImage: `url(${photoButtonUrl})` }}
            type="button"
          >
            {isParentCameraOn ? '사진 찍기' : '웹캠 촬영'}
          </button>
          <button
            className="absolute top-[25.5%] left-[46.5%] h-[62px] w-[228px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] active:translate-y-0.5"
            onClick={() => {
              stopParentCamera()
              setPhotoUrl(null)
            }}
            style={{ ...jua, backgroundImage: `url(${photoButtonUrl})` }}
            type="button"
          >
            기본으로 사용
          </button>

          <div className="absolute top-[37%] right-[14%] left-[14%] h-0.5 rounded-sm bg-[rgba(122,100,60,.22)]" />
          <div
            className="absolute top-[37.5%] left-1/2 -translate-x-1/2 text-[26px] whitespace-nowrap text-[#3a7d2c]"
            style={jua}
          >
            {pinLabel}
          </div>
          <div
            className="absolute top-[42.1%] left-1/2 -translate-x-1/2 text-[13px] whitespace-nowrap"
            style={{
              ...jua,
              color: isPinError
                ? '#c7563a'
                : isPinConfirmed
                  ? '#3a7d2c'
                  : '#8a7a55',
            }}
          >
            {pinMessage}
          </div>
          <div className="absolute top-[43.5%] left-1/2 flex -translate-x-1/2 gap-1">
            {Array.from({ length: PIN_LENGTH }).map((_, index) => (
              <img
                alt=""
                className="size-[50px] object-contain"
                key={index}
                src={index < pin.length ? pinDotFillUrl : pinDotEmptyUrl}
              />
            ))}
          </div>

          <div className="absolute top-1/2 left-1/2 grid -translate-x-1/2 grid-cols-[repeat(3,95px)] justify-center">
            {KEYPAD_KEYS.map((key, index) =>
              key ? (
                <button
                  className="mx-auto -my-[14px] h-[74px] w-[110px] bg-[length:100%_100%] bg-center bg-no-repeat text-[20px] text-[#3a7d2c] active:scale-95"
                  key={key}
                  onClick={() => pressKey(key)}
                  style={{ ...jua, backgroundImage: `url(${keypadButtonUrl})` }}
                  type="button"
                >
                  {key}
                </button>
              ) : (
                <div key={`blank-${index}`} />
              ),
            )}
          </div>

          {/*
            완료는 핀번호 바로 아래, 판 안에 둔다. 화면 구석에 떨어져 있으면
            무엇을 끝내는 버튼인지 눈으로 잇기 어렵다.
          */}
          <button
            className="absolute top-[76%] left-1/2 h-[110px] w-[350px] -translate-x-1/2 bg-[length:100%_100%] bg-center bg-no-repeat text-[28px] text-white active:translate-y-0.5"
            onClick={() => void finish()}
            style={{
              ...jua,
              backgroundImage: `url(${completeButtonUrl})`,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '3px #4a7d1e',
            }}
            type="button"
          >
            완료
          </button>
        </main>
      </div>
    </div>
  )
}
