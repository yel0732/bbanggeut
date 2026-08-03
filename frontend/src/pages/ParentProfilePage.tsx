import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { completeNewSignupOnboarding, getStoredAccessToken } from '@/api/auth'
import {
  needsParentOnboarding,
  setParentPin,
  uploadParentProfileImage,
} from '@/api/profiles'

import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import backgroundUrl from '@/assets/images/profile/background.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import keypadButtonUrl from '@/assets/images/parent-profile/keypad-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'
import { PHOTO_BUTTON_CLIP } from '@/lib/buttonClip'
import { clearParentPhoto, saveParentPhoto } from '@/lib/parentPhoto'
const jua = { fontFamily: "'Jua', sans-serif" } as const
const PIN_LENGTH = 4
/*
  핀 확인을 마치면 곧바로 프로필 선택으로 넘어간다. 화면이 즉시 바뀌면 방금 무엇이
  저장됐는지 읽을 틈이 없어 안내 문구를 볼 시간만큼 머무른다.
*/
const LEAVE_DELAY_MS = 1200
const LEAVE_DELAY_ON_PHOTO_ERROR_MS = 2000

function isDataImageUrl(value: string | null): value is string {
  return typeof value === 'string' && value.startsWith('data:image/')
}

async function dataImageUrlToBlob(dataUrl: string) {
  return await fetch(dataUrl).then((response) => response.blob())
}

async function wait(ms: number) {
  await new Promise((resolve) => window.setTimeout(resolve, ms))
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
  const [pin, setPin] = useState('')
  const [firstPin, setFirstPin] = useState<string | null>(null)
  const [pinMessage, setPinMessage] = useState('')
  const [isPinError, setIsPinError] = useState(false)
  const [isPinSaved, setIsPinSaved] = useState(false)
  const isPinConfirmed = firstPin !== null && pin === firstPin && isPinSaved

  /*
    핀을 이미 만든 사람은 여기 있을 이유가 없다.

    주소를 직접 열었거나, 다른 탭에서 먼저 마친 경우다. 그대로 두면 핀 확인을
    마치는 순간 서버가 PIN_ALREADY_SET(403)을 돌려줘 무엇이 잘못됐는지 알 수 없다.
  */
  useEffect(() => {
    if (!getStoredAccessToken()) return
    let active = true
    void needsParentOnboarding().then((needsOnboarding) => {
      if (active && !needsOnboarding) navigate('/profiles', { replace: true })
    })
    return () => {
      active = false
    }
  }, [navigate])

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
        void (async () => {
          try {
            await setParentPin(nextPin)
          } catch (error) {
            setIsPinSaved(false)
            setFirstPin(null)
            setPin('')
            setIsPinError(true)
            setPinMessage(
              error instanceof Error
                ? error.message
                : 'PIN 번호를 설정하지 못했어요.',
            )
            return
          }

          setIsPinSaved(true)
          setPinMessage('핀번호를 저장했어요. 프로필을 고르러 가요!')

          /*
            사진은 핀과 달리 이 순간에만 올라간다. 완료 버튼이 없어 실패했다고
            여기 붙잡아 두면 사용자가 다시 시도할 수단이 없으므로, 알리기만 하고
            넘어간다 — 사진은 부모 모드에서 언제든 다시 바꿀 수 있다.
          */
          let hasPhotoError = false
          if (getStoredAccessToken() && isDataImageUrl(photoUrl)) {
            try {
              await uploadParentProfileImage(await dataImageUrlToBlob(photoUrl))
              clearParentPhoto()
            } catch {
              hasPhotoError = true
              setIsPinError(true)
              setPinMessage(
                '사진은 저장하지 못했어요. 부모 모드에서 다시 바꿀 수 있어요.',
              )
            }
          }

          await wait(
            hasPhotoError ? LEAVE_DELAY_ON_PHOTO_ERROR_MS : LEAVE_DELAY_MS,
          )
          completeNewSignupOnboarding()
          navigate('/profiles')
        })()
        return
      }

      setIsPinError(true)
      setPinMessage('핀번호가 달라요. 처음부터 다시 입력해 주세요.')
      window.setTimeout(() => {
        setFirstPin(null)
        setPin('')
      }, 450)
    },
    [firstPin, isPinConfirmed, navigate, photoUrl, pin],
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
        const nextPhoto = canvas.toDataURL('image/png')
        setPhotoUrl(nextPhoto)
        // 업로드는 핀을 저장한 뒤에야 일어난다. 그 사이 새로고침하거나 업로드가
        // 실패해도 찍은 사진이 남아 있도록 여기서 맡겨 둔다.
        saveParentPhoto(nextPhoto)
      }
    }
    stopParentCamera()
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

          {/*
            제목 바로 아래. 핀을 누르기 전에 이 화면이 끝이 아니라는 것과 다음에
            무엇을 하면 되는지를 먼저 읽히게 한다. 아래 요소들은 이 문구 높이만큼
            내려가 있다.
          */}
          <div
            className="absolute top-[18.5%] left-1/2 w-[500px] -translate-x-1/2 text-left text-[16px] leading-[1.45] text-[#6b5a34]"
            style={jua}
          >
            <p>🔒 부모 프로필에 들어가려면 PIN 번호를 입력해야 해요.</p>
            <p>
              👶 아이 프로필은 부모 모드의 &lsquo;아이 프로필 관리&rsquo;에서
              추가할 수 있어요.
            </p>
            <p>✏️ PIN 번호와 프로필 사진은 언제든 변경할 수 있어요.</p>
          </div>

          {isParentCameraOn ? (
            <video
              autoPlay
              className="absolute top-[30%] left-[38.5%] size-[122px] -translate-x-1/2 -scale-x-100 rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_4px_#d9c79a,0_5px_10px_rgba(0,0,0,.15)]"
              muted
              playsInline
              ref={parentVideoRef}
            />
          ) : (
            <img
              alt="부모 프로필"
              className="absolute top-[30%] left-[38.5%] size-[122px] -translate-x-1/2 rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_4px_#d9c79a,0_5px_10px_rgba(0,0,0,.15)]"
              src={photoUrl ?? parentAvatarUrl}
            />
          )}
          <button
            className="absolute top-[28.8%] left-[46.5%] h-[80px] w-[228px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] active:translate-y-0.5"
            onClick={() => void toggleParentCamera()}
            style={{
              ...jua,
              backgroundImage: `url(${photoButtonUrl})`,
              clipPath: PHOTO_BUTTON_CLIP,
            }}
            type="button"
          >
            {isParentCameraOn ? '사진 찍기' : '웹캠 촬영'}
          </button>
          <button
            className="absolute top-[36.7%] left-[46.5%] h-[80px] w-[228px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] active:translate-y-0.5"
            onClick={() => {
              stopParentCamera()
              setPhotoUrl(null)
              clearParentPhoto()
            }}
            style={{
              ...jua,
              backgroundImage: `url(${photoButtonUrl})`,
              clipPath: PHOTO_BUTTON_CLIP,
            }}
            type="button"
          >
            기본으로 사용
          </button>

          <div className="absolute top-[49.5%] right-[14%] left-[14%] h-0.5 rounded-sm bg-[rgba(122,100,60,.22)]" />
          <div
            className="absolute top-[51%] left-1/2 -translate-x-1/2 text-[26px] whitespace-nowrap text-[#3a7d2c]"
            style={jua}
          >
            {pinLabel}
          </div>
          <div
            className="absolute top-[55%] left-1/2 -translate-x-1/2 text-[13px] whitespace-nowrap"
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
          <div className="absolute top-[57%] left-1/2 flex -translate-x-1/2 gap-1">
            {Array.from({ length: PIN_LENGTH }).map((_, index) => (
              <img
                alt=""
                className="size-[50px] object-contain"
                key={index}
                src={index < pin.length ? pinDotFillUrl : pinDotEmptyUrl}
              />
            ))}
          </div>

          <div className="absolute top-[63.5%] left-1/2 grid -translate-x-1/2 grid-cols-[repeat(3,95px)] justify-center">
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
        </main>
      </div>
    </div>
  )
}
