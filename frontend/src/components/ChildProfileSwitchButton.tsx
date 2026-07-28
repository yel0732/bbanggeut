import { Link } from 'react-router-dom'

import buttonUrl from '@/assets/images/profile/profile-switch-button.webp'

const jua = { fontFamily: "'Jua', sans-serif" } as const

type ChildProfileSwitchButtonProps = {
  className?: string
}

export function ChildProfileSwitchButton({
  className = '',
}: ChildProfileSwitchButtonProps) {
  return (
    <Link
      aria-label="프로필 바꾸기"
      className={`absolute top-[21px] right-[25px] z-50 flex h-[46px] w-[210px] items-center justify-center border-0 bg-transparent p-0 text-[26px] text-white drop-shadow-[0_4px_8px_rgba(0,0,0,.25)] transition hover:scale-105 focus-visible:outline-4 focus-visible:outline-offset-2 focus-visible:outline-white active:scale-[.98] ${className}`}
      style={jua}
      to="/profiles"
    >
      <img
        alt=""
        className="pointer-events-none absolute inset-0 h-full w-full object-fill"
        decoding="async"
        draggable={false}
        src={buttonUrl}
      />
      <span className="relative z-[1] pb-[1px] drop-shadow-[0_2px_2px_rgba(62,83,23,.55)]">
        프로필 바꾸기
      </span>
    </Link>
  )
}
