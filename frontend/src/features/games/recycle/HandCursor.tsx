import type { CSSProperties, RefObject } from 'react'

import type { HandSlot } from '@/hooks/useHandGestures'

/**
 * 손 포인터.
 *
 * 손바닥/주먹 그림 위에 조준 링이 얹힌다. 위치·손 각도·주먹 여부는 엔진이
 * `--aim`, `--fist`, `--hand-angle` CSS 변수와 transform으로 직접 쓴다.
 *
 * 그림 맞추기(`DobblePlayPage`)에 같은 커서가 먼저 들어가 있다. 잘 돌아가는
 * 게임을 흔들지 않으려고 그쪽을 여기로 옮기지 않았다. 도블을 손볼 일이 생기면
 * 그때 이 컴포넌트로 합치면 된다.
 */

/** 두 손을 구분하는 색. 화면에 손 이름은 쓰지 않고 색으로만 구분한다. */
const HAND_COLOR: Record<HandSlot, string> = {
  a: '#37cfb4',
  b: '#ffab26',
}

const CURSOR_SIZE = 96
const RING_SIZE = 118

export function HandCursor({
  nodeRef,
  slot,
}: {
  nodeRef: RefObject<HTMLDivElement | null>
  slot: HandSlot
}) {
  const color = HAND_COLOR[slot]

  return (
    <div
      className="pointer-events-none absolute top-0 left-0 z-30 opacity-0 will-change-transform"
      ref={nodeRef}
      style={
        {
          '--aim': 0,
          '--fist': 0,
          '--hand-angle': '0deg',
        } as CSSProperties
      }
    >
      <div className="relative -translate-x-1/2 -translate-y-1/2">
        {/*
          손 모양: 주먹을 쥐면 --fist가 1이 되어 그림이 바뀐다.
          동시에 살짝 작아지고 색이 진해져 "지금 쥐었다"가 몸으로 느껴진다.
        */}
        <div
          className="grid place-items-center"
          style={{
            width: CURSOR_SIZE,
            height: CURSOR_SIZE,
            transform:
              'rotate(calc(var(--hand-angle) + 90deg)) scale(calc(1 - var(--fist) * 0.12))',
            filter:
              'saturate(calc(1 + var(--fist) * 0.6)) brightness(calc(1 - var(--fist) * 0.1))',
            transition: 'transform 110ms ease-out, filter 110ms ease-out',
          }}
        >
          <HandShape color={color} />
        </div>

        {/* 조준 링: --aim(0~1)이 다 차면 주먹을 쥐어 잡을 수 있다. */}
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{
            width: RING_SIZE,
            height: RING_SIZE,
            background: `conic-gradient(from -90deg, ${color} calc(var(--aim) * 360deg), rgba(255,255,255,0.34) 0)`,
            maskImage:
              'radial-gradient(closest-side, transparent 80%, #000 82%)',
            WebkitMaskImage:
              'radial-gradient(closest-side, transparent 80%, #000 82%)',
            filter: 'drop-shadow(0 2px 6px rgba(0,0,0,0.4))',
          }}
        />
      </div>
    </div>
  )
}

/** 펼친 손바닥과 주먹. 두 그림을 겹쳐 두고 `--fist`로 교차 페이드한다. */
function HandShape({ color }: { color: string }) {
  const skin = '#ffd9ae'
  const line = '#c0803f'

  return (
    <svg
      aria-hidden="true"
      className="block h-full w-full drop-shadow-[0_4px_8px_rgba(60,36,10,0.42)]"
      viewBox="0 0 100 100"
    >
      {/* 손목 밴드로 두 손을 색으로 구분한다. */}
      <rect fill={color} height="10" rx="5" width="34" x="33" y="84" />

      <g
        style={{
          opacity: 'calc(1 - var(--fist))',
          transition: 'opacity 110ms',
        }}
      >
        {/* 펼친 손바닥 */}
        <path
          d="M30 52c0-6 4-9 8-9h24c4 0 8 3 8 9v18c0 9-7 16-16 16h-8c-9 0-16-7-16-16z"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
        <rect
          fill={skin}
          height="34"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="33"
          y="28"
        />
        <rect
          fill={skin}
          height="42"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="44"
          y="20"
        />
        <rect
          fill={skin}
          height="36"
          rx="5"
          stroke={line}
          strokeWidth="3"
          width="10"
          x="55"
          y="26"
        />
        <rect
          fill={skin}
          height="28"
          rx="5"
          stroke={line}
          strokeWidth="3"
          transform="rotate(18 68 46)"
          width="9"
          x="64"
          y="34"
        />
        <rect
          fill={skin}
          height="24"
          rx="5"
          stroke={line}
          strokeWidth="3"
          transform="rotate(-34 28 62)"
          width="10"
          x="22"
          y="52"
        />
      </g>

      <g style={{ opacity: 'var(--fist)', transition: 'opacity 110ms' }}>
        {/* 주먹 */}
        <path
          d="M28 54c0-9 7-15 16-15h13c9 0 16 6 16 15v16c0 9-7 16-16 16h-13c-9 0-16-7-16-16z"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
        <path
          d="M34 52h32M42 44v10M52 43v11M62 45v9"
          fill="none"
          stroke={line}
          strokeLinecap="round"
          strokeWidth="3"
        />
        <path
          d="M27 62c-4 1-6 4-5 8 1 4 5 5 8 4"
          fill={skin}
          stroke={line}
          strokeWidth="3"
        />
      </g>
    </svg>
  )
}
