import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'

import {
  RECYCLE_GAME_CONFIG as CFG,
  DESPAWN_X,
  SPAWN_X,
} from '@/features/games/recycle/recycleGameConfig'
import {
  binAt,
  idleAim,
  riverY,
  screenFromHand,
  stepAim,
  type FloatingItem,
  type HandAim,
} from '@/features/games/recycle/recycleGrab'
import {
  RECYCLE_ITEMS,
  type RecycleCategory,
} from '@/features/games/recycle/recycleItems'
import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

/**
 * 튜토리얼 분리수거 연습.
 *
 * 조준·잡기·놓기 판정은 본 게임과 똑같다 — `recycleGrab`의 같은 함수를 쓴다.
 * 다른 것은 규모뿐이다. 쓰레기 두 개만 띄우고, 둘 다 알맞은 통에 넣으면 끝난다.
 *
 * 점수도 시간 제한도 없다. 다른 통에 넣어도 깎지 않고 그 쓰레기를 물길로
 * 돌려보내 다시 해 보게 한다. 물길 끝까지 흘러간 쓰레기도 왼쪽에서 다시
 * 들어온다 — 연습에서 잡을 것이 사라지면 아이는 아무것도 못 하고 갇힌다.
 */

const HAND_SLOTS: HandSlot[] = ['a', 'b']

/** 연습에서 넣어야 하는 쓰레기 수. 성공을 맛보게 하는 게 목적이라 짧게 끝낸다. */
export const PRACTICE_TARGET_COUNT = 2

/** 연습은 천천히 흐른다. 조작을 처음 익히는 중이라 쫓아다니게 하지 않는다. */
const PRACTICE_SPEED_PX = 46

/**
 * 연습에 쓰는 쓰레기 두 개.
 *
 * 통 그림에 그려진 대표 그림과 가장 비슷한 것으로 골라 아이가 짝을 바로 찾게
 * 한다 — 캔 통에는 캔 그림이, 플라스틱 통에는 페트병 그림이 그려져 있다.
 */
const PRACTICE_ITEM_CODES = ['CAN_DRINK', 'PLASTIC_WATER_BOTTLE']

/** 연습 중 아이에게 보여 줄 상태. */
export type RecyclePracticeFeedback =
  /** 아직 아무것도 안 잡음 */
  | 'idle'
  /** 잡고 있음 — 통으로 가라고 알려 준다 */
  | 'holding'
  /** 알맞은 통에 넣음 */
  | 'correct'
  /** 다른 통에 넣음 — 다시 해 보라고 알려 준다 */
  | 'wrong'
  /** 둘 다 넣음 */
  | 'done'

export type RecyclePracticeState = {
  items: FloatingItem[]
  /** 지금까지 알맞은 통에 넣은 개수 */
  doneCount: number
  targetCount: number
  feedback: RecyclePracticeFeedback
  /** 손별 조준 중인 쓰레기 uid */
  hover: Record<HandSlot, number | null>
  /** 손별 올라가 있는 분리수거 통 — 본 게임과 같이 통을 강조하는 데 쓴다 */
  binHover: Record<HandSlot, RecycleCategory | null>
  /** 손 포인터 DOM — 좌표·각도·주먹·링을 여기에 직접 쓴다 */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
}

function createPracticeItems(): FloatingItem[] {
  return PRACTICE_ITEM_CODES.flatMap((code, index) => {
    const item = RECYCLE_ITEMS.find((candidate) => candidate.code === code)
    if (!item) return []

    // 두 개를 화면 좌우로 벌리고 높이도 갈라 놓아 한 번에 둘 다 보이게 한다.
    const startX = 250 + index * 420
    const laneOffsetY = index === 0 ? -40 : 34

    return [
      {
        uid: index + 1,
        code: item.code,
        name: item.name,
        category: item.category,
        imageUrl: item.imageUrl,
        x: startX,
        y: CFG.river.y + laneOffsetY,
        riverX: startX,
        speedPx: PRACTICE_SPEED_PX,
        laneOffsetY,
        bobPhaseMs: index * 900,
        heldBy: null,
        judging: false,
      },
    ]
  })
}

export function useRecyclePractice({
  active,
  gesturesRef,
  onComplete,
  onCorrect,
  onGrab,
  onWrong,
}: {
  active: boolean
  gesturesRef: RefObject<HandGesturesFrame>
  onComplete: () => void
  onCorrect?: () => void
  onGrab?: () => void
  onWrong?: () => void
}): RecyclePracticeState {
  const pointerARef = useRef<HTMLDivElement | null>(null)
  const pointerBRef = useRef<HTMLDivElement | null>(null)
  const pointerRefs = useMemo(() => ({ a: pointerARef, b: pointerBRef }), [])

  const [snapshot, setSnapshot] = useState<
    Pick<
      RecyclePracticeState,
      'items' | 'doneCount' | 'feedback' | 'hover' | 'binHover'
    >
  >({
    items: [],
    doneCount: 0,
    feedback: 'idle',
    hover: { a: null, b: null },
    binHover: { a: null, b: null },
  })

  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete
  const onCorrectRef = useRef(onCorrect)
  onCorrectRef.current = onCorrect
  const onGrabRef = useRef(onGrab)
  onGrabRef.current = onGrab
  const onWrongRef = useRef(onWrong)
  onWrongRef.current = onWrong

  useEffect(() => {
    if (!active) {
      setSnapshot({
        items: [],
        doneCount: 0,
        feedback: 'idle',
        hover: { a: null, b: null },
        binHover: { a: null, b: null },
      })
      return
    }

    let items = createPracticeItems()
    const aim: Record<HandSlot, HandAim> = { a: idleAim(), b: idleAim() }
    const binHover: Record<HandSlot, RecycleCategory | null> = {
      a: null,
      b: null,
    }
    let doneCount = 0
    let correctUntil = 0
    let wrongUntil = 0
    let finished = false
    let lastTs = performance.now()
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

    const returnToRiver = (uid: number): void => {
      const item = items.find((candidate) => candidate.uid === uid)
      if (!item) return
      item.heldBy = null
      item.x = item.riverX
    }

    const loop = (now: number): void => {
      const dt = Math.min((now - lastTs) / 1000, 0.05)
      lastTs = now
      const frame = gesturesRef.current

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          pointerRefs[slot].current?.style.setProperty('opacity', '0')
          const heldUid = aim[slot].heldUid
          if (heldUid !== null) returnToRiver(heldUid)
          aim[slot] = idleAim()
          binHover[slot] = null
          continue
        }

        const { x, y } = screenFromHand(hand)
        paintPointer(slot, x, y, hand.angle, hand.fist)

        if (finished) {
          paintAim(slot, 0)
          binHover[slot] = null
          continue
        }

        // 놓기 판정과 같은 영역을 써서, 밝아진 통에 놓으면 그 통으로 들어간다.
        binHover[slot] = binAt(x, y)

        const stepped = stepAim(
          aim[slot],
          { x, y, fist: hand.fist },
          items,
          now,
        )
        paintAim(slot, stepped.progress)

        if (stepped.grabbed) {
          stepped.grabbed.heldBy = slot
          onGrabRef.current?.()
        }

        if (stepped.released) {
          const { item, bin } = stepped.released
          if (bin === null) {
            returnToRiver(item.uid)
          } else if (bin === item.category) {
            doneCount++
            correctUntil = now + 900
            items = items.filter((candidate) => candidate.uid !== item.uid)
            onCorrectRef.current?.()
            if (doneCount >= PRACTICE_TARGET_COUNT) {
              finished = true
              doneTimer = window.setTimeout(() => onCompleteRef.current(), 1200)
            }
          } else {
            // 틀려도 깎지 않는다. 물길로 돌려보내 다시 해 보게 한다.
            wrongUntil = now + 1600
            returnToRiver(item.uid)
            onWrongRef.current?.()
          }
        }

        aim[slot] = stepped.aim

        const heldUid = stepped.aim.heldUid
        if (heldUid !== null) {
          const held = items.find((candidate) => candidate.uid === heldUid)
          if (held) {
            held.x = x
            held.y = y
          }
        }
      }

      // 떠내려가기. 끝까지 가면 왼쪽에서 다시 들어온다.
      for (const item of items) {
        if (item.heldBy !== null) continue
        item.riverX += item.speedPx * dt
        if (item.riverX > DESPAWN_X) {
          item.riverX = SPAWN_X
        }
        item.x = item.riverX
        item.y = riverY(item, now)
      }

      let feedback: RecyclePracticeFeedback = 'idle'
      if (finished) {
        feedback = 'done'
      } else if (now < correctUntil) {
        feedback = 'correct'
      } else if (now < wrongUntil) {
        feedback = 'wrong'
      } else if (items.some((item) => item.heldBy !== null)) {
        feedback = 'holding'
      }

      const key = [
        doneCount,
        feedback,
        aim.a.hoverUid,
        aim.b.hoverUid,
        binHover.a,
        binHover.b,
        items
          .map(
            (item) =>
              `${item.uid}:${Math.round(item.x)}:${Math.round(item.y)}:${item.heldBy ?? ''}`,
          )
          .join(','),
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          items: items.map((item) => ({ ...item })),
          doneCount,
          feedback,
          hover: { a: aim.a.hoverUid, b: aim.b.hoverUid },
          binHover: { ...binHover },
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
  }, [active, gesturesRef, pointerRefs])

  return { ...snapshot, targetCount: PRACTICE_TARGET_COUNT, pointerRefs }
}
