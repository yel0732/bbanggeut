import type { ReactNode } from 'react'

import errorPopupUrl from '@/assets/images/parent-mode/error-popup.webp'
import noticeCancelButtonUrl from '@/assets/images/parent-mode/notice-cancel-button.webp'
import noticeConfirmButtonUrl from '@/assets/images/parent-mode/notice-confirm-button.webp'

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
        className={`${compact ? 'h-[330px] w-[450px]' : 'h-[365px] w-[490px]'} relative -mt-8 [animation:parent-popup-in_.25s_cubic-bezier(.2,1.3,.5,1)_both]`}
      >
        <img
          alt=""
          className="pointer-events-none absolute inset-0 size-full object-fill"
          src={errorPopupUrl}
        />
        <div className="absolute top-[35%] right-[13%] left-[13%] text-center">
          <h2 className="text-[29px] text-[#754b25]" style={jua}>
            {title}
          </h2>
          {message ? (
            <p
              className="mt-2 text-[19px] leading-6 text-[#796443]"
              style={jua}
            >
              {message}
            </p>
          ) : null}
          {children}
        </div>
        <div className="absolute right-[18%] bottom-[14%] left-[18%] flex justify-center gap-3">
          {onCancel ? (
            <button
              className="relative h-[50px] w-[128px] border-0 bg-transparent p-0 pt-[1px] text-[23px] text-[#765d36] drop-shadow-[0_5px_7px_rgba(75,48,20,.22)] transition hover:scale-103 active:scale-98"
              onClick={onCancel}
              style={jua}
              type="button"
            >
              <img
                alt=""
                className="pointer-events-none absolute inset-0 size-full object-fill"
                src={noticeCancelButtonUrl}
              />
              <span className="relative z-0 inline-block translate-y-[5px]">
                {cancelLabel}
              </span>
            </button>
          ) : null}
          <button
            className="border- relative h-[50px] w-[128px] bg-transparent p-0 text-[23px] text-white drop-shadow-[0_5px_7px_rgba(75,48,20,.22)] transition hover:scale-103 active:scale-98"
            onClick={onConfirm}
            style={jua}
            type="button"
          >
            <img
              alt=""
              className="pointer-events-none absolute inset-0 size-full object-fill"
              src={noticeConfirmButtonUrl}
            />
            <span className="relative z-1 inline-block translate-y-[5px] drop-shadow-[0_2px_2px_rgba(70,93,25,.7)]">
              {confirmLabel}
            </span>
          </button>
        </div>
      </section>
    </div>
  )
}
