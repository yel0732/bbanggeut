import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'

import {
  idleAim,
  screenFromHand,
  slotAt,
  stepAim,
  type HandAim,
  type LetterTile,
} from '@/features/games/wordmatch/wordMatchGrab'
import {
  buildLetterCards,
  WORD_PROBLEMS,
  type WordProblem,
} from '@/features/games/wordmatch/wordMatchProblems'
import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

/**
 * 튜토리얼 낱말 맞추기 연습.
 *
 * 조준·잡기·놓기 판정은 본 게임과 똑같다 — `wordMatchGrab`의 같은 함수를 쓴다.
 * 다른 것은 규모다. 두 글자 한 낱말만 내고, 그것을 맞히면 끝난다.
 *
 * 점수도 시간 제한도 힌트도 없다. 틀려도 깎지 않고 잘못 놓은 글자만 카드 자리로
 * 돌려보내 다시 해 보게 한다 — 연습에서 막히면 아이는 화면이 고장난 줄 안다.
 */

const HAND_SLOTS: HandSlot[] = ['a', 'b']

/** 오답을 보여 주는 시간(ms). 이 동안 글자가 제자리로 돌아간다. */
const WRONG_FEEDBACK_MS = 1400

/** 정답 뒤 다음 단계로 넘기기까지 기다리는 시간(ms). 축하를 볼 틈을 준다. */
const DONE_DELAY_MS = 1300

/**
 * 연습에 쓸 낱말.
 *
 * 두 글자 중에서도 그림만 보고 바로 읽히는 것을 고른다. 여기서 낱말을 읽어 내는
 * 것까지 시험하면 조작을 배우기 전에 막힌다.
 */
const PRACTICE_PROBLEM_CODE = 'RABBIT'

/** 연습 중 아이에게 보여 줄 상태. */
export type WordMatchPracticeFeedback =
  /** 아직 아무것도 안 잡음 */
  | 'idle'
  /** 잡고 있음 — 칸으로 가라고 알려 준다 */
  | 'holding'
  /** 칸을 채웠는데 틀림 — 다시 해 보라고 알려 준다 */
  | 'wrong'
  /** 낱말을 완성함 */
  | 'done'

export type WordMatchPracticeState = {
  problem: WordProblem | null
  tiles: LetterTile[]
  feedback: WordMatchPracticeFeedback
  /** 손별 조준 중인 글자 uid */
  hover: Record<HandSlot, number | null>
  /** 손별 올라가 있는 정답 칸 번호 — 본 게임처럼 칸을 강조하는 데 쓴다 */
  slotHover: Record<HandSlot, number | null>
  /** 손 포인터 DOM — 좌표·각도·주먹·링을 여기에 직접 쓴다 */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
}

const PRACTICE_PROBLEM =
  WORD_PROBLEMS.find((problem) => problem.code === PRACTICE_PROBLEM_CODE) ??
  WORD_PROBLEMS[0]

function createTiles(problem: WordProblem): LetterTile[] {
  return buildLetterCards(problem).map((letter, trayIndex) => ({
    uid: trayIndex + 1,
    letter,
    trayIndex,
    slotIndex: null,
    locked: false,
    heldBy: null,
    handX: 0,
    handY: 0,
  }))
}

export function useWordMatchPractice({
  active,
  gesturesRef,
  onComplete,
  onCorrect,
  onGrab,
  onPlace,
  onWrong,
}: {
  active: boolean
  gesturesRef: RefObject<HandGesturesFrame>
  onComplete: () => void
  onCorrect?: () => void
  onGrab?: () => void
  onPlace?: () => void
  onWrong?: () => void
}): WordMatchPracticeState {
  const pointerARef = useRef<HTMLDivElement | null>(null)
  const pointerBRef = useRef<HTMLDivElement | null>(null)
  const pointerRefs = useMemo(() => ({ a: pointerARef, b: pointerBRef }), [])

  const [snapshot, setSnapshot] = useState<
    Pick<WordMatchPracticeState, 'problem' | 'tiles' | 'feedback' | 'hover'> & {
      slotHover: Record<HandSlot, number | null>
    }
  >({
    problem: null,
    tiles: [],
    feedback: 'idle',
    hover: { a: null, b: null },
    slotHover: { a: null, b: null },
  })

  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete
  const onCorrectRef = useRef(onCorrect)
  onCorrectRef.current = onCorrect
  const onGrabRef = useRef(onGrab)
  onGrabRef.current = onGrab
  const onPlaceRef = useRef(onPlace)
  onPlaceRef.current = onPlace
  const onWrongRef = useRef(onWrong)
  onWrongRef.current = onWrong

  useEffect(() => {
    if (!active) {
      setSnapshot({
        problem: null,
        tiles: [],
        feedback: 'idle',
        hover: { a: null, b: null },
        slotHover: { a: null, b: null },
      })
      return
    }

    const problem = PRACTICE_PROBLEM
    const wordLength = problem.answer.length
    const tiles = createTiles(problem)
    const aim: Record<HandSlot, HandAim> = { a: idleAim(), b: idleAim() }
    const slotHover: Record<HandSlot, number | null> = { a: null, b: null }
    let wrongUntil = 0
    let finished = false
    let lastPublished = ''
    let rafId = 0
    let doneTimer = 0

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

    /** 잡고 있던 글자를 원래 카드 자리로 돌려보낸다. */
    const returnToTray = (tile: LetterTile): void => {
      tile.heldBy = null
      tile.slotIndex = null
    }

    const loop = (now: number): void => {
      const frame = gesturesRef.current

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          pointerRefs[slot].current?.style.setProperty('opacity', '0')
          const heldUid = aim[slot].heldUid
          if (heldUid !== null) {
            const held = tiles.find((tile) => tile.uid === heldUid)
            if (held) returnToTray(held)
          }
          aim[slot] = idleAim()
          slotHover[slot] = null
          continue
        }

        const { x, y } = screenFromHand(hand)
        paintPointer(slot, x, y, hand.angle, hand.fist)

        if (finished) {
          paintAim(slot, 0)
          slotHover[slot] = null
          continue
        }

        // 놓기 판정과 같은 영역을 쓴다. 밝아진 칸에 놓으면 그 칸에 들어간다.
        slotHover[slot] = slotAt(x, y, wordLength)

        const stepped = stepAim(
          aim[slot],
          { x, y, fist: hand.fist },
          tiles,
          wordLength,
          now,
        )
        paintAim(slot, stepped.progress)

        if (stepped.grabbed) {
          stepped.grabbed.heldBy = slot
          stepped.grabbed.slotIndex = null
          onGrabRef.current?.()
        }

        if (stepped.released) {
          const { tile, slotIndex } = stepped.released
          const taken =
            slotIndex === null
              ? false
              : tiles.some(
                  (other) =>
                    other.uid !== tile.uid && other.slotIndex === slotIndex,
                )

          if (slotIndex === null || taken) {
            // 칸 밖이거나 이미 찬 칸이면 카드 자리로 돌아간다.
            returnToTray(tile)
          } else {
            tile.heldBy = null
            tile.slotIndex = slotIndex
            onPlaceRef.current?.()
          }
        }

        aim[slot] = stepped.aim

        const heldUid = stepped.aim.heldUid
        if (heldUid !== null) {
          const held = tiles.find((tile) => tile.uid === heldUid)
          if (held) {
            held.handX = x
            held.handY = y
          }
        }
      }

      /*
        칸이 다 차면 채점한다.

        본 게임과 달리 힌트도 고정도 없다. 맞으면 끝내고, 틀리면 잘못 놓은 글자만
        카드 자리로 돌려보낸다 — 맞게 놓은 글자까지 흩어 버리면 아이는 방금 무엇을
        맞혔는지 잃는다.
      */
      if (!finished && now >= wrongUntil) {
        const placed: LetterTile[] = []
        for (let index = 0; index < wordLength; index++) {
          const tile = tiles.find(
            (candidate) =>
              candidate.slotIndex === index && candidate.heldBy === null,
          )
          if (!tile) break
          placed.push(tile)
        }

        if (placed.length === wordLength) {
          const wrong = placed.filter(
            (tile, index) => tile.letter !== problem.answer[index],
          )

          if (wrong.length === 0) {
            finished = true
            onCorrectRef.current?.()
            doneTimer = window.setTimeout(
              () => onCompleteRef.current(),
              DONE_DELAY_MS,
            )
          } else {
            wrongUntil = now + WRONG_FEEDBACK_MS
            for (const tile of wrong) tile.slotIndex = null
            onWrongRef.current?.()
          }
        }
      }

      let feedback: WordMatchPracticeFeedback = 'idle'
      if (finished) {
        feedback = 'done'
      } else if (now < wrongUntil) {
        feedback = 'wrong'
      } else if (tiles.some((tile) => tile.heldBy !== null)) {
        feedback = 'holding'
      }

      const key = [
        feedback,
        aim.a.hoverUid,
        aim.b.hoverUid,
        slotHover.a,
        slotHover.b,
        tiles
          .map(
            (tile) =>
              `${tile.uid}:${tile.slotIndex ?? ''}:${tile.heldBy ?? ''}:${Math.round(tile.handX)}:${Math.round(tile.handY)}`,
          )
          .join(','),
      ].join('|')

      if (key !== lastPublished) {
        lastPublished = key
        setSnapshot({
          problem,
          tiles: tiles.map((tile) => ({ ...tile })),
          feedback,
          hover: { a: aim.a.hoverUid, b: aim.b.hoverUid },
          slotHover: { ...slotHover },
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

  return { ...snapshot, pointerRefs }
}
