import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/start/green-adventure-start.mp3'
import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import revisePopupUrl from '@/assets/images/parent-profile/child-profile-revise-popup.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import deleteButtonUrl from '@/assets/images/parent-profile/btn-delete.webp'
import editButtonUrl from '@/assets/images/parent-profile/btn-edit.webp'
import frameUrl from '@/assets/images/parent-profile/frame-square.webp'
import kidCellUrl from '@/assets/images/parent-profile/kid-cell.webp'
import pagerLeftUrl from '@/assets/images/parent-profile/pager-left.webp'
import pagerPageUrl from '@/assets/images/parent-profile/pager-page.webp'
import { getVisiblePageIndices } from '@/utils/pagination'
import pagerRightUrl from '@/assets/images/parent-profile/pager-right.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import childTigerAvatarUrl from '@/assets/images/profile/child-avatar-tiger.webp'
import childRabbitAvatarUrl from '@/assets/images/profile/child-avatar-rabbit.webp'
import backgroundUrl from '@/assets/images/profile/background.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import pinKeyUrl from '@/assets/images/profile/pin-key.webp'
import pinPanelUrl from '@/assets/images/profile/pin-panel.webp'
import musicOnUrl from '@/assets/images/start/music-btn.webp'
import musicOffUrl from '@/assets/images/start/mute-btn.webp'
import { useBgmPlayer } from '@/hooks/useBgmPlayer'
import { useFitScale } from '@/hooks/useFitScale'
import { ParentNoticeDialog } from '@/components/ParentNoticeDialog'
import {
  deleteChildProfile as deleteBackendChildProfile,
  getChildProfiles as getBackendChildProfiles,
  updateChildProfile as updateBackendChildProfile,
} from '@/api/profiles'
import { getStoredAccessToken } from '@/api/auth'
import {
  childImageNumFromPhoto,
  childPhotoFromImageNum,
  loadChildProfiles,
  MAX_CHILD_PROFILES,
  saveActiveChildProfileId,
  saveChildProfiles,
  type ChildProfile,
} from '@/lib/childProfiles'

const jua = { fontFamily: "'Jua', sans-serif" } as const
const PAGE_SIZE = 3
const DELETE_PIN_LENGTH = 4
const DELETE_PIN_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫']

export function ChildProfileListPage() {
  const navigate = useNavigate()
  const canvasRef = useFitScale<HTMLDivElement>()
  const { audioRef, isBgmPlaying, toggleBgm } = useBgmPlayer()
  const editVideoRef = useRef<HTMLVideoElement>(null)
  const editStreamRef = useRef<MediaStream | null>(null)
  const deletionPinKeyHandlerRef = useRef<(key: string) => void>(() => undefined)
  const [profiles, setProfiles] = useState(loadChildProfiles)
  const [page, setPage] = useState(0)
  const [editingProfile, setEditingProfile] = useState<ChildProfile | null>(
    null,
  )
  const [editingName, setEditingName] = useState('')
  const [editingNameMessage, setEditingNameMessage] = useState('')
  const [editingPhotoUrl, setEditingPhotoUrl] = useState<string | null>(null)
  const [isEditCameraOn, setIsEditCameraOn] = useState(false)
  const [profilePendingDeletion, setProfilePendingDeletion] =
    useState<ChildProfile | null>(null)
  const [isDeletionPinOpen, setIsDeletionPinOpen] = useState(false)
  const [deletionPin, setDeletionPin] = useState('')
  const [deletionPinMessage, setDeletionPinMessage] = useState('')
  const [isDeletingProfile, setIsDeletingProfile] = useState(false)
  const isProfileLimitReached = profiles.length >= MAX_CHILD_PROFILES
  const pageCount = Math.max(1, Math.ceil(profiles.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount - 1)
  const visibleProfiles = useMemo(
    () =>
      profiles.slice(
        currentPage * PAGE_SIZE,
        currentPage * PAGE_SIZE + PAGE_SIZE,
      ),
    [currentPage, profiles],
  )

  const updateProfiles = (nextProfiles: ChildProfile[]) => {
    setProfiles(nextProfiles)
    saveChildProfiles(nextProfiles)
  }

  useEffect(() => {
    if (!getStoredAccessToken()) return
    let active = true
    void getBackendChildProfiles()
      .then((children) => {
        if (!active) return
        const local = loadChildProfiles()
        updateProfiles(
          children.map((child) => ({
            id: String(child.childId),
            name: child.name,
            photoUrl:
              local.find((profile) => profile.id === String(child.childId))
                ?.photoUrl ?? childPhotoFromImageNum(child.profileImageNum),
            profileImageNum: child.profileImageNum,
          })),
        )
      })
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [])

  const stopEditCamera = () => {
    editStreamRef.current?.getTracks().forEach((track) => track.stop())
    editStreamRef.current = null
    setIsEditCameraOn(false)
  }

  useEffect(() => {
    const video = editVideoRef.current
    const stream = editStreamRef.current
    if (!isEditCameraOn || !video || !stream) return

    video.srcObject = stream
    void video.play()
  }, [isEditCameraOn])

  useEffect(() => () => stopEditCamera(), [])

  const removeProfile = (profile: ChildProfile) => {
    setProfilePendingDeletion(profile)
    setIsDeletionPinOpen(false)
    setDeletionPin('')
    setDeletionPinMessage('')
  }

  const closeProfileRemoval = () => {
    if (isDeletingProfile) return
    setProfilePendingDeletion(null)
    setIsDeletionPinOpen(false)
    setDeletionPin('')
    setDeletionPinMessage('')
  }

  const openDeletionPin = () => {
    setDeletionPin('')
    setDeletionPinMessage('')
    setIsDeletionPinOpen(true)
  }

  const confirmProfileRemoval = (pin: string) => {
    if (!profilePendingDeletion || isDeletingProfile) return
    const childId = Number(profilePendingDeletion.id)
    setIsDeletingProfile(true)
    setDeletionPinMessage('')
    void deleteBackendChildProfile(childId, pin)
      .then(() => {
        updateProfiles(profiles.filter(({ id }) => id !== profilePendingDeletion.id))
        setProfilePendingDeletion(null)
        setIsDeletionPinOpen(false)
        setDeletionPin('')
      })
      .catch((error: unknown) => {
        setDeletionPin('')
        setDeletionPinMessage(
          error instanceof Error ? error.message : '프로필을 삭제하지 못했어요.',
        )
      })
      .finally(() => setIsDeletingProfile(false))
  }

  const pressDeletionPinKey = (key: string) => {
    if (isDeletingProfile) return
    if (key === '⌫') {
      setDeletionPin((value) => value.slice(0, -1))
      setDeletionPinMessage('')
      return
    }
    if (!/^\d$/.test(key) || deletionPin.length >= DELETE_PIN_LENGTH) return
    const nextPin = deletionPin + key
    setDeletionPin(nextPin)
    setDeletionPinMessage('')
    if (nextPin.length === DELETE_PIN_LENGTH) {
      confirmProfileRemoval(nextPin)
    }
  }

  useEffect(() => {
    deletionPinKeyHandlerRef.current = pressDeletionPinKey
  })

  useEffect(() => {
    if (!isDeletionPinOpen) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (/^\d$/.test(event.key)) deletionPinKeyHandlerRef.current(event.key)
      if (event.key === 'Backspace') deletionPinKeyHandlerRef.current('⌫')
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isDeletionPinOpen])

  const openEdit = (profile: ChildProfile) => {
    stopEditCamera()
    setEditingProfile(profile)
    setEditingName(profile.name)
    setEditingNameMessage('')
    setEditingPhotoUrl(profile.photoUrl)
  }

  const toggleEditCamera = async () => {
    if (!isEditCameraOn) {
      try {
        editStreamRef.current = await navigator.mediaDevices.getUserMedia({
          video: { width: 480, height: 480 },
        })
        setIsEditCameraOn(true)
      } catch {
        window.alert('웹캠을 사용할 수 없어요.')
      }
      return
    }

    const video = editVideoRef.current
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
        setEditingPhotoUrl(canvas.toDataURL('image/webp', 0.9))
      }
    }
    stopEditCamera()
  }

  const cycleDefaultEditPhoto = () => {
    stopEditCamera()
    setEditingPhotoUrl((current) => {
      if (current === childTigerAvatarUrl) return childRabbitAvatarUrl
      if (current === childRabbitAvatarUrl) return null
      if (current === null || current === childAvatarUrl) {
        return childTigerAvatarUrl
      }
      return null
    })
  }

  const saveEdit = () => {
    const nextName = editingName.trim()
    if (!editingProfile || !nextName) return
    const profileImageNum = childImageNumFromPhoto(
      editingPhotoUrl,
      editingProfile.profileImageNum,
    )
    void updateBackendChildProfile(
      Number(editingProfile.id),
      nextName,
      profileImageNum,
    )
      .then((updated) => {
        updateProfiles(
          profiles.map((profile) =>
            profile.id === editingProfile.id
              ? {
                  ...profile,
                  name: updated.name,
                  photoUrl: editingPhotoUrl,
                  profileImageNum: updated.profileImageNum,
                }
              : profile,
          ),
        )
        stopEditCamera()
        setEditingProfile(null)
      })
      .catch((error: unknown) => {
        setEditingNameMessage(
          error instanceof Error ? error.message : '프로필을 수정하지 못했어요.',
        )
      })
  }

  return (
    <div className="flex h-screen w-screen items-start justify-center overflow-hidden bg-[#0e1a12]">
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
          className="absolute inset-0 size-full object-cover"
          fetchPriority="high"
          src={backgroundUrl}
        />
        <div className="absolute inset-0 bg-[rgba(10,26,15,.3)]" />

        <Link
          aria-label="뒤로"
          className="absolute top-[25px] left-[25px] z-30 size-[58px] drop-shadow-[0_4px_8px_rgba(0,0,0,.28)] transition hover:scale-105"
          to="/profiles/new"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={backButtonUrl}
          />
        </Link>
        <button
          aria-label={isBgmPlaying ? '배경 음악 끄기' : '배경 음악 켜기'}
          className="absolute bottom-[85px] left-[25px] z-40 size-[62px] bg-transparent p-0 drop-shadow-[0_4px_8px_rgba(0,0,0,.3)] transition hover:scale-105"
          onClick={() => void toggleBgm()}
          type="button"
        >
          <img
            alt=""
            className="size-full object-contain"
            src={isBgmPlaying ? musicOnUrl : musicOffUrl}
          />
        </button>

        <main className="absolute -top-4 left-1/2 z-10 h-[672px] w-[640px] -translate-x-1/2">
          <img
            alt=""
            className="pointer-events-none absolute inset-0 size-full"
            src={frameUrl}
          />
          <h1
            className="absolute top-[10.5%] right-0 left-0 text-center text-[32px] text-[#3a7d2c]"
            style={jua}
          >
            아이 프로필 목록
          </h1>
          <div
            className="absolute top-[11.8%] right-[18%] text-[17px] text-[#8a7a55]"
            style={jua}
          >
            {profiles.length}/{MAX_CHILD_PROFILES}
          </div>

          <div className="absolute top-[23.2%] right-[12%] left-[13%] flex flex-col">
            {visibleProfiles.length ? (
              visibleProfiles.map((profile) => (
                <article
                  className="relative -mb-4 flex h-[145px] flex-none items-center"
                  key={profile.id}
                >
                  <img
                    alt=""
                    className="pointer-events-none absolute inset-0 size-full"
                    src={kidCellUrl}
                  />
                  <button
                    className="relative z-10 flex min-w-0 flex-1 -translate-y-0.5 items-center bg-transparent text-left"
                    onClick={() => {
                      saveActiveChildProfileId(profile.id)
                      navigate('/child')
                    }}
                    type="button"
                  >
                    <img
                      alt=""
                      className="ml-[54px] size-[92px] rounded-full bg-[#fdf6e3] object-cover shadow-[0_0_0_3px_#d9c79a]"
                      src={profile.photoUrl ?? childAvatarUrl}
                    />
                    <span
                      className="ml-[22px] truncate text-[33px] text-[#5a4a2a]"
                      style={jua}
                    >
                      {profile.name}
                    </span>
                  </button>
                  <div className="relative z-10 mr-[22px] flex -translate-y-0.5 flex-col">
                    <button
                      className="h-[72px] w-[184px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#7a5a1e] transition hover:scale-105"
                      onClick={() => openEdit(profile)}
                      style={{
                        ...jua,
                        backgroundImage: `url(${editButtonUrl})`,
                      }}
                      type="button"
                    >
                      수정
                    </button>
                    <button
                      className="-mt-[23px] h-[72px] w-[184px] bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#9a4a3e] transition hover:scale-105"
                      onClick={() => removeProfile(profile)}
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
              <div
                className="mt-24 text-center text-[24px] text-[#8a7a55]"
                style={jua}
              >
                아직 만든 아이 프로필이 없어요.
              </div>
            )}
          </div>

          {isProfileLimitReached ? (
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
          ) : (
            <Link
              className="absolute bottom-[74%] left-5/7 flex h-18 w-60 -translate-x-1/2 items-center justify-center bg-[length:100%_100%] bg-center bg-no-repeat text-[20px] text-white active:translate-y-0.5"
              style={{
                ...jua,
                backgroundImage: `url(${completeButtonUrl})`,
                paintOrder: 'stroke fill',
                WebkitTextStroke: '3px #4a7d1e',
              }}
              to="/profiles/new?add=1&source=onboarding"
            >
              + 새 프로필 생성
            </Link>
          )}

          <div className="absolute right-0 bottom-[8.7%] left-0 flex items-center justify-center gap-1.5">
            <button
              aria-label="이전"
              className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
              disabled={currentPage === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={pagerLeftUrl}
              />
            </button>
            {getVisiblePageIndices(currentPage, pageCount).map((index) => (
              <button
                className="relative size-[54px] bg-transparent p-0 transition hover:scale-105"
                key={index}
                onClick={() => setPage(index)}
                style={{ opacity: index === currentPage ? 1 : 0.5 }}
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
            ))}
            <button
              aria-label="다음"
              className="size-[54px] bg-transparent p-0 transition hover:scale-105 disabled:opacity-40"
              disabled={currentPage >= pageCount - 1}
              onClick={() =>
                setPage((value) => Math.min(pageCount - 1, value + 1))
              }
              type="button"
            >
              <img
                alt=""
                className="size-full object-contain"
                src={pagerRightUrl}
              />
            </button>
          </div>
        </main>

        {editingProfile ? (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(10,26,15,.55)]">
            <div className="relative h-[440px] w-[430px] p-10 drop-shadow-[0_22px_36px_rgba(10,26,15,.42)]">
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={revisePopupUrl}
              />
              <h2
                className="relative -mt-2 text-center text-[28px] text-[#3a7d2c]"
                style={jua}
              >
                아이 프로필 수정
              </h2>
              <div className="relative mt-3 flex items-center justify-center gap-3">
                <div className="relative left-8 aspect-square size-[135px] shrink-0 overflow-hidden rounded-full bg-[#fdf6e3] shadow-[0_0_0_4px_#d9c79a]">
                  {isEditCameraOn ? (
                    <video
                      autoPlay
                      className="size-full -scale-x-100 object-cover"
                      muted
                      playsInline
                      ref={editVideoRef}
                    />
                  ) : (
                    <img
                      alt={`${editingProfile.name} 프로필`}
                      className="size-full object-cover"
                      src={editingPhotoUrl ?? childAvatarUrl}
                    />
                  )}
                </div>
                <div className="relative left-2 flex flex-col">
                  <button
                    className="h-18 w-58 bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#5a4a2a]"
                    onClick={() => void toggleEditCamera()}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    {isEditCameraOn ? '📸 사진 찍기' : '웹캠 촬영'}
                  </button>
                  <button
                    className="-mt-7 h-18 w-58 bg-[length:100%_100%] bg-center bg-no-repeat text-[19px] text-[#5a4a2a]"
                    onClick={cycleDefaultEditPhoto}
                    style={{
                      ...jua,
                      backgroundImage: `url(${photoButtonUrl})`,
                    }}
                    type="button"
                  >
                    기본 이미지
                  </button>
                </div>
              </div>
              <p
                className="relative mt-3 text-center text-[14px] text-[#8a7a55]"
                style={jua}
              >
                세 마리의 동물이 있어요! 눌러서 확인해보세요!
              </p>
              <input
                autoFocus
                className="relative mx-auto mt-7 block h-14 w-[90%] rounded-full border-2 border-[#d9c79a] bg-white px-6 text-center text-[20px] text-[#5a4a2a] outline-none"
                onChange={(event) => {
                  const nextName = event.target.value
                  setEditingName(nextName.slice(0, 15))
                  setEditingNameMessage(
                    nextName.length > 15 ? '이름은 15자까지 가능해요' : '',
                  )
                }}
                style={jua}
                value={editingName}
              />
              <p
                className="relative -mb-4 h-4 text-center text-[13px] text-[#c7563a]"
                style={jua}
              >
                {editingNameMessage}
              </p>
              <div className="relative mt-0 flex w-full justify-center">
                <button
                  className="w-64 bg-[length:100%_100%] bg-no-repeat px-4 py-6 text-[23px] text-white"
                  onClick={saveEdit}
                  style={{
                    ...jua,
                    backgroundImage: `url(${completeButtonUrl})`,
                  }}
                  type="button"
                >
                  저장
                </button>
                <button
                  className="w-72 bg-[length:100%_100%] bg-no-repeat px-6 text-[18px] text-[#5a4a2a]"
                  onClick={() => {
                    stopEditCamera()
                    setEditingProfile(null)
                  }}
                  style={{
                    ...jua,
                    backgroundImage: `url(${photoButtonUrl})`,
                  }}
                  type="button"
                >
                  취소
                </button>
              </div>
            </div>
          </div>
        ) : null}
        {profilePendingDeletion && !isDeletionPinOpen ? (
          <ParentNoticeDialog
            compact
            confirmLabel="PIN 입력"
            message={`${profilePendingDeletion.name} 프로필을 삭제할까요?`}
            onCancel={closeProfileRemoval}
            onConfirm={openDeletionPin}
            title="프로필 삭제"
          />
        ) : null}
        {profilePendingDeletion && isDeletionPinOpen ? (
          <div
            aria-modal="true"
            className="absolute inset-0 z-100 flex items-center justify-center bg-[rgba(18,30,18,0.6)]"
            onClick={closeProfileRemoval}
            role="dialog"
          >
            <div
              className="relative -top-[30px] h-[560px] w-[440px]"
              onClick={(event) => event.stopPropagation()}
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-contain"
                src={pinPanelUrl}
              />
              <div className="absolute top-[9%] right-[11%] left-[11%] flex flex-col">
                <h2 className="text-center text-[30px] text-[#3a7d2c]" style={jua}>
                  부모 확인
                </h2>
                <p className="mt-1.5 text-center text-[17px] text-[#8a7a5a]" style={jua}>
                  삭제하려면 PIN 번호 4자리를 입력하세요
                </p>
                <div className="mt-5 mb-1.5 flex justify-center gap-4">
                  {Array.from({ length: DELETE_PIN_LENGTH }).map((_, index) => {
                    const isFilled = index < deletionPin.length
                    return (
                      <span className="flex size-[30px] items-center justify-center" key={index}>
                        <img
                          alt=""
                          className={isFilled ? 'size-[56px] max-w-none object-contain' : 'size-[30px] object-contain'}
                          src={isFilled ? pinDotFillUrl : pinDotEmptyUrl}
                        />
                      </span>
                    )
                  })}
                </div>
                <div
                  className="h-[22px] text-center text-[16px] text-[#d1483a]"
                  style={jua}
                >
                  {isDeletingProfile ? '확인하고 있어요...' : deletionPinMessage || '.'}
                </div>
                <div className="mt-6 grid grid-cols-3 gap-2.5">
                  {DELETE_PIN_KEYS.map((key, index) =>
                    key ? (
                      <button
                        className="relative h-14 bg-cover bg-center text-[28px] text-[#3a6b1e] transition active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                        disabled={isDeletingProfile}
                        key={key}
                        onClick={() => pressDeletionPinKey(key)}
                        style={{ ...jua, backgroundImage: `url(${pinKeyUrl})` }}
                        type="button"
                      >
                        {key}
                      </button>
                    ) : (
                      <div key={`blank-${index}`} />
                    ),
                  )}
                </div>
              </div>
              <button
                aria-label="닫기"
                className="absolute -top-4 -right-4 grid size-[46px] place-items-center rounded-full border-[3px] border-white bg-[#cf6f4e] text-xl text-white shadow-[0_4px_8px_rgba(0,0,0,0.3)] transition hover:scale-105 disabled:opacity-40"
                disabled={isDeletingProfile}
                onClick={closeProfileRemoval}
                type="button"
              >
                ✕
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
