import { type CSSProperties, type RefObject } from 'react'

import boardBgUrl from '@/assets/images/dobble-game/board-bg.webp'
import { cardArea, type CardSide } from '@/features/games/dobble/dobbleCards'
import { DOBBLE_GAME_CONFIG as CFG } from '@/features/games/dobble/dobbleGameConfig'
import type { HandSlot } from '@/hooks/useHandGestures'

import { useAttachStream } from './useAttachStream'
import type { DobblePracticeState } from './useDobblePractice'

/**
 * 그림 맞추기 연습 판.
 *
 * 좌표계는 본 게임과 같은 1280x720을 그대로 쓰고, 통째로 축소해 튜토리얼 보드
 * 안에 넣는다. 판정에 쓰는 좌표와 눈에 보이는 좌표가 어긋나지 않게 하려면
 * 배치를 다시 계산하는 것보다 화면만 줄이는 쪽이 안전하다.
 */

/**
 * 본 게임 좌표계를 튜토리얼 보드에 맞춰 줄이는 배율.
 *
 * 연습 판 자리가 663x380px이라 1280x720을 가로에 맞춰 넣으면 이 값이 나온다.
 */
const SCALE = 0.52

/** 플레이 화면과 같은 손 구분 색 */
const HAND_COLOR: Record<HandSlot, string> = {
  a: '#37cfb4',
  b: '#ffab26',
}

const CURSOR_SIZE = 96

export function DobblePracticeStage({
  practice,
  streamRef,
  videoRef,
}: {
  practice: DobblePracticeState
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}) {
  useAttachStream(videoRef, streamRef)

  return (
    <div
      className="relative overflow-hidden rounded-[18px] border-[3px] border-[#c39a5d]"
      style={{
        width: CFG.canvasWidth * SCALE,
        height: CFG.canvasHeight * SCALE,
      }}
    >
      <div
        className="absolute top-0 left-0"
        style={{
          width: CFG.canvasWidth,
          height: CFG.canvasHeight,
          transform: `scale(${SCALE})`,
          transformOrigin: '0 0',
        }}
      >
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          decoding="async"
          draggable={false}
          src={boardBgUrl}
        />

        {(['left', 'right'] as const).map((side) => (
          <PracticeSide key={side} practice={practice} side={side} />
        ))}

        {/* 손 포인터 — 좌표·각도·주먹은 훅이 이 노드에 직접 쓴다. */}
        {(['a', 'b'] as const).map((slot) => (
          <PracticeCursor
            key={slot}
            nodeRef={practice.pointerRefs[slot]}
            slot={slot}
          />
        ))}
      </div>

      {/*
        자기 모습 확인용이자, 동작 인식이 프레임을 읽을 video를 붙잡아 두는
        자리다. 이 엘리먼트가 없으면 MediaPipe가 볼 영상이 사라진다.
      */}
      <video
        autoPlay
        className="pointer-events-none absolute right-1 bottom-1 h-[74px] w-[98px] scale-x-[-1] rounded-[10px] border-2 border-white/70 object-cover shadow-[0_3px_8px_rgba(0,0,0,0.3)]"
        muted
        playsInline
        ref={videoRef}
      />
    </div>
  )
}

function PracticeSide({
  practice,
  side,
}: {
  practice: DobblePracticeState
  side: CardSide
}) {
  const area = cardArea(side)
  const grabbedId = practice.selection[side]

  return (
    <div
      className="absolute"
      style={{ left: area.x, top: area.y, width: area.w, height: area.h }}
    >
      {practice.round[side].map((symbol) => {
        const grabbed = grabbedId === symbol.symbolId
        const matched = practice.matched && grabbed
        // 어느 손이 조준 중인지에 따라 링 색이 달라진다.
        const hoverSlot = (['a', 'b'] as const).find(
          (slot) => practice.hover[slot] === symbol.symbolId,
        )
        const ringColor = hoverSlot
          ? HAND_COLOR[hoverSlot]
          : HAND_COLOR[side === 'left' ? 'a' : 'b']

        return (
          <div
            className="absolute"
            key={symbol.symbolId}
            style={{
              left: symbol.x,
              top: symbol.y,
              transform: 'translate(-50%, -50%)',
            }}
          >
            {hoverSlot || grabbed ? (
              <span
                className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                style={{
                  width: symbol.hitRadius * 2,
                  height: symbol.hitRadius * 2,
                  border: `${grabbed ? 7 : 5}px ${grabbed ? 'solid' : 'dashed'} ${ringColor}`,
                  background: grabbed ? `${ringColor}33` : `${ringColor}14`,
                }}
              />
            ) : null}

            <img
              alt={symbol.name}
              className="relative block"
              decoding="sync"
              draggable={false}
              loading="eager"
              src={symbol.imageUrl}
              style={{
                width: symbol.w,
                height: symbol.h,
                animation: matched
                  ? 'dobble-symbol-pop 0.5s ease-out forwards'
                  : undefined,
                filter: matched
                  ? 'drop-shadow(0 0 16px rgba(255,224,120,0.95))'
                  : 'drop-shadow(0 6px 8px rgba(80,52,18,0.42))',
              }}
            />
          </div>
        )
      })}
    </div>
  )
}

/** 손 포인터. 조준 링이 차오르고 주먹을 쥐면 모양이 바뀐다. */
function PracticeCursor({
  nodeRef,
  slot,
}: {
  nodeRef: RefObject<HTMLDivElement | null>
  slot: HandSlot
}) {
  const color = HAND_COLOR[slot]

  return (
    <div
      className="pointer-events-none absolute top-0 left-0 z-30 opacity-0"
      ref={nodeRef}
      style={
        {
          '--aim': 0,
          '--fist': 0,
          '--hand-angle': '0deg',
        } as CSSProperties
      }
    >
      <div
        className="relative -translate-x-1/2 -translate-y-1/2"
        style={{ width: CURSOR_SIZE, height: CURSOR_SIZE }}
      >
        {/* 조준 링 — 다 차면 잡을 수 있다 */}
        <span
          className="absolute inset-0 rounded-full"
          style={{
            border: `6px solid ${color}`,
            opacity: 'var(--aim)',
            transform: 'scale(calc(0.7 + var(--aim) * 0.3))',
          }}
        />
        {/* 손 — 주먹을 쥐면 작아지고 진해진다 */}
        <span
          className="absolute inset-[18%] rounded-full border-[5px] border-white"
          style={{
            background: color,
            transform: 'scale(calc(1 - var(--fist) * 0.22))',
            boxShadow: '0 3px 8px rgba(60,36,10,0.4)',
          }}
        />
      </div>
    </div>
  )
}
