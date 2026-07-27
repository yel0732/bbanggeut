import leftButtonUrl from '@/assets/images/game-entry/left-btn.webp'
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
      className="pointer-events-auto grid size-[62px] place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] shadow-[0_6px_0_#5d8e2e,0_10px_14px_rgba(20,55,20,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e] disabled:opacity-40"
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      <span className="flex gap-[6px]">
        <span className="h-6 w-[7px] rounded-[2px] bg-white" />
        <span className="h-6 w-[7px] rounded-[2px] bg-white" />
      </span>
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
        {status === 'requesting' || status === 'idle' ? (
          <p
            className="text-[34px] leading-none text-white [text-shadow:0_3px_8px_rgba(20,50,20,0.5)]"
            style={jua}
          >
            카메라 준비 중...
          </p>
        ) : (
          <div className="flex flex-col items-center gap-4 text-center">
            <p
              className="text-[52px] leading-none text-white [text-shadow:0_4px_0_#4f8a30,0_8px_12px_rgba(19,50,18,0.4)]"
              style={jua}
            >
              {status === 'unavailable'
                ? '카메라를 찾을 수 없어요'
                : '카메라 없음'}
            </p>
            <p
              className="text-[24px] leading-snug text-white/90 [text-shadow:0_2px_6px_rgba(20,50,20,0.45)]"
              style={jua}
            >
              브라우저 주소창의 카메라 아이콘에서 권한을 허용해 주세요
            </p>
            <button
              className="mt-2 grid h-[62px] w-[200px] place-items-center rounded-full border-[4px] border-white bg-[#8fc75a] text-[26px] leading-none text-white shadow-[0_6px_0_#5d8e2e,0_10px_14px_rgba(20,55,20,0.3)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 active:shadow-[0_2px_0_#5d8e2e]"
              onClick={onRetry}
              style={jua}
              type="button"
            >
              다시 시도
            </button>
          </div>
        )}
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
    <div
      aria-modal="true"
      className="absolute inset-0 z-50 flex items-center justify-center bg-[rgba(14,30,15,0.6)]"
      role="dialog"
    >
      <div className="flex w-[440px] flex-col items-center gap-6 rounded-[36px] border-[8px] border-[#8a5a2b] bg-[#f4ead0] px-10 py-9 shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <p
          className="text-center text-[32px] leading-snug text-[#6f4a1f]"
          style={jua}
        >
          카메라에 문제가 생겼어요
        </p>
        <div className="flex w-full flex-col items-center gap-3">
          <OverlayButton label="다시 시도" onClick={onRetry} tone="green" />
          <OverlayButton label="메인으로" onClick={onQuit} tone="orange" />
        </div>
      </div>
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
      <div className="flex w-[420px] flex-col items-center gap-6 rounded-[36px] border-[8px] border-[#8a5a2b] bg-[#f4ead0] px-10 py-9 shadow-[0_22px_44px_rgba(20,48,16,0.45)]">
        <p className="text-[34px] leading-none text-[#6f4a1f]" style={jua}>
          잠깐 쉬어 갈까요?
        </p>
        <div className="flex w-full flex-col items-center gap-3">
          <OverlayButton label="이어하기" onClick={onResume} tone="green" />
          <OverlayButton
            label="처음부터 다시"
            onClick={onRestart}
            tone="yellow"
          />
          <OverlayButton label="그만하기" onClick={onQuit} tone="orange" />
        </div>
      </div>
    </div>
  )
}

const OVERLAY_BUTTON_TONE = {
  green:
    'bg-[#8fc75a] shadow-[0_6px_0_#5d8e2e] active:shadow-[0_2px_0_#5d8e2e]',
  yellow:
    'bg-[#f0c94b] shadow-[0_6px_0_#b6892a] active:shadow-[0_2px_0_#b6892a]',
  orange:
    'bg-[#e6a838] shadow-[0_6px_0_#a9761f] active:shadow-[0_2px_0_#a9761f]',
} as const

function OverlayButton({
  label,
  onClick,
  tone,
}: {
  label: string
  onClick: () => void
  tone: keyof typeof OVERLAY_BUTTON_TONE
}) {
  return (
    <button
      className={`grid h-[64px] w-full place-items-center rounded-full border-[4px] border-white text-[26px] leading-none text-white transition hover:scale-[1.03] focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:translate-y-1 ${OVERLAY_BUTTON_TONE[tone]}`}
      onClick={onClick}
      style={jua}
      type="button"
    >
      {label}
    </button>
  )
}
