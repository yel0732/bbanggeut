import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import {
  getStoredAccessToken,
  hasPendingNewSignupOnboarding,
  isPinLockedError,
  isPinMismatchError,
  logout,
  verifyParentPin,
} from '@/api/auth'
import { getChildProfiles as getBackendChildProfiles } from '@/api/profiles'
import backgroundUrl from '@/assets/images/profile/background.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import logoutButtonUrl from '@/assets/images/profile/profile-switch-button.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { PinInputDialog } from '@/components/profile/PinInputDialog'
import { useAdventureBgm } from '@/hooks/useAdventureBgm'
import { useFitScale } from '@/hooks/useFitScale'
import {
  childPhotoFromImageKey,
  loadActiveChildProfileId,
  loadChildProfiles,
  saveActiveChildProfileId,
  saveChildProfiles,
} from '@/lib/childProfiles'
import { grantParentAccess, revokeParentAccess } from '@/lib/parentAccess'

type ProfileSelectLocationState = {
  openParentPin?: boolean
  parentRedirectTo?: string
}

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PIN_LENGTH = 4
const MAX_PIN_FAILS = 5

function formatPinLockTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes === 0) return `${seconds}초`
  if (seconds === 0) return `${minutes}분`
  return `${minutes}분 ${seconds}초`
}
export function ProfileSelectPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as ProfileSelectLocationState | null
  const canvasRef = useFitScale<HTMLDivElement>()
  const { isBgmPlaying, toggleBgm } = useAdventureBgm()
  const [childProfiles, setChildProfiles] = useState(loadChildProfiles)
  const [activeChildProfileId, setActiveChildProfileId] = useState(
    loadActiveChildProfileId,
  )
  const hasTwoChildRows = childProfiles.length > 5
  const pinTimersRef = useRef<number[]>([])
  const [showPin, setShowPin] = useState(Boolean(locationState?.openParentPin))
  const [pin, setPin] = useState('')
  const [pinMessage, setPinMessage] = useState('')
  const [isPinError, setIsPinError] = useState(false)
  const [pinFailCount, setPinFailCount] = useState(0)
  const [isPinLocked, setIsPinLocked] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const lockTimerRef = useRef<number | null>(null)
  const pinLockUntilRef = useRef(0)

  useEffect(() => {
    revokeParentAccess()
  }, [])

  useEffect(() => {
    if (hasPendingNewSignupOnboarding()) {
      navigate('/profiles/new', { replace: true })
      return
    }
    if (!getStoredAccessToken()) {
      navigate('/', { replace: true })
    }
  }, [navigate])

  useEffect(() => {
    if (!getStoredAccessToken()) return
    let active = true
    void getBackendChildProfiles()
      .then((children) => {
        if (!active) return
        const localProfiles = loadChildProfiles()
        const serverProfiles = children.map((child) => {
          const local = localProfiles.find(
            (profile) => profile.id === String(child.childId),
          )
          return {
            id: String(child.childId),
            name: child.name,
            photoUrl:
              child.profileImageUrl ??
              local?.photoUrl ??
              childPhotoFromImageKey(child.profileImageKey),
            profileImageKey: child.profileImageKey,
            profileImageUrl: child.profileImageUrl,
          }
        })
        setChildProfiles(serverProfiles)
        // ChildRouteGuard는 이 캐시에 고른 아이가 들어 있어야 /child 진입을 허용한다.
        // 여기서 저장하지 않으면 부모 모드를 거치기 전까지 캐시가 비어 있거나 이전
        // 계정의 목록으로 남아, 아이를 골라도 프로필 선택 화면으로 되돌아온다.
        saveChildProfiles(serverProfiles)
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [])

  const handleLogout = async () => {
    if (isLoggingOut) return
    setIsLoggingOut(true)
    setLogoutError('')

    try {
      await logout()
      navigate('/', { replace: true })
    } catch (error) {
      setLogoutError(
        error instanceof Error ? error.message : '로그아웃에 실패했어요.',
      )
    } finally {
      setIsLoggingOut(false)
    }
  }

  useEffect(() => {
    return () => {
      if (lockTimerRef.current) {
        window.clearInterval(lockTimerRef.current)
      }
      pinTimersRef.current.forEach(window.clearTimeout)
      pinTimersRef.current = []
    }
  }, [])

  const clearLockTimer = () => {
    if (lockTimerRef.current) {
      window.clearInterval(lockTimerRef.current)
      lockTimerRef.current = null
    }
  }

  const clearPinTimers = () => {
    pinTimersRef.current.forEach(window.clearTimeout)
    pinTimersRef.current = []
  }

  const openPin = () => {
    clearPinTimers()
    setPin('')
    const secondsLeft = Math.ceil((pinLockUntilRef.current - Date.now()) / 1000)
    if (secondsLeft > 0) {
      setPinMessage(
        `잠겼어요. ${formatPinLockTime(secondsLeft)} 후 다시 시도하세요.`,
      )
      setIsPinError(true)
    } else {
      setPinMessage('')
      setIsPinError(false)
    }
    setShowPin(true)
  }

  const closePin = () => {
    clearPinTimers()
    setShowPin(false)
    setPin('')
  }

  const startPinLock = (retryAfterSeconds: number) => {
    clearLockTimer()
    setIsPinLocked(true)
    setPin('')
    setIsPinError(true)

    const lockUntil = Date.now() + Math.max(1, retryAfterSeconds) * 1000
    pinLockUntilRef.current = lockUntil
    const updateLockMessage = () => {
      const secondsLeft = Math.ceil((lockUntil - Date.now()) / 1000)
      if (secondsLeft <= 0) {
        clearLockTimer()
        pinLockUntilRef.current = 0
        setIsPinLocked(false)
        setPinFailCount(0)
        setIsPinError(false)
        setPinMessage('')
      } else {
        setPinMessage(
          `잠겼어요. ${formatPinLockTime(secondsLeft)} 후 다시 시도하세요.`,
        )
      }
    }
    updateLockMessage()
    lockTimerRef.current = window.setInterval(updateLockMessage, 1000)
  }

  const pressDigit = (digit: string) => {
    if (isPinLocked) return
    if (pin.length >= PIN_LENGTH) return
    const next = pin + digit
    setPin(next)
    setPinMessage('')
    setIsPinError(false)

    if (next.length === PIN_LENGTH) {
      const confirmTimerId = window.setTimeout(() => {
        void verifyParentPin(next)
          .then(() => {
            grantParentAccess()
            setIsPinError(false)
            setPinMessage('확인되었습니다 ✓')
            const navigateTimerId = window.setTimeout(() => {
              navigate(locationState?.parentRedirectTo ?? '/parent', {
                replace: Boolean(locationState?.openParentPin),
              })
            }, 500)
            pinTimersRef.current.push(navigateTimerId)
            setPinFailCount(0)
          })
          .catch((error: unknown) => {
            if (isPinLockedError(error)) {
              startPinLock(error.retryAfterSeconds ?? 1)
              return
            }

            const nextFailCount = pinFailCount + 1
            setPinFailCount(nextFailCount)
            setIsPinError(true)
            if (
              isPinMismatchError(error) &&
              typeof error.remainingAttempts === 'number'
            ) {
              setPinMessage(
                `비밀번호가 틀렸어요. 남은 기회 ${error.remainingAttempts}회`,
              )
            } else {
              setPinMessage(
                `비밀번호가 틀렸어요. (${nextFailCount}/${MAX_PIN_FAILS})`,
              )
            }
            setPin('')
          })
      }, 150)
      pinTimersRef.current.push(confirmTimerId)
    }
  }

  const pressBackspace = () => {
    if (isPinLocked) return
    setPin((value) => value.slice(0, -1))
    setPinMessage('')
    setIsPinError(false)
  }

  useEffect(() => {
    if (!showPin) return

    const handlePinKeyboard = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) {
        event.preventDefault()
        pressDigit(event.key)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        pressBackspace()
      }
    }

    window.addEventListener('keydown', handlePinKeyboard)
    return () => window.removeEventListener('keydown', handlePinKeyboard)
  })

  if (hasPendingNewSignupOnboarding()) {
    return <Navigate replace to="/profiles/new" />
  }

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        ref={canvasRef}
        className="relative h-[720px] w-[1280px] overflow-hidden text-white"
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(0,0,0,0)_55%,rgba(20,45,20,0.35)_100%)]" />

        <button
          aria-label="로그아웃"
          className="absolute top-[21px] right-[25px] z-40 flex h-[46px] w-[210px] items-center justify-center border-0 bg-transparent p-0 text-[26px] text-white drop-shadow-[0_4px_8px_rgba(0,0,0,.25)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[.98] disabled:cursor-wait disabled:opacity-70"
          disabled={isLoggingOut}
          onClick={() => void handleLogout()}
          style={jua}
          type="button"
        >
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full object-fill"
            decoding="async"
            draggable={false}
            src={logoutButtonUrl}
          />
          <span className="relative z-[1] pb-[1px] drop-shadow-[0_2px_2px_rgba(62,83,23,.55)]">
            {isLoggingOut ? '나가는 중...' : '로그아웃'}
          </span>
        </button>
        {logoutError ? (
          <p
            className="absolute top-[72px] right-[25px] z-40 w-[210px] rounded-xl bg-[#8d453d]/90 px-3 py-2 text-center text-[14px] text-white shadow-lg"
            role="alert"
            style={jua}
          >
            {logoutError}
          </p>
        ) : null}

        <div
          className="absolute top-[60px] right-0 left-0 z-10 text-center text-[53px] text-white [text-shadow:0_6px_14px_rgba(0,0,0,0.28)]"
          style={{
            ...jua,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '7px #3c7d38',
          }}
        >
          누가 놀러 왔어요?
        </div>

        <div className="absolute top-[140px] right-[120px] bottom-[78px] left-[120px] z-10 flex items-center justify-center gap-7">
          <div
            className={
              hasTwoChildRows
                ? 'grid grid-cols-5 gap-x-6 gap-y-5'
                : 'grid auto-cols-[150px] grid-flow-col gap-x-6'
            }
          >
            {childProfiles.map((profile) => {
              const isActive = activeChildProfileId === profile.id

              return (
                <button
                  aria-pressed={isActive}
                  className="relative flex w-[150px] flex-col items-center gap-3.5 border-none bg-none p-0 transition hover:-translate-y-1.5"
                  key={profile.id}
                  onClick={() => {
                    saveActiveChildProfileId(profile.id)
                    setActiveChildProfileId(profile.id)
                    navigate('/child')
                  }}
                  type="button"
                >
                  <div
                    className={`size-[130px] overflow-hidden rounded-full border-[7px] bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,0.3)] transition hover:scale-105 ${
                      isActive
                        ? 'border-[#dfff76] ring-[7px] ring-[#6eaa3f]'
                        : 'border-white'
                    }`}
                  >
                    <img
                      alt={profile.name}
                      className="h-full w-full object-cover"
                      decoding="async"
                      src={profile.photoUrl ?? childAvatarUrl}
                    />
                  </div>
                  {isActive ? (
                    <span
                      className="absolute -top-3 left-1/2 z-10 -translate-x-1/2 rounded-full bg-[#65983e] px-3 py-0.5 text-[14px] whitespace-nowrap text-white shadow-md"
                      style={jua}
                    >
                      현재 프로필
                    </span>
                  ) : null}
                  <span
                    className="text-[26px] text-white"
                    style={{
                      ...jua,
                      paintOrder: 'stroke fill',
                      WebkitTextStroke: '5px #3c7d38',
                    }}
                  >
                    {profile.name}
                  </span>
                </button>
              )
            })}
          </div>

          <button
            className="flex w-[150px] shrink-0 flex-col items-center gap-3.5 border-none bg-none p-0 transition hover:-translate-y-1.5"
            onClick={openPin}
            type="button"
          >
            <div className="size-[130px] overflow-hidden rounded-full border-[7px] border-[#ffe6a6] bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,0.3)] transition hover:scale-105">
              <img
                alt="부모"
                className="h-full w-full object-cover"
                decoding="async"
                src={parentAvatarUrl}
              />
            </div>
            <span
              className="flex items-center gap-1.5 text-[26px] text-white"
              style={{
                ...jua,
                paintOrder: 'stroke fill',
                WebkitTextStroke: '5px #8a5a1e',
              }}
            >
              부모 🔒
            </span>
          </button>
        </div>

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

        {showPin ? (
          <PinInputDialog
            description="4자리 비밀번호를 입력하세요"
            disabled={isPinLocked}
            message={pinMessage}
            messageTone={
              !pinMessage ? 'default' : isPinError ? 'error' : 'success'
            }
            onClose={closePin}
            onPressKey={(key) =>
              key === '←' ? pressBackspace() : pressDigit(key)
            }
            pin={pin}
            pinLength={PIN_LENGTH}
            title="부모 확인"
          />
        ) : null}
      </div>
    </div>
  )
}
