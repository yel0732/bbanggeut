import type { ReactNode } from 'react'

import errorPopupUrl from '@/assets/images/parent-mode/error-popup.webp'

const jua = { fontFamily: "'Jua', sans-serif" } as const

type ParentNoticeDialogProps = {
  title: string
  message?: string
  children?: ReactNode
  compact?: boolean
  confirmLabel?: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel?: () => void
}

export function ParentNoticeDialog({
  cancelLabel = '취소',
  children,
  compact = false,
  confirmLabel = '확인',
  message,
  onCancel,
  onConfirm,
  title,
}: ParentNoticeDialogProps) {
  return (
    <div
      aria-modal="true"
      className="absolute inset-0 z-100 flex items-center justify-center bg-[rgba(18,30,18,.62)]"
      role="dialog"
    >
      <section
        className={`${compact ? 'h-[450px] w-[600px]' : 'h-[480px] w-[640px]'} relative -mt-8 [animation:parent-popup-in_.25s_cubic-bezier(.2,1.3,.5,1)_both]`}
      >
        <img
          alt=""
          className="pointer-events-none absolute inset-0 size-full object-fill"
          src={errorPopupUrl}
        />
        <div className="absolute top-[35%] right-[13%] left-[13%] text-center">
          <h2 className="text-[30px] text-[#754b25]" style={jua}>
            {title}
          </h2>
          {message ? (
            <p
              className="mt-3 text-[19px] leading-7 text-[#796443]"
              style={jua}
            >
              {message}
            </p>
          ) : null}
          {children}
        </div>
        <div className="absolute right-[18%] bottom-[12%] left-[18%] flex justify-center gap-3">
          {onCancel ? (
            <button
              className="h-12 min-w-[140px] rounded-2xl border-[3px] border-[#c8aa70] bg-[#fff8e9] px-6 text-[19px] text-[#765d36] shadow-md transition hover:scale-103"
              onClick={onCancel}
              style={jua}
              type="button"
            >
              {cancelLabel}
            </button>
          ) : null}
          <button
            className="h-12 min-w-[140px] rounded-2xl border-[3px] border-[#668c3c] bg-[#83ad4f] px-6 text-[19px] text-white shadow-md transition hover:scale-103"
            onClick={onConfirm}
            style={jua}
            type="button"
          >
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
