import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { completeNewSignupOnboarding, getStoredAccessToken } from '@/api/auth'
import {
  createChildProfile as createBackendChildProfile,
  setParentPin,
} from '@/api/profiles'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import childTigerAvatarUrl from '@/assets/images/profile/child-avatar-tiger.webp'
import childRabbitAvatarUrl from '@/assets/images/profile/child-avatar-rabbit.webp'
import backgroundUrl from '@/assets/images/profile/background.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import portraitFrameUrl from '@/assets/images/parent-profile/frame-portrait.webp'
import inputBarUrl from '@/assets/images/parent-profile/input-bar.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import keypadButtonUrl from '@/assets/images/parent-profile/keypad-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import {
  childImageNumFromPhoto,
  loadChildProfiles,
  MAX_CHILD_PROFILES,
  saveChildProfiles,
} from '@/lib/childProfiles'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PIN_LENGTH = 4
const ONBOARDING_PIN_DRAFT_KEY = 'bbanggut-onboarding-parent-pin-draft'
const ONBOARDING_PIN_SAVED_KEY = 'bbanggut-onboarding-parent-pin-saved'

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
  const [searchParams] = useSearchParams()
  const isOnboardingChildFlow = searchParams.get('source') === 'onboarding'
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const parentVideoRef = useRef<HTMLVideoElement>(null)
  const parentStreamRef = useRef<MediaStream | null>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)
  const [isParentCameraOn, setIsParentCameraOn] = useState(false)
  const [showChildForm, setShowChildForm] = useState(
    searchParams.get('add') === '1',
  )
  const [childName, setChildName] = useState('')
  const [childPhotoUrl, setChildPhotoUrl] = useState<string | null>(null)
  const [isCameraOn, setIsCameraOn] = useState(false)
  const [childFormMessage, setChildFormMessage] = useState('')
  const childProfileCount = loadChildProfiles().length
  const isChildProfileLimitReached = childProfileCount >= MAX_CHILD_PROFILES
  const [pin, setPin] = useState(() => loadOnboardingPinDraft() ?? '')
  const [firstPin, setFirstPin] = useState<string | null>(() =>
    loadOnboardingPinDraft(),
  )
  const [pinMessage, setPinMessage] = useState('')
  const [isPinError, setIsPinError] = useState(false)
  const [isPinSaved, setIsPinSaved] = useState(isOnboardingPinSaved)
  const isEditMode = searchParams.get('edit') === '1'
  const isPinConfirmed = firstPin !== null && pin === firstPin && isPinSaved

  useEffect(() => {
    return () => {
      parentStreamRef.current?.getTracks().forEach((track) => track.stop())
      streamRef.current?.getTracks().forEach((track) => track.stop())
    }
  }, [])

  useEffect(() => {
    const video = parentVideoRef.current
    const stream = parentStreamRef.current
    if (!isParentCameraOn || !video || !stream) return

    video.srcObject = stream
    void video.play()
  }, [isParentCameraOn])

  useEffect(() => {
    const video = videoRef.current
    const stream = streamRef.current
    if (!isCameraOn || !video || !stream) return

    video.srcObject = stream
    void video.play().catch(() => {
      setChildFormMessage('웹캠 미리보기를 시작할 수 없어요.')
    })
  }, [isCameraOn])

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
              error instanceof Error ? error.message : 'PIN 번호를 설정하지 못했어요.',
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
        setPhotoUrl(canvas.toDataURL('image/webp', 0.9))
      }
    }
    stopParentCamera()
  }

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    setIsCameraOn(false)
  }

  const cycleDefaultChildPhoto = () => {
    stopCamera()
    setChildPhotoUrl((current) => {
      if (current === childTigerAvatarUrl) return childRabbitAvatarUrl
      if (current === childRabbitAvatarUrl) return null
      if (current === null) return childTigerAvatarUrl
      return null
    })
    setChildFormMessage('')
  }

  const toggleCamera = async () => {
    if (!isCameraOn) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: 480, height: 480 },
        })
        streamRef.current = stream
        setIsCameraOn(true)
        setChildFormMessage('화면을 확인한 뒤 사진 찍기를 눌러 주세요.')
      } catch {
        setChildFormMessage('웹캠을 사용할 수 없어요.')
      }
      return
    }

    const video = videoRef.current
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
        setChildPhotoUrl(canvas.toDataURL('image/webp', 0.9))
      }
    }
    stopCamera()
  }

  const closeChildForm = () => {
    stopCamera()
    if (isOnboardingChildFlow) {
      navigate('/profiles/children')
      return
    }
    setShowChildForm(false)
    setChildName('')
    setChildPhotoUrl(null)
    setChildFormMessage('')
  }

  const createChildProfile = async () => {
    const profiles = loadChildProfiles()
    if (profiles.length >= MAX_CHILD_PROFILES) {
      setChildFormMessage(
        `아이 프로필은 최대 ${MAX_CHILD_PROFILES}개까지 만들 수 있어요.`,
      )
      return
    }

    const name = childName.trim()
    if (!name) {
      setChildFormMessage('아이 이름을 입력해 주세요.')
      return
    }
    if (
      profiles.some(
        (profile) =>
          profile.name.trim().toLocaleLowerCase('ko-KR') ===
          name.toLocaleLowerCase('ko-KR'),
      )
    ) {
      setChildFormMessage('같은 이름의 아이 프로필이 이미 있어요.')
      return
    }
    const localProfile = {
      id:
        typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random()}`,
      name,
      photoUrl: childPhotoUrl,
      profileImageNum: childImageNumFromPhoto(childPhotoUrl),
    }

    try {
      if (getStoredAccessToken()) {
        const created = await createBackendChildProfile(
          name,
          localProfile.profileImageNum,
        )
        localProfile.id = String(created.childId)
        localProfile.profileImageNum = created.profileImageNum
      }
    } catch (error) {
      setChildFormMessage(
        error instanceof Error
          ? error.message
          : '?꾩씠 ?꾨줈?꾩쓣 留뚮뱾吏 紐삵뻽?댁슂.',
      )
      return
    }

    saveChildProfiles([
      ...profiles,
      localProfile,
    ])
    stopCamera()
    navigate('/profiles/children')
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
    } catch (error) {
      setIsPinError(true)
      setPinMessage(
        error instanceof Error
          ? error.message
          : '? 踰덊샇瑜???ν븯吏 紐삵뻽?댁슂.',
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
      ? isEditMode
        ? '핀번호 수정'
        : '핀번호 설정'
      : isPinConfirmed
        ? '핀번호 설정 완료'
        : '핀번호 확인'

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] origin-top overflow-hidden"
        ref={canvasRef}
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
          fetchPriority="high"
          src={backgroundUrl}
        />

        <Link
          aria-label="뒤로"
          className="absolute top-[25px] left-[25px] z-30 block size-[58px] drop-shadow-[0_4px_8px_rgba(0,0,0,0.28)] transition hover:scale-105"
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
          className="absolute bottom-[85px] left-[25px] z-40 size-[62px] border-none bg-none p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="absolute top-[-40px] left-1/2 z-10 h-[720px] w-[710px] -translate-x-1/2">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full"
            src={frameUrl}
          />

          <h1
            className="absolute top-[11.5%] left-1/2 -translate-x-1/2 text-[31px] whitespace-nowrap text-[#3a7d2c]"
            style={jua}
          >
            {isEditMode ? '부모 프로필 수정' : '부모 프로필 만들기'}
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

          <div className="absolute top-[76%] right-[14%] left-[14%] h-0.5 rounded-sm bg-[rgba(122,100,60,.22)]" />
          <img
            alt=""
            className="absolute top-[78%] left-[27%] size-[88px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_3px_#d9c79a]"
            src={childAvatarUrl}
          />
          <div
            className="absolute top-[77.5%] left-[42%] text-[27px] text-[#3a7d2c]"
            style={jua}
          >
            아이 프로필
          </div>
          <button
            className="absolute top-[80.5%] left-[37.5%] flex h-[78px] w-[276px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[17px] text-white active:translate-y-0.5"
            onClick={() => navigate('/profiles/children')}
            style={{
              ...jua,
              backgroundImage: `url(${completeButtonUrl})`,
              paintOrder: 'stroke fill',
              WebkitTextStroke: '3px #4a7d1e',
            }}
            type="button"
          >
            아이 프로필 목록
          </button>
        </main>

        <button
          className="absolute right-[25px] bottom-[85px] z-40 h-[86px] w-[245px] bg-[length:100%_100%] bg-center bg-no-repeat text-[21px] text-white active:translate-y-0.5"
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

        {showChildForm ? (
          <div className="absolute inset-0 z-60 flex items-start justify-center bg-[rgba(10,26,15,.5)]">
            <section className="relative mt-4 h-[640px] w-[500px]">
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full"
                src={portraitFrameUrl}
              />
              <h2
                className="absolute top-[12.5%] right-0 left-0 text-center text-[27px] text-[#3a7d2c]"
                style={jua}
              >
                아이 프로필 생성
              </h2>

              <div className="absolute top-[20.8%] left-[22%] size-[120px] overflow-hidden rounded-full bg-[#fdf6e3] shadow-[0_0_0_4px_#d9c79a]">
                {isCameraOn ? (
                  <video
                    autoPlay
                    className="size-full -scale-x-100 object-cover"
                    muted
                    playsInline
                    ref={videoRef}
                  />
                ) : (
                  <img
                    alt="아이 프로필"
                    className="size-full object-cover"
                    src={childPhotoUrl ?? childAvatarUrl}
                  />
                )}
              </div>

              <div className="absolute top-[18.2%] right-[18%] flex flex-col">
                <button
                  className="h-16 w-[200px] bg-[length:100%_100%] bg-center bg-no-repeat text-[16px] text-[#5a4a2a] active:translate-y-0.5"
                  onClick={() => void toggleCamera()}
                  style={{ ...jua, backgroundImage: `url(${photoButtonUrl})` }}
                  type="button"
                >
                  {isCameraOn ? '📸 사진 찍기' : '웹캠 촬영'}
                </button>
                <button
                  className="-mt-5 h-16 w-[200px] bg-[length:100%_100%] bg-center bg-no-repeat text-[16px] text-[#5a4a2a] active:translate-y-0.5"
                  onClick={cycleDefaultChildPhoto}
                  style={{ ...jua, backgroundImage: `url(${photoButtonUrl})` }}
                  type="button"
                >
                  기본 이미지
                </button>
              </div>

              <p
                className="absolute top-[42%] right-0 left-0 text-center text-[15px] text-[#8a7a55]"
                style={jua}
              >
                세 마리의 동물이 있어요! 눌러서 확인해보세요!
              </p>
              <label
                className="absolute top-[48.5%] left-[20%] text-[22px] text-[#3a7d2c]"
                htmlFor="child-profile-name"
                style={jua}
              >
                🌿 아이 이름
              </label>
              <div
                className="absolute top-[52.5%] right-[19.5%] left-[19.5%] h-[72px] -translate-x-1.5 bg-[length:100%_100%] bg-center bg-no-repeat"
                style={{ backgroundImage: `url(${inputBarUrl})` }}
              >
                <input
                  className="absolute inset-y-1.5 right-5 left-5 bg-transparent text-center text-[19px] text-[#5a4a2a] outline-none placeholder:text-[#b9a97f]"
                  id="child-profile-name"
                  onChange={(event) => {
                    const nextName = event.target.value
                    setChildName(nextName.slice(0, 15))
                    setChildFormMessage(
                      nextName.length > 15 ? '이름은 15자까지 가능해요' : '',
                    )
                  }}
                  placeholder="이름을 입력해 주세요"
                  style={jua}
                  value={childName}
                />
              </div>
              <p
                className="absolute top-[62%] right-0 left-0 text-center text-[14px] text-[#c7563a]"
                style={jua}
              >
                {isChildProfileLimitReached
                  ? `아이 프로필은 최대 ${MAX_CHILD_PROFILES}개까지 만들 수 있어요.`
                  : childFormMessage}
              </p>
              <button
                className="absolute top-[65.5%] left-1/2 h-[84px] w-[370px] -translate-x-1/2 bg-[length:100%_100%] bg-center bg-no-repeat text-[20px] text-white active:translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={isChildProfileLimitReached}
                onClick={() => void createChildProfile()}
                style={{
                  ...jua,
                  backgroundImage: `url(${completeButtonUrl})`,
                  paintOrder: 'stroke fill',
                  WebkitTextStroke: '3px #4a7d1e',
                }}
                type="button"
              >
                {isChildProfileLimitReached
                  ? '프로필 10개 생성 완료'
                  : '아이 프로필 만들기'}
              </button>
              <button
                className="absolute top-[74.5%] left-1/2 h-[78px] w-[265px] -translate-x-1/2 bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-[#5a4a2a] active:translate-y-0.5"
                onClick={closeChildForm}
                style={{
                  ...jua,
                  backgroundImage: `url(${photoButtonUrl})`,
                }}
                type="button"
              >
                취소
              </button>
            </section>
          </div>
        ) : null}
      </div>
    </div>
  )
}
