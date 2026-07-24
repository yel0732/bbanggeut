import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import {
  getChildReports,
  getStoredAccessToken,
  isBackendChildId,
  subscribeToReportNotifications,
} from '@/api/parentMode'
import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import deleteButtonUrl from '@/assets/images/parent-profile/btn-delete.webp'
import editButtonUrl from '@/assets/images/parent-profile/btn-edit.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import inputBarUrl from '@/assets/images/parent-profile/input-bar.webp'
import keypadButtonUrl from '@/assets/images/parent-profile/keypad-panel.webp'
import kidCellUrl from '@/assets/images/parent-profile/kid-cell.webp'
import pagerLeftUrl from '@/assets/images/parent-profile/pager-left.webp'
import pagerPageUrl from '@/assets/images/parent-profile/pager-page.webp'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import backgroundUrl from '@/assets/images/parent-mode/background.webp'
import menuBoardUrl from '@/assets/images/parent-mode/menu-board.webp'
import profileMenuButtonUrl from '@/assets/images/parent-mode/profile-menu-button.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import {
  loadChildProfiles,
  MAX_CHILD_PROFILES,
  saveChildProfiles,
  type ChildProfile,
} from '@/lib/childProfiles'

type PopupType = 'children' | 'profile' | 'notification' | null
type ProfileView = 'menu' | 'photo' | 'pin' | 'password'
type PinStep = 'current' | 'new' | 'confirm'
type PasswordErrors = {
  current: string
  next: string
  confirm: string
}

type NotificationSettings = {
  enabled: boolean
  time: string
  timezone: string
}

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PARENT_PHOTO_KEY = 'bbanggut-parent-photo'
const NOTIFICATION_KEY = 'bbanggut-report-notification'
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '←']
const ALLOWED_PASSWORD_SPECIALS = `!"#$%&'()*+,-./:;<=>?@[\\]₩^_\`{|}~`
const PASSWORD_GUIDE =
  '8~16자의 영문, 숫자, 특수문자를 각각 1자 이상 포함해 주세요.'

const DEMO_PROFILES: ChildProfile[] = [
  { id: 'demo-1', name: '아이1', photoUrl: null },
  { id: 'demo-2', name: '아이2', photoUrl: null },
  { id: 'demo-3', name: '아이3', photoUrl: null },
]

function loadNotificationSettings(): NotificationSettings {
  try {
    const stored = window.localStorage.getItem(NOTIFICATION_KEY)
    if (!stored) {
      return { enabled: true, time: '20:00', timezone: 'Asia/Seoul' }
    }
    const parsed = JSON.parse(stored) as Partial<NotificationSettings>
    return {
      enabled: parsed.enabled ?? true,
      time: parsed.time ?? '20:00',
      timezone: parsed.timezone ?? 'Asia/Seoul',
    }
  } catch {
    return { enabled: true, time: '20:00', timezone: 'Asia/Seoul' }
  }
}

function validatePassword(password: string) {
  if (password.length < 8 || password.length > 16) {
    return '새 비밀번호는 8~16자로 입력해 주세요.'
  }
  for (const character of password) {
    if (
      !/[A-Za-z0-9]/.test(character) &&
      !ALLOWED_PASSWORD_SPECIALS.includes(character)
    ) {
      return '영문, 숫자와 안내된 특수문자만 사용할 수 있어요.'
    }
  }
  if (!/[A-Za-z]/.test(password)) {
    return '영문을 1자 이상 포함해 주세요.'
  }
  if (!/[0-9]/.test(password)) {
    return '숫자를 1자 이상 포함해 주세요.'
  }
  if (
    ![...password].some((character) =>
      ALLOWED_PASSWORD_SPECIALS.includes(character),
    )
  ) {
    return '특수문자를 1자 이상 포함해 주세요.'
  }
  return null
}

export function ParentModePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const photoInputRef = useRef<HTMLInputElement>(null)
  const [popup, setPopup] = useState<PopupType>(null)
  const [profileView, setProfileView] = useState<ProfileView>('menu')
  const [profiles, setProfiles] = useState(() => {
    const stored = loadChildProfiles()
    return stored.length ? stored : DEMO_PROFILES
  })
  const [parentPhoto, setParentPhoto] = useState<string | null>(() =>
    window.localStorage.getItem(PARENT_PHOTO_KEY),
  )
  const [pin, setPin] = useState('')
  const [pinStep, setPinStep] = useState<PinStep>('current')
  const [newPin, setNewPin] = useState('')
  const [pinMessage, setPinMessage] = useState(
    '현재 핀 번호 4자리를 입력해 주세요.',
  )
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordErrors, setPasswordErrors] = useState<PasswordErrors>({
    current: '',
    next: '',
    confirm: '',
  })
  const [passwordSuccess, setPasswordSuccess] = useState('')
  const [notification, setNotification] = useState(loadNotificationSettings)
  const [savedMessage, setSavedMessage] = useState('')
  const [reportCounts, setReportCounts] = useState<Record<string, number>>({})
  const [notificationToast, setNotificationToast] = useState('')
  const [childListPage, setChildListPage] = useState(0)

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setPopup(null)
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    const backendProfiles = profiles.filter((profile) =>
      isBackendChildId(profile.id),
    )
    if (!backendProfiles.length || !getStoredAccessToken()) {
      return () => controller.abort()
    }

    void Promise.allSettled(
      backendProfiles.map(async (profile) => {
        const response = await getChildReports(Number(profile.id), {
          page: 0,
          size: 1,
          signal: controller.signal,
        })
        return [profile.id, response.page.totalElements] as const
      }),
    ).then((results) => {
      if (controller.signal.aborted) return
      setReportCounts(
        Object.fromEntries(
          results.flatMap((result) =>
            result.status === 'fulfilled' ? [result.value] : [],
          ),
        ),
      )
    })

    return () => controller.abort()
  }, [profiles])

  useEffect(() => {
    if (!getStoredAccessToken()) return
    const controller = new AbortController()

    void subscribeToReportNotifications((event) => {
      setNotificationToast(event.message)
      window.setTimeout(() => setNotificationToast(''), 4500)
    }, controller.signal).catch(() => {
      // 인증/서버가 준비되지 않은 개발 환경에서는 홈 기능을 계속 제공한다.
    })

    return () => controller.abort()
  }, [])

  const openPopup = (nextPopup: Exclude<PopupType, null>) => {
    setSavedMessage('')
    if (nextPopup === 'profile') setProfileView('menu')
    if (nextPopup === 'children') setChildListPage(0)
    setPopup(nextPopup)
  }

  const selectParentPhoto = (file: File | undefined) => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result !== 'string') return
      setParentPhoto(reader.result)
      window.localStorage.setItem(PARENT_PHOTO_KEY, reader.result)
    }
    reader.readAsDataURL(file)
  }

  const useDefaultPhoto = () => {
    setParentPhoto(null)
    window.localStorage.removeItem(PARENT_PHOTO_KEY)
  }

  const resetPin = () => {
    setPin('')
    setNewPin('')
    setPinStep('current')
    setPinMessage('현재 핀 번호 4자리를 입력해 주세요.')
  }

  const pressPinKey = (key: string) => {
    if (key === '←') {
      setPin((value) => value.slice(0, -1))
      return
    }
    if (!/^\d$/.test(key) || pin.length >= 4) return

    const nextPin = pin + key
    setPin(nextPin)
    if (nextPin.length < 4) return

    if (pinStep === 'current') {
      window.setTimeout(() => {
        setPin('')
        setPinStep('new')
        setPinMessage('변경할 새 핀 번호를 입력해 주세요.')
      }, 180)
      return
    }

    if (pinStep === 'new') {
      window.setTimeout(() => {
        setNewPin(nextPin)
        setPin('')
        setPinStep('confirm')
        setPinMessage('새 핀 번호를 한 번 더 입력해 주세요.')
      }, 180)
      return
    }

    if (nextPin === newPin) {
      setPinMessage('핀 번호가 변경되었어요 ✓')
      return
    }

    setPinMessage('새 핀 번호가 달라요. 다시 입력해 주세요.')
    window.setTimeout(() => {
      setPin('')
      setNewPin('')
      setPinStep('new')
    }, 450)
  }

  useEffect(() => {
    if (popup !== 'profile' || profileView !== 'pin') return

    const handlePinKeyboard = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) {
        event.preventDefault()
        pressPinKey(event.key)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        pressPinKey('←')
      }
    }
    window.addEventListener('keydown', handlePinKeyboard)
    return () => window.removeEventListener('keydown', handlePinKeyboard)
  })

  const changePassword = () => {
    const errors: PasswordErrors = { current: '', next: '', confirm: '' }
    if (!currentPassword) {
      errors.current = '현재 비밀번호를 입력해 주세요.'
    }
    const validationMessage = validatePassword(newPassword)
    if (validationMessage) {
      errors.next = validationMessage
    }
    if (newPassword !== confirmPassword) {
      errors.confirm = '비밀번호 확인이 일치하지 않아요.'
    }
    setPasswordErrors(errors)
    setPasswordSuccess('')
    if (Object.values(errors).some(Boolean)) {
      return
    }

    // 변경 API 연결 후 현재 비밀번호 불일치 응답은 errors.current에 매핑한다.
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setPasswordSuccess('비밀번호 변경 요청을 확인했어요 ✓')
  }

  const saveNotification = () => {
    window.localStorage.setItem(NOTIFICATION_KEY, JSON.stringify(notification))
    setSavedMessage('알림 설정을 저장했어요.')
    window.setTimeout(() => setPopup(null), 500)
  }

  const closePopup = () => {
    setPopup(null)
    setProfileView('menu')
    setSavedMessage('')
  }

  const updateChildProfiles = (nextProfiles: ChildProfile[]) => {
    setProfiles(nextProfiles)
    saveChildProfiles(nextProfiles)
  }

  const editChildProfile = (profile: ChildProfile) => {
    const nextName = window.prompt(
      '변경할 아이 이름을 입력해 주세요.',
      profile.name,
    )
    if (nextName === null) return
    const trimmedName = nextName.trim()
    if (!trimmedName) {
      window.alert('아이 이름을 입력해 주세요.')
      return
    }
    if (trimmedName.length > 15) {
      window.alert('이름은 15자까지 가능해요.')
      return
    }
    updateChildProfiles(
      profiles.map((item) =>
        item.id === profile.id ? { ...item, name: trimmedName } : item,
      ),
    )
  }

  const removeChildProfile = (profile: ChildProfile) => {
    if (!window.confirm(`${profile.name} 프로필을 삭제할까요?`)) return
    updateChildProfiles(profiles.filter((item) => item.id !== profile.id))
  }

  const childPageCount = Math.max(1, Math.ceil(profiles.length / 3))
  const currentChildPage = Math.min(childListPage, childPageCount - 1)
  const visibleChildProfiles = profiles.slice(
    currentChildPage * 3,
    currentChildPage * 3 + 3,
  )

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] shrink-0 origin-top overflow-hidden"
        ref={canvasRef}
      >
        <audio
          aria-label="부모 모드 배경 음악"
          autoPlay
          loop
          preload="auto"
          ref={audioRef}
          src={bgmUrl}
        />
        <img
          alt=""
          className="absolute inset-0 size-full object-cover"
          fetchPriority="high"
          src={backgroundUrl}
        />

        <Link
          aria-label="프로필 선택으로 돌아가기"
          className="absolute top-4 left-[78px] z-30 size-[58px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
          to="/profiles"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>

        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-3 left-[78px] z-40 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-106"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <nav className="absolute top-[22px] right-[70px] z-40 flex gap-3">
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('children')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            아이 프로필 관리
          </button>
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('profile')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            내 프로필 관리
          </button>
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('notification')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            보고서 알림 설정
          </button>
        </nav>

        <h1
          className="absolute top-[120px] right-0 left-0 z-10 text-center text-[40px] text-white [-webkit-text-stroke:6px_#3c7d38] [paint-order:stroke_fill]"
          style={jua}
        >
          리포트를 볼 아이를 선택하세요
        </h1>

        <section className="absolute top-[56%] left-1/2 z-10 flex -translate-1/2 flex-wrap items-center justify-center gap-x-14 gap-y-10">
          {profiles.map((profile) => (
            <button
              className="group relative flex w-[150px] flex-col items-center gap-3.5 transition hover:-translate-y-1.5"
              key={profile.id}
              onClick={() =>
                navigate(`/reports?childId=${encodeURIComponent(profile.id)}`)
              }
              type="button"
            >
              <span className="size-[150px] overflow-hidden rounded-full border-[7px] border-[#ffe6a6] bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,.3)] transition group-hover:scale-105">
                <img
                  alt={`${profile.name} 프로필`}
                  className="size-full object-cover"
                  src={profile.photoUrl ?? childAvatarUrl}
                />
              </span>
              <span
                className="text-[26px] whitespace-nowrap text-white [-webkit-text-stroke:5px_#3c7d38] [paint-order:stroke_fill]"
                style={jua}
              >
                {profile.name}
              </span>
              {reportCounts[profile.id] !== undefined ? (
                <span
                  className="absolute -top-2 -right-2 rounded-full border-2 border-white bg-[#6fae43] px-2.5 py-1 text-[14px] text-white shadow-md"
                  style={jua}
                >
                  리포트 {reportCounts[profile.id]}개
                </span>
              ) : null}
            </button>
          ))}
        </section>

        {notificationToast ? (
          <div
            aria-live="polite"
            className="absolute right-[70px] bottom-8 z-50 max-w-[360px] rounded-2xl border-4 border-[#d9c79a] bg-[#fff9e9] px-6 py-4 text-[18px] text-[#5a4a2a] shadow-[0_10px_24px_rgba(0,0,0,.28)]"
            style={jua}
          >
            🔔 {notificationToast}
          </div>
        ) : null}

        {popup === 'children' ? (
          <div
            className="absolute inset-0 z-60 flex items-center justify-center bg-[rgba(18,30,18,.58)]"
            onClick={closePopup}
          >
            <section
              aria-label="아이 프로필 목록"
              className="relative -mt-6 h-[672px] w-[640px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
              onClick={(event) => event.stopPropagation()}
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={frameUrl}
              />
              <button
                aria-label="닫기"
                className="absolute top-[7%] right-[8%] z-20 size-[48px] bg-transparent p-0 transition hover:scale-108"
                onClick={closePopup}
                type="button"
              >
                <img
                  alt=""
                  className="size-full object-contain"
                  src={closeButtonUrl}
                />
              </button>
              <h2
                className="absolute top-[10.5%] right-0 left-0 text-center text-[32px] text-[#3a7d2c]"
                style={jua}
              >
                아이 프로필 목록
              </h2>
              <p
                className="absolute top-[12%] right-[18%] text-[16px] text-[#8a7a55]"
                style={jua}
              >
                {profiles.length}/{MAX_CHILD_PROFILES}
              </p>

              <div className="absolute top-[22%] right-[12%] left-[13%] flex flex-col">
                {visibleChildProfiles.length ? (
                  visibleChildProfiles.map((profile) => (
                    <article
                      className="relative -mb-4 flex h-[145px] flex-none items-center"
                      key={profile.id}
                    >
                      <img
                        alt=""
                        className="pointer-events-none absolute inset-0 size-full"
                        src={kidCellUrl}
                      />
                      <div className="relative z-10 flex min-w-0 flex-1 items-center">
                        <img
                          alt={`${profile.name} 프로필`}
                          className="ml-[54px] size-[92px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_3px_#d9c79a]"
                          src={profile.photoUrl ?? childAvatarUrl}
                        />
                        <span
                          className="ml-[22px] max-w-[150px] truncate text-[29px] text-[#5a4a2a]"
                          style={jua}
                        >
                          {profile.name}
                        </span>
                      </div>
                      <div className="relative z-10 mr-[22px] flex flex-col">
                        <button
                          className="h-[68px] w-[168px] bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-[#7a5a1e] transition hover:scale-105"
                          onClick={() => editChildProfile(profile)}
                          style={{
                            ...jua,
                            backgroundImage: `url(${editButtonUrl})`,
                          }}
                          type="button"
                        >
                          수정
                        </button>
                        <button
                          className="-mt-[22px] h-[68px] w-[168px] bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-[#9a4a3e] transition hover:scale-105"
                          onClick={() => removeChildProfile(profile)}
                          style={{
                            ...jua,
                            backgroundImage: `url(${deleteButtonUrl})`,
                          }}
                          type="button"
                        >
                          삭제
                        </button>
                      </div>
                    </article>
                  ))
                ) : (
                  <p
                    className="mt-28 text-center text-[23px] text-[#8a7a55]"
                    style={jua}
                  >
                    아직 만든 아이 프로필이 없어요.
                  </p>
                )}
              </div>

              {profiles.length < MAX_CHILD_PROFILES ? (
                <button
                  className="absolute bottom-[14%] left-1/2 flex h-[76px] w-[260px] -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-white transition [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] hover:scale-103"
                  onClick={() => navigate('/profiles/new?add=1')}
                  style={{
                    ...jua,
                    backgroundImage: `url(${completeButtonUrl})`,
                  }}
                  type="button"
                >
                  + 새 프로필 생성
                </button>
              ) : (
                <p
                  className="absolute right-0 bottom-[18%] left-0 text-center text-[17px] text-[#8a7a55]"
                  style={jua}
                >
                  최대 10개까지 만들 수 있어요
                </p>
              )}

              <nav
                aria-label="아이 프로필 목록 페이지"
                className="absolute right-0 bottom-[5%] left-0 flex items-center justify-center gap-1.5"
              >
                <button
                  aria-label="이전 페이지"
                  className="size-[50px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
                  disabled={currentChildPage === 0}
                  onClick={() =>
                    setChildListPage((value) => Math.max(0, value - 1))
                  }
                  type="button"
                >
                  <img
                    alt=""
                    className="size-full object-contain"
                    src={pagerLeftUrl}
                  />
                </button>
                {Array.from({ length: childPageCount }, (_, index) => (
                  <button
                    aria-current={
                      index === currentChildPage ? 'page' : undefined
                    }
                    className={`relative size-[50px] bg-transparent p-0 transition hover:scale-105 ${
                      index === currentChildPage ? '' : 'opacity-50'
                    }`}
                    key={index}
                    onClick={() => setChildListPage(index)}
                    type="button"
                  >
                    <img
                      alt=""
                      className="absolute inset-0 size-full object-contain"
                      src={pagerPageUrl}
                    />
                    <span
                      className="absolute inset-0 flex items-center justify-center text-[21px] text-white"
                      style={jua}
                    >
                      {index + 1}
                    </span>
                  </button>
                ))}
                <button
                  aria-label="다음 페이지"
                  className="size-[50px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
                  disabled={currentChildPage >= childPageCount - 1}
                  onClick={() =>
                    setChildListPage((value) =>
                      Math.min(childPageCount - 1, value + 1),
                    )
                  }
                  type="button"
                >
                  <img
                    alt=""
                    className="size-full object-contain"
                    src={pagerRightUrl}
                  />
                </button>
              </nav>
            </section>
          </div>
        ) : null}

        {popup === 'profile' ? (
          <div
            className="absolute inset-0 z-60 flex items-center justify-center bg-[rgba(18,30,18,.58)]"
            onClick={closePopup}
          >
            <section
              aria-label="내 프로필 관리"
              className="relative -mt-20 h-[660px] w-[520px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
              onClick={(event) => event.stopPropagation()}
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={frameUrl}
              />
              <button
                aria-label="닫기"
                className="absolute top-[6%] right-[7%] z-10 size-[52px] bg-transparent p-0 transition hover:scale-108"
                onClick={closePopup}
                type="button"
              >
                <img
                  alt=""
                  className="size-full object-contain"
                  src={closeButtonUrl}
                />
              </button>
              <h2
                className={`absolute right-0 left-0 text-center text-[31px] text-[#3a7d2c] ${
                  profileView === 'menu' ? 'top-[10.5%]' : 'top-[12.5%]'
                }`}
                style={jua}
              >
                {profileView === 'menu'
                  ? '내 프로필 관리'
                  : profileView === 'photo'
                    ? '프로필 사진 관리'
                    : profileView === 'pin'
                      ? '핀 번호 변경'
                      : '계정 비밀번호 변경'}
              </h2>

              {profileView !== 'menu' ? (
                <button
                  aria-label="프로필 관리 메뉴로 돌아가기"
                  className="absolute top-[7%] left-[8%] z-10 size-[48px] bg-transparent p-0 transition hover:scale-108"
                  onClick={() => setProfileView('menu')}
                  type="button"
                >
                  <img
                    alt=""
                    className="size-full object-contain"
                    src={backButtonUrl}
                  />
                </button>
              ) : null}

              {profileView === 'menu' ? (
                <div className="absolute top-[18%] right-[17%] left-[17%] flex flex-col items-center">
                  <img
                    alt="부모 프로필"
                    className="mb-3 size-[132px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_5px_#d9c79a,0_7px_14px_rgba(0,0,0,.18)]"
                    src={parentPhoto ?? parentAvatarUrl}
                  />
                  {[
                    {
                      description: '내 사진을 추가하거나 기본 이미지로 바꿔요',
                      label: '프로필 사진 관리',
                      view: 'photo' as const,
                    },
                    {
                      description: '부모 모드에 사용하는 숫자 4자리를 바꿔요',
                      label: '핀 번호 변경',
                      view: 'pin' as const,
                    },
                    {
                      description: '로그인에 사용하는 비밀번호를 바꿔요',
                      label: '계정 비밀번호 변경',
                      view: 'password' as const,
                    },
                  ].map((item) => (
                    <button
                      className="relative -mb-0 h-[110px] w-[430px] bg-[length:100%_100%] bg-center bg-no-repeat text-center transition hover:scale-103"
                      key={item.view}
                      onClick={() => {
                        if (item.view === 'pin') resetPin()
                        setProfileView(item.view)
                      }}
                      style={{
                        ...jua,
                        backgroundImage: `url(${profileMenuButtonUrl})`,
                      }}
                      type="button"
                    >
                      <span className="block text-[23px] text-[#5f492a]">
                        {item.label}
                      </span>
                      <span className="mt-0.5 block text-[13px] text-[#82945a]">
                        {item.description}
                      </span>
                    </button>
                  ))}
                </div>
              ) : null}

              {profileView === 'photo' ? (
                <div className="absolute top-[24%] right-[18%] left-[18%] flex flex-col items-center">
                  <img
                    alt="부모 프로필"
                    className="size-[190px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_6px_#d9c79a,0_8px_18px_rgba(0,0,0,.2)]"
                    src={parentPhoto ?? parentAvatarUrl}
                  />
                  <p className="mt-5 text-[16px] text-[#8a7a55]" style={jua}>
                    부모 모드에서 사용할 프로필 사진을 골라 주세요.
                  </p>
                  <button
                    className="mt-4 h-[82px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] transition hover:scale-103"
                    onClick={() => photoInputRef.current?.click()}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    사진 추가
                  </button>
                  <button
                    className="-mt-6 h-[82px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] transition hover:scale-103"
                    onClick={useDefaultPhoto}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    기본 이미지로 변경
                  </button>
                  <input
                    accept="image/*"
                    className="hidden"
                    onChange={(event) =>
                      selectParentPhoto(event.target.files?.[0])
                    }
                    ref={photoInputRef}
                    type="file"
                  />
                </div>
              ) : null}

              {profileView === 'pin' ? (
                <div className="absolute top-[21%] right-[18%] left-[18%] text-center">
                  <p className="h-9 text-[19px] text-[#8a7a55]" style={jua}>
                    {pinMessage}
                  </p>
                  <div className="mt-0 flex justify-center gap-1">
                    {Array.from({ length: 4 }, (_, index) => (
                      <img
                        alt=""
                        className="size-[55px] object-contain"
                        key={index}
                        src={
                          index < pin.length ? pinDotFillUrl : pinDotEmptyUrl
                        }
                      />
                    ))}
                  </div>
                  <p className="mt-6 text-[15px] text-[#9b8862]" style={jua}>
                    화면 키패드 또는 키보드 숫자 키를 사용할 수 있어요.
                  </p>
                  <div className="mx-auto mt-2 grid w-[300px] -translate-x-2 grid-cols-3">
                    {KEYS.map((key, index) =>
                      key ? (
                        <button
                          className="-m-3 mx-auto h-[85px] w-[120px] bg-[length:100%_100%] bg-center bg-no-repeat text-[24px] text-[#3a7d2c] active:scale-95"
                          key={key}
                          onClick={() => pressPinKey(key)}
                          style={{
                            ...jua,
                            backgroundImage: `url(${keypadButtonUrl})`,
                          }}
                          type="button"
                        >
                          {key}
                        </button>
                      ) : (
                        <span key={`empty-${index}`} />
                      ),
                    )}
                  </div>
                  <button
                    className="mt-4 text-[15px] text-[#8a7a55] underline"
                    onClick={resetPin}
                    style={jua}
                    type="button"
                  >
                    처음부터 다시 입력
                  </button>
                </div>
              ) : null}

              {profileView === 'password' ? (
                <div className="absolute top-[24%] right-[21%] left-[21%]">
                  <p
                    className="mb-4 text-center text-[15.5px] text-[#8a7a55]"
                    style={jua}
                  >
                    현재 비밀번호를 확인한 뒤 새 비밀번호로 변경해요.
                  </p>
                  {[
                    {
                      autoComplete: 'current-password',
                      error: passwordErrors.current,
                      field: 'current' as const,
                      placeholder: '현재 비밀번호',
                      setter: setCurrentPassword,
                      value: currentPassword,
                    },
                    {
                      autoComplete: 'new-password',
                      error: passwordErrors.next,
                      field: 'next' as const,
                      placeholder: '새 비밀번호',
                      setter: setNewPassword,
                      value: newPassword,
                    },
                    {
                      autoComplete: 'new-password',
                      error: passwordErrors.confirm,
                      field: 'confirm' as const,
                      placeholder: '새 비밀번호 확인',
                      setter: setConfirmPassword,
                      value: confirmPassword,
                    },
                  ].map(
                    ({
                      autoComplete,
                      error,
                      field,
                      placeholder,
                      setter,
                      value,
                    }) => (
                      <label className="mb-2 block" key={field}>
                        <input
                          aria-invalid={Boolean(error)}
                          autoComplete={autoComplete}
                          className={`h-[54px] w-full rounded-xl border-2 bg-[#fdf6e3] px-4 text-[20px] text-[#5a4a2a] outline-none placeholder:text-[#b9a97f] ${
                            error ? 'border-[#c7563a]' : 'border-[#cdbb92]'
                          }`}
                          onChange={(event) => {
                            setter(event.target.value)
                            setPasswordErrors((current) => ({
                              ...current,
                              [field]: '',
                            }))
                            setPasswordSuccess('')
                          }}
                          placeholder={placeholder}
                          style={jua}
                          type="password"
                          value={value}
                        />
                        <span
                          className="block h-[18px] px-1 text-[12px] leading-[18px] text-[#c7563a]"
                          style={jua}
                        >
                          {error}
                        </span>
                      </label>
                    ),
                  )}
                  <p
                    className={`h-9 text-center text-[18px] leading-[1.7] ${
                      passwordSuccess ? 'text-[#3a7d2c]' : 'text-[#8a7a55]'
                    }`}
                    style={jua}
                  >
                    {passwordSuccess || PASSWORD_GUIDE}
                  </p>
                  <button
                    className="mt-10 h-[88px] w-full bg-[length:100%_100%] bg-center bg-no-repeat text-[21px] text-white transition [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] hover:scale-102"
                    onClick={changePassword}
                    style={{
                      ...jua,
                      backgroundImage: `url(${completeButtonUrl})`,
                    }}
                    type="button"
                  >
                    비밀번호 변경
                  </button>
                </div>
              ) : null}
            </section>
          </div>
        ) : null}

        {popup === 'notification' ? (
          <div
            className="absolute inset-0 z-60 flex items-center justify-center bg-[rgba(18,30,18,.58)]"
            onClick={closePopup}
          >
            <section
              aria-label="보고서 알림 설정"
              className="relative -mt-16 h-[640px] w-[540px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
              onClick={(event) => event.stopPropagation()}
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={frameUrl}
              />
              <button
                aria-label="닫기"
                className="absolute top-[5.5%] right-[7%] z-10 size-[58px] bg-transparent p-0 transition hover:scale-108"
                onClick={closePopup}
                type="button"
              >
                <img
                  alt=""
                  className="size-full object-contain"
                  src={closeButtonUrl}
                />
              </button>
              <h2
                className="absolute top-[11%] right-0 left-0 text-center text-[34px] text-[#3a7d2c]"
                style={jua}
              >
                보고서 알림
              </h2>

              <div className="absolute top-[21%] right-[16.5%] left-[16.5%] flex items-center justify-between">
                <div>
                  <h3 className="text-[24px] text-[#5a4a2a]" style={jua}>
                    일일 리포트 알림
                  </h3>
                  <p className="mt-0.5 text-[14px] text-[#8a7a55]" style={jua}>
                    리포트가 준비되면 알려드려요.
                  </p>
                </div>
                <button
                  aria-pressed={notification.enabled}
                  className={`relative h-8 w-[60px] rounded-full transition ${
                    notification.enabled ? 'bg-[#7ec850]' : 'bg-[#cfcfcf]'
                  }`}
                  onClick={() =>
                    setNotification((current) => ({
                      ...current,
                      enabled: !current.enabled,
                    }))
                  }
                  type="button"
                >
                  <span
                    className={`absolute top-[3px] size-[26px] rounded-full bg-white shadow-[0_2px_4px_rgba(0,0,0,.25)] transition-all ${
                      notification.enabled ? 'left-[31px]' : 'left-[3px]'
                    }`}
                  />
                </button>
              </div>

              <label
                className="absolute top-[33%] right-[16.5%] left-[16.5%] text-[19px] text-[#8a7a55]"
                style={jua}
              >
                알림 받을 시각
                <span
                  className="relative -mt-1 block h-[70px] bg-[length:100%_100%] bg-center bg-no-repeat"
                  style={{ backgroundImage: `url(${inputBarUrl})` }}
                >
                  <input
                    className="absolute inset-y-1.5 right-5 left-8 bg-transparent text-[20px] text-[#5a4a2a] outline-none"
                    disabled={!notification.enabled}
                    onChange={(event) =>
                      setNotification((current) => ({
                        ...current,
                        time: event.target.value,
                      }))
                    }
                    style={jua}
                    type="time"
                    value={notification.time}
                  />
                </span>
              </label>

              <label
                className="absolute top-[50.5%] right-[16.5%] left-[16.5%] text-[19px] text-[#8a7a55]"
                style={jua}
              >
                기준 시간대
                <span
                  className="relative -mt-1 block h-[70px] bg-[length:100%_100%] bg-center bg-no-repeat"
                  style={{ backgroundImage: `url(${inputBarUrl})` }}
                >
                  <input
                    className="absolute inset-y-1.5 right-5 left-8 bg-transparent text-[20px] text-[#5a4a2a] outline-none"
                    onChange={(event) =>
                      setNotification((current) => ({
                        ...current,
                        timezone: event.target.value,
                      }))
                    }
                    style={jua}
                    value={notification.timezone}
                  />
                </span>
              </label>

              <div className="absolute top-[68.5%] right-[16.5%] left-[16.5%] rounded-[14px] bg-[rgba(126,200,80,.14)] px-4 py-3">
                <h3 className="text-[15px] text-[#5a7d3a]" style={jua}>
                  알림 개인정보 보호
                </h3>
                <p className="mt-1 text-[13px] text-[#8a7a55]" style={jua}>
                  알림에는 일기 내용·감정·점수 등 상세 정보를 표시하지 않습니다.
                </p>
              </div>

              <p
                aria-live="polite"
                className="absolute right-0 bottom-[16%] left-0 text-center text-[14px] text-[#3a7d2c]"
                style={jua}
              >
                {savedMessage}
              </p>
              <button
                className="absolute right-[16.5%] bottom-[7.5%] left-[16.5%] h-[90px] bg-[length:100%_100%] bg-center bg-no-repeat text-[23px] text-white [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] active:translate-y-0.5"
                onClick={saveNotification}
                style={{
                  ...jua,
                  backgroundImage: `url(${completeButtonUrl})`,
                }}
                type="button"
              >
                설정 저장
              </button>
            </section>
          </div>
        ) : null}
      </div>
    </div>
  )
}
