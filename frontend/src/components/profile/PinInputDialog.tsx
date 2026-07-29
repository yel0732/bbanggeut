import backButtonUrl from '@/assets/images/game-entry/left-btn.webp'
import keypadButtonUrl from '@/assets/images/parent-profile/keypad-panel.webp'
import pinInputPopupUrl from '@/assets/images/profile/pin-input-popup.webp'
import pinDotEmptyUrl from '@/assets/images/profile/pin-dot-empty.webp'
import pinDotFillUrl from '@/assets/images/profile/pin-dot-fill.webp'
import closeButtonUrl from '@/assets/images/start/close-btn.webp'

type PinInputDialogProps = {
  title: string
  description: string
  pin: string
  pinLength?: number
  message?: string
  messageTone?: 'default' | 'error' | 'success'
  disabled?: boolean
  closeDisabled?: boolean
  showBackButton?: boolean
  resetLabel?: string
  onBack?: () => void
  onClose: () => void
  onPressKey: (key: string) => void
  onReset?: () => void
}

const jua = { fontFamily: "'Jua', sans-serif" } as const
const DEFAULT_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '←']

const messageColorByTone = {
  default: '#8a7a5a',
  error: '#c85845',
  success: '#3a7d2c',
} as const

export function PinInputDialog({
  title,
  description,
  pin,
  pinLength = 4,
  message = '',
  messageTone = 'default',
  disabled = false,
  closeDisabled = false,
  showBackButton = false,
  resetLabel,
  onBack,
  onClose,
  onPressKey,
  onReset,
}: PinInputDialogProps) {
  const guideMessage = message || description
  const guideTone = message ? messageTone : 'default'

  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-100 flex items-center justify-center bg-[rgba(18,30,18,0.6)]"
      onClick={onClose}
      role="dialog"
    >
      <section
        aria-label={title}
        className="relative h-[660px] w-[520px] [animation:parent-popup-in_.3s_cubic-bezier(.2,1.3,.5,1)_both]"
        onClick={(event) => event.stopPropagation()}
      >
        <img
          alt=""
          className="pointer-events-none absolute inset-0 size-full object-fill"
          decoding="async"
          src={pinInputPopupUrl}
        />

        {showBackButton && onBack ? (
          <button
            aria-label="뒤로 가기"
            className="absolute top-[7%] left-[8%] z-10 size-[48px] bg-transparent p-0 transition hover:scale-108"
            onClick={onBack}
            type="button"
          >
            <img
              alt=""
              className="block size-full object-contain"
              decoding="async"
              src={backButtonUrl}
            />
          </button>
        ) : null}

        <button
          aria-label="닫기"
          className="absolute top-[7.5%] right-[13.5%] z-10 size-[52px] bg-transparent p-0 transition hover:scale-108 disabled:cursor-not-allowed disabled:opacity-40"
          disabled={closeDisabled}
          onClick={onClose}
          type="button"
        >
          <img
            alt=""
            className="block size-full object-contain"
            decoding="async"
            src={closeButtonUrl}
          />
        </button>

        <h2
          className="absolute top-[12.5%] right-0 left-0 text-center text-[32px] text-[#3a7d2c]"
          style={jua}
        >
          {title}
        </h2>

        <div className="absolute top-[21%] right-[18%] left-[18%] text-center">
          <p
            className="h-14 text-[20px] leading-7 whitespace-pre-line"
            style={{
              ...jua,
              color: messageColorByTone[guideTone],
            }}
          >
            {guideMessage}
          </p>

          <div className="mt-0 -ml-3 flex justify-center gap-1">
            {Array.from({ length: pinLength }).map((_, index) => {
              const isFilled = index < pin.length
              return (
                <img
                  alt=""
                  className="size-[57px] object-contain"
                  key={index}
                  src={isFilled ? pinDotFillUrl : pinDotEmptyUrl}
                />
              )
            })}
          </div>

          <p className="mt-5 -ml-2.5 text-[16px] text-[#9b8862]" style={jua}>
            화면 키패드 또는 키보드 숫자 키를 사용할 수 있어요.
          </p>

          <div className="mx-auto mt-1 grid w-[320px] -translate-x-2 grid-cols-3">
            {DEFAULT_KEYS.map((key, index) =>
              key ? (
                <button
                  className="-m-3 -ml-1.5 mx-auto h-[90px] w-[120px] bg-[length:100%_100%] bg-center bg-no-repeat text-[28px] text-[#3a7d2c] active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={disabled}
                  key={key}
                  onClick={() => onPressKey(key)}
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

          {resetLabel && onReset ? (
            <button
              className="mt-2 bg-transparent text-[17px] text-[#8a7a55] underline disabled:cursor-not-allowed disabled:opacity-40"
              disabled={disabled}
              onClick={onReset}
              style={jua}
              type="button"
            >
              {resetLabel}
            </button>
          ) : null}
        </div>
      </section>
    </div>
  )
}
