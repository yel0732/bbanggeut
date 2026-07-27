import { useEffect } from 'react'

import { getDobbleSymbol, preloadDobbleSymbols } from './dobbleSymbols'

/**
 * 도블 게임 준비 화면의 튜토리얼 미리보기.
 *
 * 실제 판과 같은 구성(좌우 두 묶음, 공통 그림 하나)을 고정된 예시로 보여 주고
 * 정답 그림에 표시가 깜빡이게 해 "무엇을 찾아야 하는지"를 말 대신 그림으로
 * 알려 준다. 동시에 심볼 이미지 20장을 미리 디코딩해 두어 플레이 화면 진입
 * 직후 첫 라운드가 바로 뜬다.
 */

const DEMO_SIDE_WIDTH = 196
const DEMO_SIDE_HEIGHT = 150
const DEMO_SYMBOL_SIZE = 54

type DemoSlot = {
  symbolId: string
  /** 영역 좌상단 기준 상대 좌표(px) */
  x: number
  y: number
  rotation: number
}

const COMMON_SYMBOL_ID = 'frog'

const LEFT_SLOTS: DemoSlot[] = [
  { symbolId: 'bear', x: 52, y: 42, rotation: -6 },
  { symbolId: COMMON_SYMBOL_ID, x: 142, y: 56, rotation: 5 },
  { symbolId: 'chick', x: 84, y: 116, rotation: -3 },
]

const RIGHT_SLOTS: DemoSlot[] = [
  { symbolId: COMMON_SYMBOL_ID, x: 58, y: 50, rotation: 7 },
  { symbolId: 'tiger', x: 146, y: 44, rotation: -4 },
  { symbolId: 'rabbit', x: 110, y: 118, rotation: 3 },
]

/** 플레이 화면과 같은 손 구분 색 */
const DEMO_HAND_COLOR = { left: '#37cfb4', right: '#ffab26' } as const

export function DobbleTutorialDemo() {
  useEffect(() => {
    void preloadDobbleSymbols()
  }, [])

  return (
    <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 rounded-[24px] border-[3px] border-dashed border-[#c39a5d] bg-[#fffaec]/70 py-3 shadow-[inset_0_2px_10px_rgba(120,80,30,0.12)]">
      <div className="flex items-stretch gap-0 rounded-[18px] border-[5px] border-[#a9762f] bg-[linear-gradient(180deg,#e6b872_0%,#d9a457_100%)] p-1 shadow-[0_8px_16px_rgba(120,80,30,0.28)]">
        <DemoSide handColor={DEMO_HAND_COLOR.left} slots={LEFT_SLOTS} />
        {/* 가운데 덩굴 구분선 */}
        <div className="mx-1 w-[4px] rounded-full bg-[repeating-linear-gradient(180deg,#5fb15a_0_10px,transparent_10px_16px)]" />
        <DemoSide handColor={DEMO_HAND_COLOR.right} slots={RIGHT_SLOTS} />
      </div>
      <p
        className="text-center text-[20px] leading-snug text-[#9a6a34]"
        style={{ fontFamily: "'Jua', sans-serif" }}
      >
        양쪽에 똑같이 있는 그림은 하나예요
        <br />
        손바닥을 올리고 주먹을 쥐면 그 동물을 잡아요
      </p>
    </div>
  )
}

function DemoSide({
  handColor,
  slots,
}: {
  handColor: string
  slots: DemoSlot[]
}) {
  return (
    <div
      className="relative"
      style={{ width: DEMO_SIDE_WIDTH, height: DEMO_SIDE_HEIGHT }}
    >
      {slots.map((slot) => {
        const symbol = getDobbleSymbol(slot.symbolId)
        if (!symbol) return null
        const isCommon = slot.symbolId === COMMON_SYMBOL_ID
        const width =
          symbol.aspect >= 1
            ? DEMO_SYMBOL_SIZE
            : DEMO_SYMBOL_SIZE * symbol.aspect
        const height =
          symbol.aspect >= 1
            ? DEMO_SYMBOL_SIZE / symbol.aspect
            : DEMO_SYMBOL_SIZE

        return (
          <div
            className="absolute"
            key={slot.symbolId}
            style={{
              left: slot.x,
              top: slot.y,
              transform: 'translate(-50%, -50%)',
            }}
          >
            {isCommon ? (
              <span
                className="absolute top-1/2 left-1/2 size-[62px] [animation:dobble-guide-pulse_2.4s_ease-in-out_infinite] rounded-full"
                style={{ border: `4px solid ${handColor}` }}
              />
            ) : null}
            <img
              alt={symbol.name}
              className="relative block"
              decoding="async"
              draggable={false}
              src={symbol.imageUrl}
              style={{
                width,
                height,
                transform: `rotate(${slot.rotation}deg)`,
                filter: 'drop-shadow(0 3px 4px rgba(80,52,18,0.35))',
              }}
            />
          </div>
        )
      })}
    </div>
  )
}
