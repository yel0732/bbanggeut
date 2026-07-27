import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import goHomeButtonUrl from '@/assets/images/game-common/go-home-button.webp'
import pauseButtonUrl from '@/assets/images/game-common/pause-button.webp'
import pausePopupUrl from '@/assets/images/game-common/pause-popup.webp'
import replayButtonUrl from '@/assets/images/game-common/replay-button.webp'
import resumeButtonUrl from '@/assets/images/game-common/resume-button.webp'
import errorPopupUrl from '@/assets/images/parent-mode/error-popup.webp'
import type { CameraStatus } from '@/hooks/useCameraStream'

/**
 * 게임 플레이 화면 공통 조각.
 *
 * 카메라 권한/오류/일시정지 연출은 게임 종류와 무관하게 같은 흐름이라
 * 과일 자르기와 도블(그림 맞추기)이 함께 쓴다.
 */

const jua = { fontFamily: "'Jua', sans-serif" } as const

export function QuitButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      aria-label="게임 그만하기"
      className="pointer-events-auto size-[62px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="block h-full w-full object-contain"
        decoding="async"
        draggable={false}
        src={leftButtonUrl}
      />
    </button>
  )
}

export function PauseButton({
  disabled,
  onClick,
}: {
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      aria-label="일시정지"
      className="pointer-events-auto size-[68px] border-0 bg-transparent p-0 drop-shadow-[0_5px_8px_rgba(20,55,20,0.32)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95 disabled:opacity-40"
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="block h-full w-full object-contain"
        decoding="async"
        draggable={false}
        src={pauseButtonUrl}
      />
    </button>
  )
}

export function CameraPermissionView({
  onQuit,
  onRetry,
  status,
  title,
}: {
  onQuit: () => void
  onRetry: () => void
  status: CameraStatus
  title: string
}) {
  if (
    status === 'denied' ||
    status === 'unavailable' ||
    status === 'error'
  ) {
    return (
      <CameraNoticeDialog
        message={
          status === 'denied'
            ? '브라우저 주소창의 카메라 아이콘에서 권한을 허용해 주세요.'
            : '카메라 연결 상태를 확인한 뒤 다시 시도해 주세요.'
        }
        onQuit={onQuit}
        onRetry={onRetry}
        title={
          status === 'denied'
            ? '카메라 권한이 필요해요'
            : status === 'unavailable'
              ? '카메라를 찾을 수 없어요'
              : '카메라 연결에 문제가 생겼어요'
        }
      />
    )
  }

  return (
    <>
      <div className="absolute top-[25px] left-[45px] z-30">
        <QuitButton onClick={onQuit} />
      </div>
      <h1
        className="absolute top-[34px] left-[120px] z-30 text-[30px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_7px_10px_rgba(19,50,18,0.28)]"
        style={jua}
      >
        {title}
      </h1>

      <div className="absolute inset-0 z-10 flex items-center justify-center">
        <p
          className="text-[34px] leading-none text-white [text-shadow:0_3px_8px_rgba(20,50,20,0.5)]"
          style={jua}
        >
          카메라 준비 중...
        </p>
      </div>
    </>
  )
}

export function CameraErrorOverlay({
  onQuit,
  onRetry,
}: {
  onQuit: () => void
  onRetry: () => void
}) {
  return (
    <CameraNoticeDialog
      message="카메라 연결 상태를 확인한 뒤 다시 시도해 주세요."
      onQuit={onQuit}
      onRetry={onRetry}
      title="카메라 연결에 문제가 생겼어요"
    />
  )
}

function CameraNoticeDialog({
  message,
  onQuit,
  onRetry,
  title,
}: {
  message: string
  onQuit: () => void
  onRetry: () => void
  title: string
}) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(14,30,15,0.6)]"
      role="dialog"
    >
      <section className="relative -mt-6 h-[420px] w-[560px] drop-shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <img
          alt=""
          className="pointer-events-none absolute inset-0 size-full object-fill"
          decoding="async"
          draggable={false}
          src={errorPopupUrl}
        />
        <div className="absolute top-[35%] right-[13%] left-[13%] text-center">
          <h2 className="text-[27px] text-[#754b25]" style={jua}>
            {title}
          </h2>
          <p className="mt-2 text-[17px] leading-6 text-[#796443]" style={jua}>
            {message}
          </p>
        </div>
        <div className="absolute right-[18%] bottom-[12%] left-[18%] flex justify-center gap-3">
          <button
            className="h-11 min-w-[125px] rounded-2xl border-[3px] border-[#c8aa70] bg-[#fff8e9] px-5 text-[17px] text-[#765d36] shadow-md transition hover:scale-103"
            onClick={onQuit}
            style={jua}
            type="button"
          >
            게임 선택으로
          </button>
          <button
            className="h-11 min-w-[125px] rounded-2xl border-[3px] border-[#668c3c] bg-[#83ad4f] px-5 text-[17px] text-white shadow-md transition hover:scale-103"
            onClick={onRetry}
            style={jua}
            type="button"
          >
            다시 시도
          </button>
        </div>
      </section>
    </div>
  )
}

export function PauseOverlay({
  onQuit,
  onRestart,
  onResume,
}: {
  onQuit: () => void
  onRestart: () => void
  onResume: () => void
}) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(14,30,15,0.55)]"
      role="dialog"
    >
      <div className="relative h-[540px] w-[400px] drop-shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <img
          alt=""
          className="pointer-events-none absolute inset-0 h-full w-full object-contain"
          decoding="async"
          draggable={false}
          src={pausePopupUrl}
        />

        <h2
          className="absolute top-[45px] left-1/2 -translate-x-1/2 whitespace-nowrap text-[34px] leading-none text-[#6f4a1f]"
          style={jua}
        >
          일시 정지
        </h2>

        <button
          aria-label="게임 이어하기"
          className="absolute top-[60px] right-[8px] size-[64px] rounded-full border-0 bg-transparent transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-95"
          onClick={onResume}
          type="button"
        />

        <div className="absolute top-[130px] left-1/2 flex -translate-x-1/2 flex-col items-center gap-[18px]">
          <PauseMenuButton
            imageUrl={resumeButtonUrl}
            label="이어하기"
            onClick={onResume}
          />
          <PauseMenuButton
            imageUrl={replayButtonUrl}
            label="다시하기"
            onClick={onRestart}
          />
          <PauseMenuButton
            imageUrl={goHomeButtonUrl}
            label="홈으로 가기"
            onClick={onQuit}
          />
        </div>
      </div>
    </div>
  )
}

function PauseMenuButton({
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
      className="relative grid h-[82px] w-[300px] place-items-center border-0 bg-transparent p-0 transition hover:scale-[1.035] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[0.98]"
      onClick={onClick}
      type="button"
    >
      <img
        alt=""
        className="absolute inset-0 h-full w-full object-fill"
        decoding="async"
        draggable={false}
        src={imageUrl}
      />
      <span
        className="relative z-10 text-[28px] leading-none text-white [text-shadow:0_3px_0_rgba(83,69,29,0.42)]"
        style={jua}
      >
        {label}
      </span>
    </button>
  )
}
