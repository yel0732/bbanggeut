import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'

import type { HandGesturesFrame } from '@/hooks/useHandGestures'

import {
  cardCenter,
  slotCenter,
  WORD_MATCH_CONFIG,
  type WordMatchProblem,
} from './wordMatchConfig'

type Origin = { kind: 'tray' } | { kind: 'slot'; index: number }

export type WordMatchCard = {
  id: string
  letter: string
  trayIndex: number
}

export type WordMatchInteraction = {
  aimProgress: number
  cards: WordMatchCard[]
  completed: boolean
  grabbedId: string | null
  hoveredId: string | null
  placements: Array<string | null>
  pointer: { visible: boolean; x: number; y: number; fist: boolean }
}

function distance(x1: number, y1: number, x2: number, y2: number) {
  return Math.hypot(x1 - x2, y1 - y2)
}

function shuffledCards(cards: string[]): WordMatchCard[] {
  const shuffled = cards.map((letter, trayIndex) => ({
    id: `${trayIndex}-${letter}`,
    letter,
    trayIndex,
  }))
  for (let index = shuffled.length - 1; index > 0; index--) {
    const target = Math.floor(Math.random() * (index + 1))
    ;[shuffled[index], shuffled[target]] = [shuffled[target], shuffled[index]]
  }
  return shuffled.map((card, trayIndex) => ({ ...card, trayIndex }))
}

export function useWordMatchInteraction({
  active,
  gesturesRef,
  onComplete,
  problem,
}: {
  active: boolean
  gesturesRef: RefObject<HandGesturesFrame>
  onComplete?: () => void
  problem: WordMatchProblem
}): WordMatchInteraction {
  const cards = useMemo(() => shuffledCards(problem.cards), [problem.cards])
  const [placements, setPlacements] = useState<Array<string | null>>(() =>
    Array(problem.answer.length).fill(null),
  )
  const placementsRef = useRef(placements)
  const [pointer, setPointer] = useState({
    visible: false,
    x: 0,
    y: 0,
    fist: false,
  })
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const [grabbedId, setGrabbedId] = useState<string | null>(null)
  const grabbedRef = useRef<string | null>(null)
  const originRef = useRef<Origin | null>(null)
  const aimRef = useRef<{ id: string | null; since: number }>({
    id: null,
    since: 0,
  })
  const [aimProgress, setAimProgress] = useState(0)
  const canGrabRef = useRef(true)
  const [completed, setCompleted] = useState(false)
  const completionNotifiedRef = useRef(false)

  useEffect(() => {
    placementsRef.current = placements
    const solved = placements.every((id, index) => {
      const card = cards.find((item) => item.id === id)
      return card?.letter === problem.answer[index]
    })
    setCompleted(solved)
  }, [cards, placements, problem.answer])

  useEffect(() => {
    if (completed && !completionNotifiedRef.current) {
      completionNotifiedRef.current = true
      onComplete?.()
    }
  }, [completed, onComplete])

  useEffect(() => {
    const next = Array<string | null>(problem.answer.length).fill(null)
    placementsRef.current = next
    setPlacements(next)
    grabbedRef.current = null
    setGrabbedId(null)
    originRef.current = null
    aimRef.current = { id: null, since: 0 }
    setHoveredId(null)
    setAimProgress(0)
    canGrabRef.current = true
    setCompleted(false)
    completionNotifiedRef.current = false
  }, [active, problem.answer, problem.cards])

  useEffect(() => {
    if (!active) {
      setPointer((current) => ({ ...current, visible: false }))
      return
    }

    let frameId = 0
    const tick = (now: number) => {
      const frame = gesturesRef.current
      // WGAME은 한 손 게임이다. 두 손이 보여도 첫 번째로 잡힌 손 하나만 사용한다.
      const hand = frame.a ?? frame.b
      if (!hand) {
        setPointer((current) =>
          current.visible ? { ...current, visible: false } : current,
        )
        aimRef.current = { id: null, since: 0 }
        setHoveredId(null)
        setAimProgress(0)
        frameId = requestAnimationFrame(tick)
        return
      }

      const x = (1 - hand.x) * WORD_MATCH_CONFIG.width
      const y = hand.y * WORD_MATCH_CONFIG.height
      setPointer({ visible: true, x, y, fist: hand.fist })

      const heldId = grabbedRef.current
      if (heldId) {
        if (!hand.fist) {
          const target = Array.from(
            { length: problem.answer.length },
            (_, index) => index,
          ).find((index) => {
            const point = slotCenter(index, problem.answer.length)
            return distance(x, y, point.x, point.y) <= 66
          })

          setPlacements((current) => {
            const next = [...current]
            const origin = originRef.current
            const oldIndex = next.indexOf(heldId)
            if (oldIndex >= 0) next[oldIndex] = null
            if (target === undefined) {
              if (origin?.kind === 'slot') next[origin.index] = heldId
            } else {
              next[target] = heldId
            }
            placementsRef.current = next
            return next
          })
          grabbedRef.current = null
          setGrabbedId(null)
          originRef.current = null
          canGrabRef.current = true
        }
        frameId = requestAnimationFrame(tick)
        return
      }

      if (hand.fist) {
        const aimed = aimRef.current.id
        const ready =
          aimed !== null &&
          now - aimRef.current.since >= WORD_MATCH_CONFIG.aimMs
        if (ready && canGrabRef.current) {
          const slotIndex = placementsRef.current.indexOf(aimed)
          originRef.current =
            slotIndex >= 0
              ? { kind: 'slot', index: slotIndex }
              : { kind: 'tray' }
          grabbedRef.current = aimed
          setGrabbedId(aimed)
          canGrabRef.current = false
        }
        aimRef.current = { id: null, since: 0 }
        setHoveredId(null)
        setAimProgress(0)
        frameId = requestAnimationFrame(tick)
        return
      }

      canGrabRef.current = true
      const candidate = cards.find((card) => {
        const placedIndex = placementsRef.current.indexOf(card.id)
        const point =
          placedIndex >= 0
            ? slotCenter(placedIndex, problem.answer.length)
            : cardCenter(card.trayIndex)
        return (
          distance(x, y, point.x, point.y) <= WORD_MATCH_CONFIG.cardSize / 2
        )
      })
      const candidateId = candidate?.id ?? null
      if (candidateId !== aimRef.current.id) {
        aimRef.current = { id: candidateId, since: now }
      }
      setHoveredId(candidateId)
      setAimProgress(
        candidateId
          ? Math.min(1, (now - aimRef.current.since) / WORD_MATCH_CONFIG.aimMs)
          : 0,
      )
      frameId = requestAnimationFrame(tick)
    }

    frameId = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frameId)
  }, [active, cards, gesturesRef, problem.answer.length])

  return {
    aimProgress,
    cards,
    completed,
    grabbedId,
    hoveredId,
    placements,
    pointer,
  }
}
