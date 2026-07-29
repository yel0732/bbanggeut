import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  changeAccountPassword,
  getStoredLoginMethod,
  isPinLockedError,
  isPinMismatchError,
  verifyParentPin,
  withdrawAccount,
} from '@/api/auth'

import {
  getChildReports,
  getReportNotificationPreference,
  getStoredAccessToken,
  isBackendChildId,
  subscribeToReportNotifications,
  updateReportNotificationPreference,
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
import { getVisiblePageIndices } from '@/utils/pagination'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import backgroundUrl from '@/assets/images/parent-mode/background.webp'
import menuBoardUrl from '@/assets/images/parent-mode/menu-board.webp'
import profileMenuWideUrl from '@/assets/images/parent-mode/profile-menu-wide-trimmed.png'
import reportNotificationButtonUrl from '@/assets/images/parent-mode/report-notification-button.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import parentAvatarUrl from '@/assets/images/profile/parent-avatar.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import { ChildProfileCreateDialog } from '@/components/profile/ChildProfileCreateDialog'
import { PinInputDialog } from '@/components/profile/PinInputDialog'
import {
  createChildProfile as createBackendChildProfile,
  changeParentPin,
  deleteChildProfile as deleteBackendChildProfile,
  getChildProfiles as getBackendChildProfiles,
  getParentProfile,
  updateParentProfileImage,
  updateChildProfile as updateBackendChildProfile,
  uploadChildProfileImage,
  uploadParentProfileImage,
} from '@/api/profiles'
import {
  childImageKeyFromPhoto,
  childPhotoFromImageKey,
  loadChildProfiles,
  MAX_CHILD_PROFILES,
  PARENT_DEFAULT_IMAGE_KEY,
  saveChildProfiles,
  type ChildProfile,
} from '@/lib/childProfiles'

type PopupType =
  'children' | 'child-create' | 'child-edit' | 'profile' | 'notification' | null
type ProfileView = 'menu' | 'photo' | 'pin' | 'password' | 'withdraw'
type PinStep = 'current' | 'new' | 'confirm'
type PasswordErrors = {
  current: string
  next: string
  confirm: string
}
type NoticeDialog = { kind: 'delete'; profile: ChildProfile } | null

type NotificationSettings = {
  enabled: boolean
  time: string
  timezone: string
}

type ReportArrivalNotification = {
  id: string
  childName: string
  reportDate: string
  read: boolean
}

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PARENT_PHOTO_KEY = 'bbanggut-parent-photo'
const NOTIFICATION_KEY = 'bbanggut-report-notification'
const REPORT_ARRIVALS_KEY = 'bbanggut-report-arrivals'
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '←']
const MAX_PIN_FAILS = 5
const ALLOWED_PASSWORD_SPECIALS = `!"#$%&'()*+,-./:;<=>?@[\\]₩^_\`{|}~`
const PASSWORD_GUIDE =
  '8~16자의 영문, 숫자, 특수문자를 각각 1자 이상 포함해 주세요.'

const DEMO_PROFILES: ChildProfile[] = [
  { id: 'demo-1', name: '아이1', photoUrl: null },
  { id: 'demo-2', name: '아이2', photoUrl: null },
  { id: 'demo-3', name: '아이3', photoUrl: null },
]

function formatPinLockTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}분 ${seconds}초`
}

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

function loadReportArrivals(): ReportArrivalNotification[] {
  try {
    const value = JSON.parse(
      window.localStorage.getItem(REPORT_ARRIVALS_KEY) ?? '[]',
    )
    return Array.isArray(value) ? value : []
  } catch {
    return []
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

function isDataImageUrl(value: string | null): value is string {
  return typeof value === 'string' && value.startsWith('data:image/')
}

async function dataImageUrlToBlob(dataUrl: string) {
  return await fetch(dataUrl).then((response) => response.blob())
}

export function ParentModePage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const isGoogleAccount = getStoredLoginMethod() === 'google'
  const parentVideoRef = useRef<HTMLVideoElement>(null)
  const parentStreamRef = useRef<MediaStream | null>(null)
  const [popup, setPopup] = useState<PopupType>(null)
  const [profileView, setProfileView] = useState<ProfileView>('menu')
  const [profiles, setProfiles] = useState(() => {
    const stored = loadChildProfiles()
    return stored.length ? stored : DEMO_PROFILES
  })
  const [parentPhoto, setParentPhoto] = useState<string | null>(() =>
    window.localStorage.getItem(PARENT_PHOTO_KEY),
  )
  const [isParentCameraOn, setIsParentCameraOn] = useState(false)
  const [pin, setPin] = useState('')
  const [pinStep, setPinStep] = useState<PinStep>('current')
  const [verifiedCurrentPin, setVerifiedCurrentPin] = useState('')
  const [newPin, setNewPin] = useState('')
  const [pinMessage, setPinMessage] = useState(
    '현재 핀 번호 4자리를 입력해 주세요.',
  )
  const [pinFailCount, setPinFailCount] = useState(0)
  const [isPinLocked, setIsPinLocked] = useState(false)
  const pinLockTimerRef = useRef<number | null>(null)
  const pinLockUntilRef = useRef(0)
  const [withdrawPin, setWithdrawPin] = useState('')
  const [firstWithdrawPin, setFirstWithdrawPin] = useState('')
  const [withdrawMessage, setWithdrawMessage] =
    useState('핀 번호를 입력해 주세요.')
  const [isWithdrawPinLocked, setIsWithdrawPinLocked] = useState(false)
  const withdrawLockTimerRef = useRef<number | null>(null)
  const [isVerifyingWithdrawal, setIsVerifyingWithdrawal] = useState(false)
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
  const [reportArrivals, setReportArrivals] = useState(loadReportArrivals)
  const [isArrivalListOpen, setIsArrivalListOpen] = useState(false)
  const [childListPage, setChildListPage] = useState(0)
  const [editingChildProfile, setEditingChildProfile] =
    useState<ChildProfile | null>(null)
  const [noticeDialog, setNoticeDialog] = useState<NoticeDialog>(null)
  const [isChildDeletionPinOpen, setIsChildDeletionPinOpen] = useState(false)
  const [childDeletionPin, setChildDeletionPin] = useState('')
  const [childDeletionMessage, setChildDeletionMessage] = useState('')
  const [isChildDeletionPinLocked, setIsChildDeletionPinLocked] =
    useState(false)
  const childDeletionLockTimerRef = useRef<number | null>(null)
  const [isDeletingChildProfile, setIsDeletingChildProfile] = useState(false)

  useEffect(() => {
    const video = parentVideoRef.current
    const stream = parentStreamRef.current
    if (!isParentCameraOn || !video || !stream) return

    video.srcObject = stream
    void video.play()
  }, [isParentCameraOn])

  useEffect(
    () => () => {
      parentStreamRef.current?.getTracks().forEach((track) => track.stop())
      if (pinLockTimerRef.current) {
        window.clearInterval(pinLockTimerRef.current)
      }
      if (withdrawLockTimerRef.current) {
        window.clearInterval(withdrawLockTimerRef.current)
      }
      if (childDeletionLockTimerRef.current) {
        window.clearInterval(childDeletionLockTimerRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    if (!getStoredAccessToken()) return
    let active = true
    void getParentProfile()
      .then((profile) => {
        if (!active) return
        setParentPhoto(profile.profileImageUrl)
        if (profile.profileImageUrl) {
          window.localStorage.removeItem(PARENT_PHOTO_KEY)
        }
      })
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!getStoredAccessToken()) return
    let active = true
    void getBackendChildProfiles()
      .then((children) => {
        if (!active) return
        const localProfiles = loadChildProfiles()
        const nextProfiles = children.map((child) => {
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
        updateChildProfiles(nextProfiles)
      })
      .catch((error: unknown) => {
        if (!active) return
        setNotificationToast(
          error instanceof Error
            ? error.message
            : '아이 프로필을 불러오지 못했어요.',
        )
      })
    return () => {
      active = false
    }
  }, [])

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

    void getReportNotificationPreference(controller.signal)
      .then((preference) => {
        setNotification(preference)
        window.localStorage.setItem(
          NOTIFICATION_KEY,
          JSON.stringify(preference),
        )
      })
      .catch(() => {
        // Keep the last local preference while the server is unavailable.
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!getStoredAccessToken() || !notification.enabled) return
    const controller = new AbortController()

    void subscribeToReportNotifications((event) => {
      const childName = event.childName.trim()
      const message = childName
        ? `${childName}이의 일일 리포트가 도착했어요!`
        : event.message
      setNotificationToast(message)
      window.setTimeout(() => setNotificationToast(''), 4500)
      if (event.type !== 'REPORT_COMPLETED' || !childName) return
      setReportArrivals((current) => {
        const nextItem: ReportArrivalNotification = {
          id: event.id || `${childName}-${event.reportDate}`,
          childName,
          reportDate: event.reportDate,
          read: false,
        }
        const next = [
          nextItem,
          ...current.filter((item) => item.id !== nextItem.id),
        ].slice(0, 30)
        window.localStorage.setItem(REPORT_ARRIVALS_KEY, JSON.stringify(next))
        return next
      })
    }, controller.signal).catch(() => {
      // 인증/서버가 준비되지 않은 개발 환경에서는 홈 기능을 계속 제공한다.
    })

    return () => controller.abort()
  }, [notification.enabled])

  const openArrivedReport = (arrival: ReportArrivalNotification) => {
    const profile = profiles.find(
      (item) =>
        item.name.trim().toLocaleLowerCase('ko-KR') ===
        arrival.childName.trim().toLocaleLowerCase('ko-KR'),
    )
    if (!profile) {
      setNotificationToast(`${arrival.childName} 프로필을 찾을 수 없어요.`)
      return
    }
    const next = reportArrivals.map((item) =>
      item.id === arrival.id ? { ...item, read: true } : item,
    )
    setReportArrivals(next)
    window.localStorage.setItem(REPORT_ARRIVALS_KEY, JSON.stringify(next))
    navigate(
      `/reports?childId=${encodeURIComponent(profile.id)}&childName=${encodeURIComponent(profile.name)}&reportDate=${encodeURIComponent(arrival.reportDate)}`,
    )
  }

  const openPopup = (nextPopup: Exclude<PopupType, null>) => {
    setSavedMessage('')
    if (nextPopup === 'profile') setProfileView('menu')
    if (nextPopup === 'children') setChildListPage(0)
    setPopup(nextPopup)
  }

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
        setNotificationToast('웹캠을 사용할 수 없어요.')
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
        setParentPhoto(nextPhoto)
        window.localStorage.setItem(PARENT_PHOTO_KEY, nextPhoto)
      }
    }
    stopParentCamera()
  }

  const useDefaultPhoto = () => {
    stopParentCamera()
    setParentPhoto(null)
    window.localStorage.removeItem(PARENT_PHOTO_KEY)
  }

  const saveParentPhoto = async () => {
    try {
      const result = isDataImageUrl(parentPhoto)
        ? await uploadParentProfileImage(await dataImageUrlToBlob(parentPhoto))
        : await updateParentProfileImage(PARENT_DEFAULT_IMAGE_KEY)
      setParentPhoto(result.profileImageUrl)
      window.localStorage.removeItem(PARENT_PHOTO_KEY)
      setNotificationToast('프로필 사진을 저장했어요.')
    } catch (error) {
      setNotificationToast(
        error instanceof Error
          ? error.message
          : '프로필 사진을 저장하지 못했어요.',
      )
    }
  }

  const resetPin = () => {
    setPin('')
    setVerifiedCurrentPin('')
    setNewPin('')
    setPinStep('current')
    const secondsLeft = Math.ceil((pinLockUntilRef.current - Date.now()) / 1000)
    setPinMessage(
      secondsLeft > 0
        ? `PIN 입력이 잠겼습니다.\n${formatPinLockTime(secondsLeft)} 후 다시 시도해 주세요.`
        : '현재 핀 번호 4자리를 입력해 주세요.',
    )
  }

  const clearPinLockTimer = () => {
    if (pinLockTimerRef.current) {
      window.clearInterval(pinLockTimerRef.current)
      pinLockTimerRef.current = null
    }
  }

  const startPinLock = (retryAfterSeconds: number) => {
    clearPinLockTimer()
    setIsPinLocked(true)
    setPin('')
    const lockUntil = Date.now() + Math.max(1, retryAfterSeconds) * 1000
    pinLockUntilRef.current = lockUntil

    const updateLockMessage = () => {
      const secondsLeft = Math.ceil((lockUntil - Date.now()) / 1000)
      if (secondsLeft <= 0) {
        clearPinLockTimer()
        pinLockUntilRef.current = 0
        setIsPinLocked(false)
        setPinFailCount(0)
        setPinMessage('현재 핀 번호 4자리를 입력해 주세요.')
        return
      }
      setPinMessage(
        `PIN 입력이 잠겼습니다.\n${formatPinLockTime(secondsLeft)} 후 다시 시도해 주세요.`,
      )
    }

    updateLockMessage()
    pinLockTimerRef.current = window.setInterval(updateLockMessage, 1000)
  }

  const clearWithdrawLockTimer = () => {
    if (withdrawLockTimerRef.current) {
      window.clearInterval(withdrawLockTimerRef.current)
      withdrawLockTimerRef.current = null
    }
  }

  const startWithdrawPinLock = (retryAfterSeconds: number) => {
    clearWithdrawLockTimer()
    setIsWithdrawPinLocked(true)
    setWithdrawPin('')
    const lockUntil = Date.now() + Math.max(1, retryAfterSeconds) * 1000

    const updateLockMessage = () => {
      const secondsLeft = Math.ceil((lockUntil - Date.now()) / 1000)
      if (secondsLeft <= 0) {
        clearWithdrawLockTimer()
        setIsWithdrawPinLocked(false)
        setWithdrawMessage('핀 번호를 입력해 주세요.')
        return
      }
      setWithdrawMessage(
        `PIN 입력이 잠겼습니다.\n${formatPinLockTime(secondsLeft)} 후 다시 시도해 주세요.`,
      )
    }

    updateLockMessage()
    withdrawLockTimerRef.current = window.setInterval(updateLockMessage, 1000)
  }

  const clearChildDeletionLockTimer = () => {
    if (childDeletionLockTimerRef.current) {
      window.clearInterval(childDeletionLockTimerRef.current)
      childDeletionLockTimerRef.current = null
    }
  }

  const startChildDeletionPinLock = (retryAfterSeconds: number) => {
    clearChildDeletionLockTimer()
    setIsChildDeletionPinLocked(true)
    setChildDeletionPin('')
    const lockUntil = Date.now() + Math.max(1, retryAfterSeconds) * 1000

    const updateLockMessage = () => {
      const secondsLeft = Math.ceil((lockUntil - Date.now()) / 1000)
      if (secondsLeft <= 0) {
        clearChildDeletionLockTimer()
        setIsChildDeletionPinLocked(false)
        setChildDeletionMessage('')
        return
      }
      setChildDeletionMessage(
        `PIN 입력이 잠겼습니다.\n${formatPinLockTime(secondsLeft)} 후 다시 시도해 주세요.`,
      )
    }

    updateLockMessage()
    childDeletionLockTimerRef.current = window.setInterval(
      updateLockMessage,
      1000,
    )
  }

  const resetWithdrawal = () => {
    setWithdrawPin('')
    setFirstWithdrawPin('')
    setWithdrawMessage('핀 번호를 입력해 주세요.')
    setIsVerifyingWithdrawal(false)
  }

  const pressWithdrawalPinKey = (key: string) => {
    if (isVerifyingWithdrawal || isWithdrawPinLocked) return
    if (key === '←') {
      setWithdrawPin((value) => value.slice(0, -1))
      return
    }
    if (!/^\d$/.test(key) || withdrawPin.length >= 4) return
    const nextPin = withdrawPin + key
    setWithdrawPin(nextPin)
    if (nextPin.length < 4) return

    if (!firstWithdrawPin) {
      window.setTimeout(() => {
        setFirstWithdrawPin(nextPin)
        setWithdrawPin('')
        setWithdrawMessage('확인을 위해 같은 핀 번호를 한 번 더 입력해 주세요.')
      }, 180)
      return
    }

    if (nextPin !== firstWithdrawPin) {
      setWithdrawMessage('핀 번호가 서로 달라요. 처음부터 다시 입력해 주세요.')
      window.setTimeout(resetWithdrawal, 700)
      return
    }

    setIsVerifyingWithdrawal(true)
    void withdrawAccount(nextPin)
      .then(() => {
        setWithdrawMessage('회원 탈퇴가 완료됐어요.')
        window.setTimeout(() => navigate('/', { replace: true }), 650)
      })
      .catch((error: unknown) => {
        if (isPinLockedError(error)) {
          setFirstWithdrawPin('')
          startWithdrawPinLock(error.retryAfterSeconds ?? 1)
          return
        }

        if (
          isPinMismatchError(error) &&
          typeof error.remainingAttempts === 'number'
        ) {
          setWithdrawMessage(
            `핀 번호가 올바르지 않아요.\n남은 기회 ${error.remainingAttempts}회`,
          )
        } else {
          setWithdrawMessage(
            error instanceof Error
              ? error.message
              : '핀 번호가 올바르지 않아요.',
          )
        }
        window.setTimeout(resetWithdrawal, 900)
      })
      .finally(() => setIsVerifyingWithdrawal(false))
  }

  const pressPinKey = (key: string) => {
    if (isPinLocked) return
    if (key === '←') {
      setPin((value) => value.slice(0, -1))
      return
    }
    if (!/^\d$/.test(key) || pin.length >= 4) return

    const nextPin = pin + key
    setPin(nextPin)
    if (nextPin.length < 4) return

    if (pinStep === 'current') {
      void verifyParentPin(nextPin)
        .then(() => {
          setPinFailCount(0)
          setVerifiedCurrentPin(nextPin)
          setPin('')
          setPinStep('new')
          setPinMessage('변경할 새 PIN 번호를 입력해 주세요.')
        })
        .catch((error: unknown) => {
          setPin('')
          if (isPinLockedError(error)) {
            startPinLock(error.retryAfterSeconds ?? 1)
            return
          }

          const nextFailCount = pinFailCount + 1
          setPinFailCount(nextFailCount)
          if (
            isPinMismatchError(error) &&
            typeof error.remainingAttempts === 'number'
          ) {
            setPinMessage(
              `PIN 번호가 틀렸습니다.\n남은 기회 ${error.remainingAttempts}회`,
            )
          } else {
            setPinMessage(
              `PIN 번호가 틀렸습니다. (${nextFailCount}/${MAX_PIN_FAILS})`,
            )
          }
        })
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
      void changeParentPin(verifiedCurrentPin, newPin)
        .then(() => setPinMessage('PIN 번호가 변경되었어요.'))
        .catch((error: unknown) => {
          setPin('')
          setVerifiedCurrentPin('')
          setNewPin('')
          setPinStep('current')
          setPinMessage(
            error instanceof Error
              ? error.message
              : 'PIN 번호를 변경하지 못했어요.',
          )
        })
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
    if (
      popup !== 'profile' ||
      (profileView !== 'pin' && profileView !== 'withdraw')
    )
      return

    const handlePinKeyboard = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) {
        event.preventDefault()
        if (profileView === 'withdraw') pressWithdrawalPinKey(event.key)
        else pressPinKey(event.key)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        if (profileView === 'withdraw') pressWithdrawalPinKey('←')
        else pressPinKey('←')
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
    void changeAccountPassword(currentPassword, newPassword)
      .then(() => {
        setCurrentPassword('')
        setNewPassword('')
        setConfirmPassword('')
        setPasswordSuccess('비밀번호가 변경되었어요.')
      })
      .catch((error: unknown) => {
        setPasswordErrors({
          current:
            error instanceof Error
              ? error.message
              : '현재 비밀번호를 확인해 주세요.',
          next: '',
          confirm: '',
        })
      })
  }

  const saveNotification = () => {
    void updateReportNotificationPreference(notification)
      .then((saved) => {
        setNotification(saved)
        window.localStorage.setItem(NOTIFICATION_KEY, JSON.stringify(saved))
        setSavedMessage('알림 설정을 저장했어요.')
        window.setTimeout(() => setPopup(null), 500)
      })
      .catch((error: unknown) => {
        setSavedMessage(
          error instanceof Error
            ? error.message
            : '알림 설정을 저장하지 못했어요.',
        )
      })
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
    setEditingChildProfile(profile)
    setPopup('child-edit')
  }

  const removeChildProfile = (profile: ChildProfile) => {
    setIsChildDeletionPinOpen(false)
    setChildDeletionPin('')
    setChildDeletionMessage('')
    setNoticeDialog({ kind: 'delete', profile })
  }

  const closeChildProfileRemoval = () => {
    if (isDeletingChildProfile) return
    clearChildDeletionLockTimer()
    setIsChildDeletionPinLocked(false)
    setNoticeDialog(null)
    setIsChildDeletionPinOpen(false)
    setChildDeletionPin('')
    setChildDeletionMessage('')
  }

  const openChildDeletionPin = () => {
    if (isChildDeletionPinLocked) return
    setChildDeletionPin('')
    setChildDeletionMessage('')
    setIsChildDeletionPinOpen(true)
  }

  const confirmChildProfileRemoval = (deletionPin: string) => {
    if (!noticeDialog || noticeDialog.kind !== 'delete') return
    const profileId = noticeDialog.profile.id
    setIsDeletingChildProfile(true)
    setChildDeletionMessage('')

    void deleteBackendChildProfile(Number(profileId), deletionPin)
      .then(() => {
        updateChildProfiles(profiles.filter((item) => item.id !== profileId))
        setNoticeDialog(null)
        setIsChildDeletionPinOpen(false)
        setChildDeletionPin('')
        setChildDeletionMessage('')
      })
      .catch((error: unknown) => {
        setChildDeletionPin('')
        if (isPinLockedError(error)) {
          startChildDeletionPinLock(error.retryAfterSeconds ?? 1)
          return
        }

        if (
          isPinMismatchError(error) &&
          typeof error.remainingAttempts === 'number'
        ) {
          setChildDeletionMessage(
            `PIN 번호가 틀렸습니다.\n남은 기회 ${error.remainingAttempts}회`,
          )
        } else {
          setChildDeletionMessage(
            error instanceof Error
              ? error.message
              : '프로필을 삭제하지 못했어요.',
          )
        }
      })
      .finally(() => setIsDeletingChildProfile(false))
  }

  const pressChildDeletionPinKey = (key: string) => {
    if (isDeletingChildProfile || isChildDeletionPinLocked) return
    if (key === '←') {
      setChildDeletionPin((value) => value.slice(0, -1))
      setChildDeletionMessage('')
      return
    }
    if (!/^\d$/.test(key) || childDeletionPin.length >= 4) return

    const nextPin = childDeletionPin + key
    setChildDeletionPin(nextPin)
    setChildDeletionMessage('')
    if (nextPin.length === 4) confirmChildProfileRemoval(nextPin)
  }

  useEffect(() => {
    if (!isChildDeletionPinOpen) return

    const handleChildDeletionPinKeyboard = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) {
        event.preventDefault()
        pressChildDeletionPinKey(event.key)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        pressChildDeletionPinKey('←')
      }
    }

    window.addEventListener('keydown', handleChildDeletionPinKeyboard)
    return () =>
      window.removeEventListener('keydown', handleChildDeletionPinKeyboard)
  })

  const childPageCount = Math.max(1, Math.ceil(profiles.length / 3))
  const currentChildPage = Math.min(childListPage, childPageCount - 1)
  const visibleChildProfiles = profiles.slice(
    currentChildPage * 3,
    currentChildPage * 3 + 3,
  )

  return (
    <div className="flex h-screen w-screen items-center justify-center overflow-hidden bg-[radial-gradient(120%_120%_at_50%_20%,_#12241a_0%,_#0b140e_100%)]">
      <div
        className="relative h-[720px] w-[1280px] shrink-0 overflow-hidden"
        ref={canvasRef}
      >
        <audio
          aria-label="부모 모드 배경 음악"
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
          className="absolute top-[24px] left-[24px] z-30 size-[62px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-108"
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
          className="absolute bottom-[25px] left-[25px] z-40 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-106"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <div className="absolute bottom-[90px] left-[25px] z-50">
          <button
            aria-expanded={isArrivalListOpen}
            aria-label="도착한 일일 리포트 알림"
            className="relative size-[62px] bg-transparent p-0 transition hover:scale-106"
            onClick={() => setIsArrivalListOpen((value) => !value)}
            type="button"
          >
            <img
              alt=""
              className="size-full scale-[1.62] object-cover drop-shadow-[0_4px_5px_rgba(0,0,0,.3)]"
              src={reportNotificationButtonUrl}
            />
            {reportArrivals.some((item) => !item.read) ? (
              <span
                className="absolute top-0.5 right-0.5 flex min-h-5 min-w-5 items-center justify-center rounded-full border-2 border-white bg-[#ef694f] px-1 text-[12px] leading-none text-white shadow"
                style={jua}
              >
                {Math.min(
                  99,
                  reportArrivals.filter((item) => !item.read).length,
                )}
              </span>
            ) : null}
          </button>

          {isArrivalListOpen ? (
            <section
              aria-label="리포트 알림 목록"
              className="absolute bottom-0 left-[76px] w-[360px] rounded-[22px] border-4 border-[#d8bd79] bg-[#fff9e9] p-4 shadow-[0_12px_30px_rgba(0,0,0,.32)]"
            >
              <div className="mb-2 flex items-center justify-between px-1">
                <h2 className="text-[23px] text-[#4f6f35]" style={jua}>
                  도착한 일일 리포트
                </h2>
                <button
                  aria-label="알림 목록 닫기"
                  className="text-[20px] text-[#806d47]"
                  onClick={() => setIsArrivalListOpen(false)}
                  type="button"
                >
                  ×
                </button>
              </div>
              <div className="max-h-[250px] space-y-2 overflow-y-auto pr-1">
                {reportArrivals.length ? (
                  reportArrivals.map((arrival) => (
                    <button
                      className={`w-full rounded-[15px] border-2 px-4 py-3 text-left text-[16px] transition hover:-translate-y-0.5 hover:bg-[#eef6d9] ${
                        arrival.read
                          ? 'border-[#dfd5b9] bg-[#faf5e8] text-[#756a51]'
                          : 'border-[#aac86b] bg-[#f5fbdc] text-[#4e6434]'
                      }`}
                      key={arrival.id}
                      onClick={() => openArrivedReport(arrival)}
                      style={jua}
                      type="button"
                    >
                      <span className="block">
                        {arrival.childName}이의 일일 리포트가 도착했어요!
                      </span>
                      <span className="mt-1 block text-[13px] opacity-70">
                        {arrival.reportDate}
                      </span>
                    </button>
                  ))
                ) : (
                  <p
                    className="py-8 text-center text-[18px] text-[#8a7a55]"
                    style={jua}
                  >
                    새로 도착한 리포트가 없어요.
                  </p>
                )}
              </div>
            </section>
          ) : null}
        </div>

        <nav className="absolute top-[25px] right-[10px] z-40 flex gap-3">
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('children')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            아이 프로필 관리
          </button>
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('profile')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            내 프로필 관리
          </button>
          <button
            className="flex h-[58px] w-[220px] items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#f7ecd2] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
            onClick={() => openPopup('notification')}
            style={{ ...jua, backgroundImage: `url(${menuBoardUrl})` }}
            type="button"
          >
            보고서 알림 설정
          </button>
        </nav>

        <h1
          className="absolute top-[110px] right-0 left-0 z-10 text-center text-[50px] text-white [-webkit-text-stroke:6px_#3c7d38] [paint-order:stroke_fill]"
          style={jua}
        >
          리포트를 볼 아이를 선택하세요
        </h1>

        <section className="absolute top-[130px] right-[120px] bottom-[78px] left-[120px] z-10 flex flex-wrap content-center items-center justify-center gap-x-14 gap-y-7">
          {profiles.map((profile) => (
            <button
              className="group relative flex w-[150px] flex-col items-center gap-3.5 transition hover:-translate-y-1.5"
              key={profile.id}
              onClick={() =>
                navigate(
                  `/reports?childId=${encodeURIComponent(profile.id)}&childName=${encodeURIComponent(profile.name)}`,
                )
              }
              type="button"
            >
              <span className="size-[130px] overflow-hidden rounded-full border-[7px] border-[#ffe6a6] bg-[#eaf4d8] shadow-[0_12px_26px_rgba(0,0,0,.3)] transition group-hover:scale-105">
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

        {popup === 'children' || popup === 'child-edit' ? (
          <div
            className="absolute inset-0 z-60 flex items-center justify-center bg-[rgba(18,30,18,.58)]"
            onClick={closePopup}
          >
            <section
              aria-label="아이 프로필 목록"
              className="relative h-[672px] w-[640px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
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
                className="absolute top-[11.8%] right-[18%] text-[17px] text-[#8a7a55]"
                style={jua}
              >
                {profiles.length}/{MAX_CHILD_PROFILES}
              </p>

              <div className="absolute top-[23.2%] right-[12%] left-[13%] flex flex-col">
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
                      <div className="relative z-10 flex min-w-0 flex-1 -translate-y-0.5 items-center">
                        <img
                          alt={`${profile.name} 프로필`}
                          className="ml-[54px] size-[92px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_3px_#d9c79a]"
                          src={profile.photoUrl ?? childAvatarUrl}
                        />
                        <span
                          className="ml-[22px] truncate text-[33px] text-[#5a4a2a]"
                          style={jua}
                        >
                          {profile.name}
                        </span>
                      </div>
                      <div className="relative z-10 mr-[50px] flex -translate-y-0.5 flex-col">
                        <button
                          className="-mt-[2px] pt-[3px] h-[50px] w-[150px] bg-[length:100%_100%] bg-center bg-no-repeat text-[25px] text-[#7a5a1e] transition hover:scale-105"
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
                          className="pt-[3px] mt-[2px] h-[50px] w-[150px] bg-[length:100%_100%] bg-center bg-no-repeat text-[25px] text-[#9a4a3e] transition hover:scale-105"
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
                    className="mt-24 text-center text-[24px] text-[#8a7a55]"
                    style={jua}
                  >
                    아직 만든 아이 프로필이 없어요.
                  </p>
                )}
              </div>

              {profiles.length < MAX_CHILD_PROFILES ? (
                <button
                  className="absolute bottom-[74%] left-5/7 flex h-18 w-60 -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[23px] text-white transition [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] hover:scale-103"
                  onClick={() => setPopup('child-create')}
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
                  className="absolute right-0 bottom-[75.9%] left-0 flex h-14 items-center justify-center text-center text-[20px] text-white"
                  style={{
                    ...jua,
                    paintOrder: 'stroke fill',
                    WebkitTextStroke: '3px #8fac68',
                  }}
                >
                  최대 10개까지 만들 수 있어요
                </p>
              )}

              <nav
                aria-label="아이 프로필 목록 페이지"
                className="absolute right-0 bottom-[8.7%] left-0 flex items-center justify-center gap-1.5"
              >
                <button
                  aria-label="이전 페이지"
                  className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
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
                {getVisiblePageIndices(currentChildPage, childPageCount).map(
                  (index) => (
                    <button
                      aria-current={
                        index === currentChildPage ? 'page' : undefined
                      }
                      className={`relative size-[54px] bg-transparent p-0 transition hover:scale-105 ${
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
                        className="absolute inset-0 flex items-center justify-center text-[24px] text-white"
                        style={{
                          ...jua,
                          paintOrder: 'stroke fill',
                          WebkitTextStroke: '2px #3f7d1e',
                        }}
                      >
                        {index + 1}
                      </span>
                    </button>
                  ),
                )}
                <button
                  aria-label="다음 페이지"
                  className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
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

        {popup === 'child-create' ? (
          <ChildProfileCreateDialog
            onBack={() => setPopup('children')}
            onCreate={(profile) => {
              const imageKey = childImageKeyFromPhoto(profile.photoUrl)
              void createBackendChildProfile(profile.name, imageKey)
                .then(async (created) => {
                  const saved = isDataImageUrl(profile.photoUrl)
                    ? await uploadChildProfileImage(
                        created.childId,
                        await dataImageUrlToBlob(profile.photoUrl),
                      )
                    : created
                  updateChildProfiles([
                    ...profiles,
                    {
                      ...profile,
                      id: String(saved.childId),
                      photoUrl:
                        saved.profileImageUrl ??
                        childPhotoFromImageKey(saved.profileImageKey),
                      profileImageKey: saved.profileImageKey,
                      profileImageUrl: saved.profileImageUrl,
                    },
                  ])
                  setChildListPage(Math.floor(profiles.length / 3))
                  setPopup('children')
                })
                .catch((error: unknown) =>
                  setNotificationToast(
                    error instanceof Error
                      ? error.message
                      : '아이 프로필을 만들지 못했어요.',
                  ),
                )
            }}
            profiles={profiles}
          />
        ) : null}

        {popup === 'child-edit' && editingChildProfile ? (
          <ChildProfileCreateDialog
            initialProfile={editingChildProfile}
            onBack={() => {
              setEditingChildProfile(null)
              setPopup('children')
            }}
            onCreate={(updatedProfile) => {
              const childId = Number(updatedProfile.id)
              const imageKey = childImageKeyFromPhoto(
                updatedProfile.photoUrl,
                updatedProfile.profileImageKey,
              )
              void updateBackendChildProfile(
                childId,
                updatedProfile.name,
                imageKey,
              )
                .then(async (updated) => {
                  const saved = isDataImageUrl(updatedProfile.photoUrl)
                    ? await uploadChildProfileImage(
                        childId,
                        await dataImageUrlToBlob(updatedProfile.photoUrl),
                      )
                    : updated
                  updateChildProfiles(
                    profiles.map((profile) =>
                      profile.id === updatedProfile.id
                        ? {
                            ...updatedProfile,
                            photoUrl:
                              saved.profileImageUrl ??
                              childPhotoFromImageKey(saved.profileImageKey),
                            profileImageKey: saved.profileImageKey,
                            profileImageUrl: saved.profileImageUrl,
                          }
                        : profile,
                    ),
                  )
                  setEditingChildProfile(null)
                  setPopup('children')
                })
                .catch((error: unknown) =>
                  setNotificationToast(
                    error instanceof Error
                      ? error.message
                      : '아이 프로필을 수정하지 못했어요.',
                  ),
                )
            }}
            profiles={profiles}
          />
        ) : null}

        {popup === 'profile' ? (
          <div
            className="absolute inset-0 z-60 flex items-center justify-center bg-[rgba(18,30,18,.58)]"
            onClick={closePopup}
          >
            <section
              aria-label="내 프로필 관리"
              className="relative h-[660px] w-[520px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
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
                className={`absolute right-0 left-0 text-center text-[32px] text-[#3a7d2c] ${
                  profileView === 'menu' ? 'top-[11.5%]' : 'top-[12.5%]'
                }`}
                style={jua}
              >
                {profileView === 'menu'
                  ? '내 프로필 관리'
                  : profileView === 'photo'
                    ? '프로필 사진 관리'
                    : profileView === 'pin'
                      ? '핀 번호 변경'
                      : profileView === 'password'
                        ? '계정 비밀번호 변경'
                        : '회원 탈퇴'}
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
                <div className="absolute top-[20%] right-[17%] left-[17%] flex flex-col items-center">
                  <img
                    alt="부모 프로필"
                    className="mb-6 size-[108px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_5px_#d9c79a,0_7px_14px_rgba(0,0,0,.18)]"
                    src={parentPhoto ?? parentAvatarUrl}
                  />
                  {[
                    {
                      label: '프로필 사진 관리',
                      view: 'photo' as const,
                    },
                    {
                      label: '핀 번호 변경',
                      view: 'pin' as const,
                    },
                    {
                      label: '계정 비밀번호 변경',
                      view: 'password' as const,
                    },
                    {
                      label: '탈퇴하기',
                      view: 'withdraw' as const,
                    },
                  ].map((item) => (
                    <button
                      className="relative mb-[9px] h-[73px] w-[406px] shrink-0 bg-[length:100%_100%] bg-center bg-no-repeat text-center transition first:mt-[46px] hover:scale-103 disabled:cursor-not-allowed disabled:opacity-65 disabled:hover:scale-100"
                      disabled={item.view === 'password' && isGoogleAccount}
                      key={item.view}
                      onClick={() => {
                        if (item.view === 'password' && isGoogleAccount) return
                        if (item.view === 'pin') resetPin()
                        if (item.view === 'withdraw') resetWithdrawal()
                        setProfileView(item.view)
                      }}
                      style={{
                        ...jua,
                        backgroundImage: `url(${profileMenuWideUrl})`,
                      }}
                      type="button"
                    >
                      <span className={`${item.view === 'password' && isGoogleAccount ? '-mt-1 text-[22px]' : 'text-[25px]'} block text-[#5f492a]`}>
                        {item.label}
                      </span>
                      {item.view === 'password' && isGoogleAccount ? (
                        <span className="-mt-1 block text-[14px] text-[#82945a]">
                          구글로 로그인한 계정
                        </span>
                      ) : null}
                    </button>
                  ))}
                </div>
              ) : null}

              {profileView === 'photo' ? (
                <div className="absolute top-[22%] right-[18%] left-[18%] flex flex-col items-center">
                  {isParentCameraOn ? (
                    <video
                      autoPlay
                      className="size-[180px] -scale-x-100 rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_6px_#d9c79a,0_8px_18px_rgba(0,0,0,.2)]"
                      muted
                      playsInline
                      ref={parentVideoRef}
                    />
                  ) : (
                    <img
                      alt="부모 프로필"
                      className="size-[180px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_6px_#d9c79a,0_8px_18px_rgba(0,0,0,.2)]"
                      src={parentPhoto ?? parentAvatarUrl}
                    />
                  )}
                  <p className="mt-4 text-[18px] text-[#8a7a55]" style={jua}>
                    부모 모드에서 사용할 프로필 사진을 골라 주세요.
                  </p>
                  <button
                    className="-mt-1 h-[85px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[24px] text-[#5a4a2a] transition hover:scale-103"
                    onClick={() => void toggleParentCamera()}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    {isParentCameraOn ? '사진 찍기' : '웹캠 촬영'}
                  </button>
                  <button
                    className="-mt-6 h-[85px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[23px] text-[#5a4a2a] transition hover:scale-103"
                    onClick={useDefaultPhoto}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    기본 이미지로 변경
                  </button>
                  <button
                    className="-mt-1 h-[90px] w-[320px] bg-[length:100%_100%] bg-center bg-no-repeat text-[24px] text-white transition hover:scale-103"
                    onClick={() => void saveParentPhoto()}
                    style={{
                      ...jua,
                      backgroundImage: `url(${completeButtonUrl})`,
                      paintOrder: 'stroke fill',
                      WebkitTextStroke: '3px #4a7d1e',
                    }}
                    type="button"
                  >
                    프로필 저장하기
                  </button>
                </div>
              ) : null}

              {profileView === 'pin' ? (
                <div className="absolute top-[21%] right-[18%] left-[18%] text-center">
                  <p
                    className="h-14 text-[20px] leading-7 whitespace-pre-line text-[#8a7a55]"
                    style={jua}
                  >
                    {pinMessage}
                  </p>
                  <div className="mt-0 flex justify-center gap-1">
                    {Array.from({ length: 4 }, (_, index) => (
                      <img
                        alt=""
                        className="size-[57px] object-contain"
                        key={index}
                        src={
                          index < pin.length ? pinDotFillUrl : pinDotEmptyUrl
                        }
                      />
                    ))}
                  </div>
                  <p className="mt-5 text-[16px] text-[#9b8862]" style={jua}>
                    화면 키패드 또는 키보드 숫자 키를 사용할 수 있어요.
                  </p>
                  <div className="mx-auto mt-1 grid w-[320px] -translate-x-2 grid-cols-3">
                    {KEYS.map((key, index) =>
                      key ? (
                        <button
                          className="-m-3 mx-auto h-[90px] w-[120px] bg-[length:100%_100%] bg-center bg-no-repeat text-[28px] text-[#3a7d2c] active:scale-95"
                          disabled={isPinLocked}
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
                    className="mt-2 text-[17px] text-[#8a7a55] underline disabled:cursor-not-allowed disabled:opacity-40"
                    disabled={isPinLocked}
                    onClick={resetPin}
                    style={jua}
                    type="button"
                  >
                    처음부터 다시 입력
                  </button>
                </div>
              ) : null}

              {profileView === 'withdraw' ? (
                <div className="absolute top-[23%] right-[18%] left-[18%] text-center">
                  <p
                    className="h-12 text-[19px] leading-6 text-[#a34f3d]"
                    style={jua}
                  >
                    {withdrawMessage}
                  </p>
                  <div className="-gap-0 -mt-3 flex justify-center">
                    {Array.from({ length: 4 }, (_, index) => (
                      <img
                        alt=""
                        className="size-[59px] object-contain"
                        key={index}
                        src={
                          index < withdrawPin.length
                            ? pinDotFillUrl
                            : pinDotEmptyUrl
                        }
                      />
                    ))}
                  </div>
                  <p className="mt-2 text-[15px] text-[#8a7a55]" style={jua}>
                    탈퇴 확인을 위해 동일한 핀 번호를 두 번 입력해 주세요.
                  </p>
                  <div className="mx-auto mt-1 grid w-[300px] -translate-x-2 grid-cols-3">
                    {KEYS.map((key, index) =>
                      key ? (
                        <button
                          className="-m-3 mx-auto h-[90px] w-[120px] bg-[length:100%_100%] bg-center bg-no-repeat text-[28px] text-[#3a7d2c] active:scale-95 disabled:opacity-50"
                          disabled={
                            isVerifyingWithdrawal || isWithdrawPinLocked
                          }
                          key={key}
                          onClick={() => pressWithdrawalPinKey(key)}
                          style={{
                            ...jua,
                            backgroundImage: `url(${keypadButtonUrl})`,
                          }}
                          type="button"
                        >
                          {key}
                        </button>
                      ) : (
                        <span key={`withdraw-empty-${index}`} />
                      ),
                    )}
                  </div>
                  <button
                    className="mt-2 text-[17px] text-[#8a7a55] underline disabled:cursor-not-allowed disabled:opacity-40"
                    disabled={isWithdrawPinLocked}
                    onClick={resetWithdrawal}
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
                    className="mb-4 text-center text-[15.7px] text-[#8a7a55]"
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
                          className={`h-[54px] w-full rounded-xl border-2 bg-[#fdf6e3] px-4 text-[22px] text-[#5a4a2a] outline-none placeholder:text-[#b9a97f] ${
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
                    className={`h-9 text-center text-[20px] leading-[1.7] ${
                      passwordSuccess ? 'text-[#3a7d2c]' : 'text-[#8a7a55]'
                    }`}
                    style={jua}
                  >
                    {passwordSuccess || PASSWORD_GUIDE}
                  </p>
                  <button
                    className="mt-10 h-[88px] w-full bg-[length:100%_100%] bg-center bg-no-repeat text-[24px] text-white transition [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] hover:scale-102"
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
              className="relative h-[640px] w-[540px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
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

              <div className="absolute top-[65.5%] right-[16.5%] left-[16.5%] rounded-[14px] bg-[rgba(126,200,80,.14)] px-4 py-3">
                <h3 className="text-[16px] text-[#5a7d3a]" style={jua}>
                  알림 개인정보 보호
                </h3>
                <p className="mt-1 text-[13.5px] text-[#8a7a55]" style={jua}>
                  알림에는 일기 내용·감정·점수 등 상세 정보를 표시하지 않습니다.
                </p>
              </div>

              <p
                aria-live="polite"
                className="absolute right-0 bottom-[18%] left-0 text-center text-[14px] text-[#3a7d2c]"
                style={jua}
              >
                {savedMessage}
              </p>
              <button
                className="absolute right-[16.5%] bottom-[7.5%] left-[16.5%] h-[90px] bg-[length:100%_100%] bg-center bg-no-repeat text-[26px] text-white [-webkit-text-stroke:3px_#4a7d1e] [paint-order:stroke_fill] active:translate-y-0.5"
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
        {noticeDialog?.kind === 'delete' && !isChildDeletionPinOpen ? (
          <ParentNoticeDialog
            confirmLabel="PIN 입력"
            message={`${noticeDialog.profile.name} 프로필을 삭제할까요?`}
            onCancel={closeChildProfileRemoval}
            onConfirm={openChildDeletionPin}
            title="프로필 삭제"
          />
        ) : null}
        {noticeDialog?.kind === 'delete' && isChildDeletionPinOpen ? (
          <PinInputDialog
            closeDisabled={isDeletingChildProfile}
            description="삭제하려면 PIN 번호 4자리를 입력하세요"
            disabled={isDeletingChildProfile || isChildDeletionPinLocked}
            message={
              isDeletingChildProfile
                ? '확인하고 있어요...'
                : childDeletionMessage
            }
            messageTone={childDeletionMessage ? 'error' : 'default'}
            onClose={closeChildProfileRemoval}
            onPressKey={pressChildDeletionPinKey}
            pin={childDeletionPin}
            title="부모 확인"
          />
        ) : null}
      </div>
    </div>
  )
}
