import { useEffect, useRef, useState } from 'react'

import childAvatarUrl from '@/assets/images/profile/child-avatar.webp'
import childTigerAvatarUrl from '@/assets/images/profile/child-avatar-tiger.webp'
import childRabbitAvatarUrl from '@/assets/images/profile/child-avatar-rabbit.webp'
import completeButtonUrl from '@/assets/images/parent-profile/btn-complete.webp'
import inputBarUrl from '@/assets/images/parent-profile/input-bar.webp'
import portraitFrameUrl from '@/assets/images/parent-profile/frame-portrait.webp'
import photoButtonUrl from '@/assets/images/parent-profile/btn-photo.webp'
import { COMPLETE_BUTTON_CLIP, PHOTO_BUTTON_CLIP } from '@/lib/buttonClip'
import { MAX_CHILD_PROFILES, type ChildProfile } from '@/lib/childProfiles'

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function ChildProfileCreateDialog({
  initialProfile,
  onBack,
  onCreate,
  profiles,
}: {
  initialProfile?: ChildProfile
  onBack: () => void
  onCreate: (profile: ChildProfile) => void
  profiles: ChildProfile[]
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [name, setName] = useState(initialProfile?.name ?? '')
  const [photoUrl, setPhotoUrl] = useState<string | null>(
    initialProfile?.photoUrl ?? null,
  )
  const [message, setMessage] = useState('')
  const [isCameraOn, setIsCameraOn] = useState(false)
  const isEditMode = initialProfile !== undefined
  const isLimitReached = !isEditMode && profiles.length >= MAX_CHILD_PROFILES

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    setIsCameraOn(false)
  }

  useEffect(() => {
    const video = videoRef.current
    if (!isCameraOn || !video || !streamRef.current) return
    video.srcObject = streamRef.current
    void video.play()
  }, [isCameraOn])

  useEffect(() => () => stopCamera(), [])

  const cycleDefaultPhoto = () => {
    stopCamera()
    setPhotoUrl((current) => {
      if (current === childTigerAvatarUrl) return childRabbitAvatarUrl
      if (current === childRabbitAvatarUrl) return null
      if (current === null) return childTigerAvatarUrl
      return null
    })
    setMessage('')
  }

  const toggleCamera = async () => {
    if (!isCameraOn) {
      try {
        streamRef.current = await navigator.mediaDevices.getUserMedia({
          video: { width: 480, height: 480 },
        })
        setIsCameraOn(true)
        setMessage('화면을 확인한 뒤 사진 찍기를 눌러 주세요.')
      } catch {
        setMessage('웹캠을 사용할 수 없어요.')
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
        setPhotoUrl(canvas.toDataURL('image/png'))
      }
    }
    stopCamera()
  }

  const createProfile = () => {
    if (isLimitReached) return
    const trimmedName = name.trim()
    if (!trimmedName) {
      setMessage('아이 이름을 입력해 주세요.')
      return
    }
    const hasDuplicateName = profiles.some(
      (profile) =>
        profile.id !== initialProfile?.id &&
        profile.name.trim().toLocaleLowerCase('ko-KR') ===
          trimmedName.toLocaleLowerCase('ko-KR'),
    )
    if (hasDuplicateName) {
      setMessage('같은 이름의 아이 프로필이 이미 있어요.')
      return
    }
    stopCamera()
    onCreate({
      id:
        initialProfile?.id ??
        (typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random()}`),
      name: trimmedName,
      photoUrl,
    })
  }

  return (
    <div className="absolute inset-0 z-70 flex items-start justify-center bg-[rgba(10,26,15,.5)]">
      <section
        aria-label={isEditMode ? '아이 프로필 수정' : '아이 프로필 생성'}
        className="relative mt-9 h-[640px] w-[500px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
      >
        <img
          alt=""
          className="pointer-events-none absolute inset-0 size-full"
          src={portraitFrameUrl}
        />
        <h2
          className="absolute top-[12.5%] right-0 left-0 text-center text-[29px] text-[#3a7d2c]"
          style={jua}
        >
          {isEditMode ? '아이 프로필 수정' : '아이 프로필 생성'}
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
              src={photoUrl ?? childAvatarUrl}
            />
          )}
        </div>

        <div className="absolute top-[21.2%] right-[18%] flex flex-col">
          <PhotoButton
            label={isCameraOn ? '사진 찍기' : '웹캠 촬영'}
            onClick={() => void toggleCamera()}
          />
          <PhotoButton label="기본 이미지" onClick={cycleDefaultPhoto} />
        </div>

        <p
          className="absolute top-[42%] right-0 left-0 text-center text-[16px] text-[#8a7a55]"
          style={jua}
        >
          세 마리의 동물이 있어요! 눌러서 확인해보세요!
        </p>
        <label
          className="absolute top-[48.5%] left-[20%] text-[22px] text-[#3a7d2c]"
          htmlFor="parent-child-profile-name"
          style={jua}
        >
          아이 이름
        </label>
        <div
          className="absolute top-[52.5%] right-[19.5%] left-[19.5%] h-[72px] -translate-x-1.5 bg-[length:100%_100%] bg-center bg-no-repeat"
          style={{ backgroundImage: `url(${inputBarUrl})` }}
        >
          <input
            className="absolute inset-y-1.5 right-5 left-5 bg-transparent text-center text-[20px] text-[#5a4a2a] outline-none placeholder:text-[#b9a97f]"
            id="parent-child-profile-name"
            onChange={(event) => {
              setName(event.target.value.slice(0, 15))
              setMessage(
                event.target.value.length > 15
                  ? '이름은 15자까지 가능해요'
                  : '',
              )
            }}
            placeholder="이름을 입력해 주세요"
            style={jua}
            value={name}
          />
        </div>
        <p
          className="absolute top-[62%] right-0 left-0 text-center text-[14px] text-[#c7563a]"
          style={jua}
        >
          {isLimitReached
            ? `아이 프로필은 최대 ${MAX_CHILD_PROFILES}개까지 만들 수 있어요`
            : message}
        </p>
        <button
          className="absolute top-[65.5%] left-1/2 h-[84px] w-[370px] -translate-x-1/2 bg-[length:100%_100%] bg-center bg-no-repeat text-[23px] text-white active:translate-y-0.5 disabled:opacity-50"
          disabled={isLimitReached}
          onClick={createProfile}
          style={{
            ...jua,
            backgroundImage: `url(${completeButtonUrl})`,
            clipPath: COMPLETE_BUTTON_CLIP,
            paintOrder: 'stroke fill',
            WebkitTextStroke: '3px #4a7d1e',
          }}
          type="button"
        >
          {isEditMode ? '아이 프로필 수정하기' : '아이 프로필 만들기'}
        </button>
        <button
          className="absolute top-[74.5%] left-1/2 h-[78px] w-[265px] -translate-x-1/2 bg-[length:100%_100%] bg-center bg-no-repeat text-[22px] text-[#5a4a2a] active:translate-y-0.5"
          onClick={() => {
            stopCamera()
            onBack()
          }}
          style={{
            ...jua,
            backgroundImage: `url(${photoButtonUrl})`,
            clipPath: PHOTO_BUTTON_CLIP,
          }}
          type="button"
        >
          뒤로 가기
        </button>
      </section>
    </div>
  )
}

function PhotoButton({
  label,
  onClick,
}: {
  label: string
  onClick: () => void
}) {
  return (
    <button
      className="-mt-5 h-16 w-[200px] bg-[length:100%_100%] bg-center bg-no-repeat text-[18px] text-[#5a4a2a] first:mt-0 active:translate-y-0.5"
      onClick={onClick}
      style={{ ...jua, backgroundImage: `url(${photoButtonUrl})` }}
      type="button"
    >
      {label}
    </button>
  )
}
