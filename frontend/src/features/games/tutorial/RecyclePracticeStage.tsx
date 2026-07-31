import { type CSSProperties, type RefObject } from 'react'

import backgroundUrl from '@/assets/images/recycle-game/background.webp'
import { BinRow } from '@/features/games/recycle/BinRow'
import { RECYCLE_GAME_CONFIG as CFG } from '@/features/games/recycle/recycleGameConfig'
import { hitRadius } from '@/features/games/recycle/recycleGrab'
import type { HandSlot } from '@/hooks/useHandGestures'

import { useAttachStream } from './useAttachStream'
import type { RecyclePracticeState } from './useRecyclePractice'

/**
 * 분리수거 연습 판.
 *
 * 좌표계는 본 게임과 같은 1280x720을 그대로 쓰고, 통째로 축소해 튜토리얼 보드
 * 안에 넣는다. 판정에 쓰는 좌표와 눈에 보이는 좌표가 어긋나지 않게 하려면
 * 배치를 다시 계산하는 것보다 화면만 줄이는 쪽이 안전하다. 배경·통·쓰레기가
 * 본 게임과 같은 자리에 있어서, 연습에서 익힌 위치가 본 게임에서 그대로 통한다.
 */

/** 연습 판 자리가 663x380px이라 1280x720을 가로에 맞춰 넣으면 이 값이 나온다. */
const SCALE = 0.52

/** 플레이 화면과 같은 손 구분 색 */
const HAND_COLOR: Record<HandSlot, string> = {
  a: '#37cfb4',
  b: '#ffab26',
}

const CURSOR_SIZE = 96

export function RecyclePracticeStage({
  practice,
  streamRef,
  videoRef,
}: {
  practice: RecyclePracticeState
  streamRef: RefObject<MediaStream | null>
  videoRef: RefObject<HTMLVideoElement | null>
}) {
  useAttachStream(videoRef, streamRef)
  const radius = hitRadius()

  // 본 게임과 같은 규칙: 쓰레기를 든 손이 올라간 통을 먼저 강조한다.
  const holdingSlot = (['a', 'b'] as const).find((slot) =>
    practice.items.some((item) => item.heldBy === slot),
  )
  const activeSlot =
    holdingSlot && practice.binHover[holdingSlot]
      ? holdingSlot
      : practice.binHover.a
        ? 'a'
        : practice.binHover.b
          ? 'b'
          : null

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
          src={backgroundUrl}
        />

        <BinRow
          holding={activeSlot !== null && activeSlot === holdingSlot}
          hoveredBin={activeSlot ? practice.binHover[activeSlot] : null}
        />

        {practice.items.map((item) => {
          const hovered =
            practice.hover.a === item.uid || practice.hover.b === item.uid
          const held = item.heldBy !== null

          return (
            <div
              className="absolute"
              key={item.uid}
              style={{
                left: item.x,
                top: item.y,
                // 본 게임과 같은 이유로 크기를 못박는다. 비워 두면 화면 오른쪽
                // 밖으로 나갈 때 박스가 눌려 쓰레기가 쪼그라든다.
                width: CFG.itemSize,
                height: CFG.itemSize,
                transform: 'translate(-50%, -50%)',
              }}
            >
              {hovered || held ? (
                <span
                  className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
                  style={{
                    width: radius * 2,
                    height: radius * 2,
                    border: held
                      ? '6px solid rgba(255,206,42,0.95)'
                      : '5px dashed rgba(255,255,255,0.85)',
                    background: held
                      ? 'rgba(255,206,42,0.2)'
                      : 'rgba(255,255,255,0.12)',
                  }}
                />
              ) : null}

              <img
                alt={item.name}
                className="relative block object-contain"
                decoding="sync"
                draggable={false}
                loading="eager"
                src={item.imageUrl}
                style={{
                  width: CFG.itemSize,
                  height: CFG.itemSize,
                  filter: held
                    ? 'drop-shadow(0 0 14px rgba(255,224,120,0.9))'
                    : 'drop-shadow(0 6px 8px rgba(20,50,40,0.35))',
                }}
              />
            </div>
          )
        })}

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

      {/* 남은 개수는 판 위에 겹쳐 둔다. 글자를 못 읽는 아이도 진행도를 안다. */}
      <div className="pointer-events-none absolute top-2 left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-full bg-[rgba(255,250,236,0.85)] px-3 py-1.5">
        {Array.from({ length: practice.targetCount }, (_, index) => (
          <span
            className="block size-[16px] rounded-full transition-colors"
            key={index}
            style={{
              background: index < practice.doneCount ? '#6cbc3f' : '#d8c39d',
            }}
          />
        ))}
      </div>
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
