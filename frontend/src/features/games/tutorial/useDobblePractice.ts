import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'

import {
  cardArea,
  type DobbleRound,
  type PlacedSymbol,
} from '@/features/games/dobble/dobbleCards'
import {
  DOBBLE_GAME_CONFIG as DOBBLE_CFG,
  SYMBOL_BASE_SIZE,
} from '@/features/games/dobble/dobbleGameConfig'
import {
  idleAim,
  stepAim,
  type HandAim,
} from '@/features/games/dobble/dobbleGrab'
import { DOBBLE_SYMBOLS } from '@/features/games/dobble/dobbleSymbols'
import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

/**
 * 튜토리얼 그림 맞추기 연습.
 *
 * 조준·잡기 판정은 본 게임과 똑같다 — `dobbleGrab`의 같은 함수를 쓴다. 다른
 * 것은 판의 크기뿐이다. 본 게임은 한쪽에 다섯 마리지만 연습은 세 마리라
 * 처음 해 보는 아이도 공통 그림을 금방 찾는다.
 *
 * 점수도 라운드도 없다. 좌우에서 같은 동물을 한 번 잡으면 끝난다. 틀려도
 * 깎지 않고 잡은 것만 풀어 다시 해 보게 한다.
 */

const { canvasWidth: W, canvasHeight: H } = DOBBLE_CFG
const HAND_SLOTS: HandSlot[] = ['a', 'b']

/** 연습 판 배치. 지터 없이 고정이라 늘 같은 자리에 뜬다. */
const PRACTICE_SLOTS = [
  { x: 0.3, y: 0.24 },
  { x: 0.72, y: 0.46 },
  { x: 0.32, y: 0.76 },
]
const COMMON_SYMBOL_ID = 'frog'
const LEFT_ONLY = ['bear', 'chick']
const RIGHT_ONLY = ['tiger', 'rabbit']
/** 정답을 맞힌 뒤 연출을 보여 주고 넘어가기까지(ms) */
const CELEBRATE_MS = 1400
/** 틀렸을 때 다시 해 보라는 안내를 띄우는 시간(ms) */
const WRONG_HINT_MS = 1600

function place(symbolIds: string[], side: 'left' | 'right'): PlacedSymbol[] {
  const area = cardArea(side)

  return symbolIds.flatMap((symbolId, index) => {
    const symbol = DOBBLE_SYMBOLS.find((item) => item.id === symbolId)
    if (!symbol) return []

    // 연습은 크게 보여 준다. 세 마리뿐이라 커져도 서로 겹치지 않는다.
    const size = SYMBOL_BASE_SIZE * 1.18
    const w = symbol.aspect >= 1 ? size : size * symbol.aspect
    const h = symbol.aspect >= 1 ? size / symbol.aspect : size
    const slot = PRACTICE_SLOTS[index % PRACTICE_SLOTS.length]

    return [
      {
        symbolId: symbol.id,
        name: symbol.name,
        imageUrl: symbol.imageUrl,
        x: Math.min(Math.max(slot.x * area.w, w / 2), area.w - w / 2),
        y: Math.min(Math.max(slot.y * area.h, h / 2), area.h - h / 2),
        w,
        h,
        rotation: 0,
        hitRadius: size * DOBBLE_CFG.hitRadiusScale,
      },
    ]
  })
}

/** 좌우 세 마리씩, 공통 그림은 서로 다른 자리에 둔다. */
function createPracticeRound(): DobbleRound {
  return {
    index: 1,
    commonSymbolId: COMMON_SYMBOL_ID,
    left: place([LEFT_ONLY[0], COMMON_SYMBOL_ID, LEFT_ONLY[1]], 'left'),
    right: place([RIGHT_ONLY[0], RIGHT_ONLY[1], COMMON_SYMBOL_ID], 'right'),
  }
}

/**
 * 연습 중 아이에게 보여 줄 상태.
 *
 * - `idle`: 아직 아무것도 안 잡음
 * - `one-side`: 한쪽만 잡음 — 반대쪽도 찾으라고 알려 준다
 * - `wrong`: 서로 다른 동물을 잡음 — 다시 해 보라고 알려 준다
 * - `matched`: 정답
 */
export type PracticeFeedback = 'idle' | 'one-side' | 'wrong' | 'matched'

export type DobblePracticeState = {
  round: DobbleRound
  /** 좌우 각각 잡혀 있는 그림 id */
  selection: Record<'left' | 'right', string | null>
  /** 손별 조준 중인 그림 id (같은 면일 때만) */
  hover: Record<HandSlot, string | null>
  /** 정답을 맞혀 축하 연출 중인지 */
  matched: boolean
  /** 지금 띄울 안내. 화면 문구는 이 값으로 고른다. */
  feedback: PracticeFeedback
  /** 손 포인터 DOM — 좌표·각도·주먹·링을 여기에 직접 쓴다 */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
}

export function useDobblePractice({
  active,
  gesturesRef,
  onComplete,
  onGrab,
}: {
  active: boolean
  gesturesRef: RefObject<HandGesturesFrame>
  onComplete: () => void
  onGrab?: () => void
}): DobblePracticeState {
  const pointerARef = useRef<HTMLDivElement | null>(null)
  const pointerBRef = useRef<HTMLDivElement | null>(null)
  const pointerRefs = useMemo(() => ({ a: pointerARef, b: pointerBRef }), [])
  const round = useMemo(() => createPracticeRound(), [])

  const [snapshot, setSnapshot] = useState<
    Pick<DobblePracticeState, 'selection' | 'hover' | 'matched' | 'feedback'>
  >({
    selection: { left: null, right: null },
    hover: { a: null, b: null },
    matched: false,
    feedback: 'idle',
  })

  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete
  const onGrabRef = useRef(onGrab)
  onGrabRef.current = onGrab

  useEffect(() => {
    if (!active) {
      setSnapshot({
        selection: { left: null, right: null },
        hover: { a: null, b: null },
        matched: false,
        feedback: 'idle',
      })
      return
    }

    const aim: Record<HandSlot, HandAim> = { a: idleAim(), b: idleAim() }
    const selection: Record<'left' | 'right', string | null> = {
      left: null,
      right: null,
    }
    let matched = false
    let wrongUntil = 0
    let rafId = 0
    let doneTimer = 0
    let publishedKey = ''

    const paintPointer = (
      slot: HandSlot,
      x: number,
      y: number,
      angleRad: number,
      fist: boolean,
    ): void => {
      const el = pointerRefs[slot].current
      if (!el) return
      el.style.opacity = '1'
      el.style.transform = `translate3d(${x}px, ${y}px, 0)`
      el.style.setProperty('--hand-angle', `${(angleRad * 180) / Math.PI}deg`)
      el.style.setProperty('--fist', fist ? '1' : '0')
    }

    const paintAim = (slot: HandSlot, progress: number): void => {
      pointerRefs[slot].current?.style.setProperty('--aim', progress.toFixed(3))
    }

    const loop = (now: number): void => {
      const frame = gesturesRef.current

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          pointerRefs[slot].current?.style.setProperty('opacity', '0')
          aim[slot] = idleAim()
          continue
        }

        // 카메라 미러링: 아이가 든 손이 화면에서도 같은 쪽에 보이게 한다.
        const x = (1 - hand.x) * W
        const y = hand.y * H
        paintPointer(slot, x, y, hand.angle, hand.fist)

        if (matched) {
          aim[slot] = idleAim(hand.fist)
          paintAim(slot, 0)
          continue
        }

        const stepped = stepAim(
          aim[slot],
          { x, y, fist: hand.fist },
          round,
          now,
        )
        paintAim(slot, stepped.progress)
        if (stepped.grabbed) {
          selection[stepped.grabbed.side] = stepped.grabbed.symbolId
          onGrabRef.current?.()
        }
        aim[slot] = stepped.aim
      }

      // 좌우가 하나씩 채워지면 판정한다. 두 손을 동시에 쥘 필요는 없다.
      if (!matched && selection.left && selection.right) {
        if (selection.left === selection.right) {
          matched = true
          doneTimer = window.setTimeout(
            () => onCompleteRef.current(),
            CELEBRATE_MS,
          )
        } else {
          // 틀려도 깎지 않는다. 잡은 것만 풀고 다시 찾게 안내한다.
          selection.left = null
          selection.right = null
          wrongUntil = now + WRONG_HINT_MS
          for (const slot of HAND_SLOTS) {
            aim[slot] = idleAim(aim[slot].grabbedInFist)
            paintAim(slot, 0)
          }
        }
      }

      // 안내는 급한 것부터: 정답 > 틀림 > 한쪽만 잡음 > 기본.
      let feedback: PracticeFeedback = 'idle'
      if (matched) {
        feedback = 'matched'
      } else if (now < wrongUntil) {
        feedback = 'wrong'
      } else if (selection.left || selection.right) {
        feedback = 'one-side'
      }

      const key = [
        selection.left,
        selection.right,
        aim.a.hover?.symbolId,
        aim.b.hover?.symbolId,
        feedback,
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          selection: { ...selection },
          hover: {
            a: aim.a.hover?.symbolId ?? null,
            b: aim.b.hover?.symbolId ?? null,
          },
          matched,
          feedback,
        })
      }

      rafId = requestAnimationFrame(loop)
    }

    rafId = requestAnimationFrame(loop)
    return () => {
      cancelAnimationFrame(rafId)
      // 통과 직후 화면을 벗어나면 다음 단계로 밀지 않는다.
      window.clearTimeout(doneTimer)
    }
  }, [active, gesturesRef, pointerRefs, round])

  return { ...snapshot, pointerRefs, round }
}
