import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type RefObject,
} from 'react'

import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

import {
  cardArea,
  createDobbleRound,
  type CardSide,
  type DobbleRound,
} from './dobbleCards'
import { DOBBLE_GAME_CONFIG as CFG } from './dobbleGameConfig'

export type DobblePhase =
  | 'waiting-hands'
  | 'countdown'
  | 'playing'
  | 'paused'
  | 'interrupted'
  | 'error'
  | 'ended'

export type HandHover = {
  side: CardSide
  symbolId: string
}

/** 한쪽에서 잡혀 있는 그림. 판정되거나 다시 잡을 때까지 유지된다. */
export type CardSelection = {
  symbolId: string
  /** 이 그림을 잡은 손 — 표시 색에만 쓴다. */
  by: HandSlot
}

export type JudgeKind = 'correct' | 'wrong'

export type DobbleFeedback = {
  kind: JudgeKind
  /** 판정 대상 심볼 (정답이면 맞춘 그림) */
  symbolId: string
}

export type DobbleGameResult = {
  score: number
  matchedCount: number
  roundCount: number
  /** 힌트가 켜진 라운드 수 */
  hintCount: number
  activePlaySec: number
  ruleVersion: string
  perfectCount: number
  excellentCount: number
  goodCount: number
  incompleteCount: number
  averageReactionTimeMs: number
  cardResults: DobbleCardResult[]
  /** 결과 정합성 통과 여부 */
  valid: boolean
}

export type DobbleCardResult = {
  roundNo: number
  comparisonCount: number
  wrongCount: number
  status: 'COMPLETED' | 'INCOMPLETE'
  solveTimeMs: number | null
}

export type DobbleGameState = {
  phase: DobblePhase
  countdown: number
  remainingSec: number
  score: number
  matchedCount: number
  round: DobbleRound
  /** 좌우 각각 잡혀 있는 그림 */
  selection: Record<CardSide, CardSelection | null>
  /** 손별 현재 조준 중인 그림 */
  hover: Record<HandSlot, HandHover | null>
  feedback: DobbleFeedback | null
  /** 힌트가 켜진 면. 그 면의 정답 동물만 반짝인다. */
  hintSide: CardSide | null
  /** 손 포인터 DOM 노드 — 좌표/각도/주먹 상태를 리렌더 없이 여기에 직접 쓴다. */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
  pause: () => void
  resume: () => void
  restart: () => void
}

/** 화면에 발행하는 상태. 값이 바뀐 프레임에만 갱신한다. */
type Snapshot = Omit<
  DobbleGameState,
  'pointerRefs' | 'pause' | 'resume' | 'restart'
>

type HandAim = {
  hover: HandHover | null
  /** hover가 시작된 시각 */
  since: number
  /** 링이 다 차서 잡을 수 있는 상태인지 */
  ready: boolean
  /** 이번에 주먹을 쥔 동안 이미 잡았는지 — 손을 펴야 다시 잡을 수 있다 */
  grabbedInFist: boolean
}

type Engine = {
  phase: DobblePhase
  round: DobbleRound
  roundStartedAt: number
  roundCount: number
  score: number
  scoreSum: number
  matchedCount: number
  playElapsedMs: number
  countdownEndsAt: number
  lastFrameTs: number
  lastHandSeenAt: number
  aim: Record<HandSlot, HandAim>
  selection: Record<CardSide, CardSelection | null>
  feedback: DobbleFeedback | null
  /** 이번 라운드에서 틀린 횟수 */
  wrongInRound: number
  /** 힌트가 켜진 면 */
  hintSide: CardSide | null
  /** 힌트가 켜진 라운드 수 */
  hintCount: number
  cardResults: DobbleCardResult[]
  /** 이 시각까지는 새 입력을 받지 않는다 (중복 판정 방지) */
  inputLockedUntil: number
  /** 연출이 끝난 뒤 할 일 */
  pendingAfterFeedback: 'next-round' | 'retry' | null
  ended: boolean
}

const { canvasWidth: W, canvasHeight: H } = CFG
const HAND_SLOTS: HandSlot[] = ['a', 'b']

function idleAim(grabbedInFist = false): HandAim {
  return { hover: null, since: 0, ready: false, grabbedInFist }
}

function emptyAim(): Record<HandSlot, HandAim> {
  return { a: idleAim(), b: idleAim() }
}

function createEngine(now: number): Engine {
  return {
    phase: 'waiting-hands',
    round: createDobbleRound(1),
    roundStartedAt: now,
    roundCount: 1,
    score: 0,
    scoreSum: 0,
    matchedCount: 0,
    playElapsedMs: 0,
    countdownEndsAt: 0,
    lastFrameTs: now,
    lastHandSeenAt: now,
    aim: emptyAim(),
    selection: { left: null, right: null },
    feedback: null,
    wrongInRound: 0,
    hintSide: null,
    hintCount: 0,
    cardResults: [],
    inputLockedUntil: 0,
    pendingAfterFeedback: null,
    ended: false,
  }
}

/**
 * 정답을 빨리 찾을수록 보너스 점수를 준다.
 *
 * 힌트를 본 라운드는 기본 점수만 준다. 아이 화면에는 감점처럼 보이지 않는다.
 */
function scoreForComparisonCount(comparisonCount: number): number {
  if (comparisonCount === 1) return 1000
  if (comparisonCount <= 4) return 850
  return 700
}

/** 손바닥이 올라간 그림. 히트 영역이 겹치면 더 가까운 쪽을 고른다. */
function hitTest(x: number, y: number, round: DobbleRound): HandHover | null {
  let best: HandHover | null = null
  let bestDistSq = Infinity

  for (const side of ['left', 'right'] as const) {
    const area = cardArea(side)
    for (const symbol of round[side]) {
      const dx = x - (area.x + symbol.x)
      const dy = y - (area.y + symbol.y)
      const distSq = dx * dx + dy * dy
      if (
        distSq <= symbol.hitRadius * symbol.hitRadius &&
        distSq < bestDistSq
      ) {
        bestDistSq = distSq
        best = { side, symbolId: symbol.symbolId }
      }
    }
  }

  return best
}

/**
 * 도블(그림 맞추기) 게임 엔진.
 *
 * 양손 인식 → 3초 카운트다운 → 라운드 반복 → 시간 종료 흐름을 담당한다.
 * 손바닥을 그림 위에 올려 링이 다 차면 주먹을 쥐어 그 그림을 잡는다. 잡기는
 * 주먹을 쥐는 순간만 인정하므로 계속 쥐고 있어도 다시 잡히지 않는다.
 *
 * 잡은 그림은 좌우 각각 유지된다. 왼쪽을 잡고 손을 내린 뒤 오른쪽을 잡아도 되고,
 * 한 손으로 순서대로 잡아도 된다. 두 손을 동시에 쥐고 있을 필요는 없다. 좌우가
 * 모두 채워지면 판정하고, 판정 직후에는 연출이 끝날 때까지 입력을 잠가 같은 동작이
 * 두 번 반영되지 않게 한다.
 *
 * 렌더 비용을 줄이기 위해 손 좌표·각도·주먹 상태는 React state로 올리지 않고
 * `pointerRefs`가 가리키는 DOM 노드의 transform과 CSS 변수에 직접 쓴다. state
 * 스냅샷은 실제로 값이 바뀐 프레임에만 발행한다.
 */
export function useDobbleGame(
  gesturesRef: RefObject<HandGesturesFrame>,
  active: boolean,
  onEnd: (result: DobbleGameResult) => void,
  onJudge?: (kind: JudgeKind) => void,
  onGrab?: () => void,
  cameraError?: boolean,
): DobbleGameState {
  const engineRef = useRef<Engine | null>(null)
  const pointerARef = useRef<HTMLDivElement | null>(null)
  const pointerBRef = useRef<HTMLDivElement | null>(null)
  const pointerRefs = useMemo(() => ({ a: pointerARef, b: pointerBRef }), [])

  const onEndRef = useRef(onEnd)
  onEndRef.current = onEnd
  const onJudgeRef = useRef(onJudge)
  onJudgeRef.current = onJudge
  const onGrabRef = useRef(onGrab)
  onGrabRef.current = onGrab
  const cameraErrorRef = useRef(cameraError)
  cameraErrorRef.current = cameraError

  const [snapshot, setSnapshot] = useState<Snapshot>(() => ({
    phase: 'waiting-hands',
    countdown: CFG.countdownSec,
    remainingSec: CFG.durationSec,
    score: 0,
    matchedCount: 0,
    round: createDobbleRound(1),
    selection: { left: null, right: null },
    hover: { a: null, b: null },
    feedback: null,
    hintSide: null,
  }))

  const pause = useCallback(() => {
    const engine = engineRef.current
    if (
      engine &&
      (engine.phase === 'playing' || engine.phase === 'interrupted')
    ) {
      engine.phase = 'paused'
    }
  }, [])

  const resume = useCallback(() => {
    const engine = engineRef.current
    if (engine && engine.phase === 'paused') {
      // 이어하기: 잡은 그림을 놓고 카운트다운부터 다시.
      engine.aim = emptyAim()
      engine.selection = { left: null, right: null }
      engine.feedback = null
      engine.pendingAfterFeedback = null
      engine.inputLockedUntil = 0
      engine.phase = 'countdown'
      engine.countdownEndsAt = performance.now() + CFG.countdownSec * 1000
    }
  }, [])

  const restart = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    // 다시 시작: 임시 점수·라운드를 모두 버리고 처음부터.
    const fresh = createEngine(performance.now())
    fresh.lastFrameTs = engine.lastFrameTs
    engineRef.current = fresh
  }, [])

  useEffect(() => {
    if (!active) {
      return
    }

    engineRef.current = createEngine(performance.now())
    let rafId = 0
    let publishedKey = ''

    /** 손 위치/각도/주먹 상태를 DOM에 직접 반영한다. (리렌더 없음) */
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

    const hidePointer = (slot: HandSlot): void => {
      const el = pointerRefs[slot].current
      if (el) el.style.opacity = '0'
    }

    /** 조준 링 진행도(0~1)를 CSS 변수로 넘긴다. */
    const paintAim = (slot: HandSlot, progress: number): void => {
      pointerRefs[slot].current?.style.setProperty('--aim', progress.toFixed(3))
    }

    const judge = (engine: Engine, now: number): void => {
      const left = engine.selection.left
      const right = engine.selection.right
      // 좌우가 하나씩 채워지면 판정한다. 두 손을 동시에 쥘 필요는 없다.
      if (!left || !right) return

      const correct = left.symbolId === right.symbolId

      if (correct) {
        const comparisonCount = engine.wrongInRound + 1
        const solveTimeMs = Math.max(0, Math.round(now - engine.roundStartedAt))
        const gained = scoreForComparisonCount(comparisonCount)
        engine.score += gained
        engine.scoreSum += gained
        engine.matchedCount++
        engine.cardResults.push({
          comparisonCount,
          roundNo: engine.roundCount,
          solveTimeMs,
          status: 'COMPLETED',
          wrongCount: engine.wrongInRound,
        })
        engine.feedback = { kind: 'correct', symbolId: left.symbolId }
        engine.inputLockedUntil = now + CFG.correctFeedbackMs
        engine.pendingAfterFeedback = 'next-round'
      } else {
        // 오답은 점수를 깎지 않는다. 다시 찾도록 안내만 한다.
        engine.wrongInRound++
        if (
          engine.hintSide === null &&
          engine.wrongInRound >= CFG.hintAfterWrongCount
        ) {
          // 한쪽 면만 알려 준다. 반대쪽은 아이가 직접 찾는다.
          engine.hintSide = Math.random() < 0.5 ? 'left' : 'right'
          engine.hintCount++
        }
        engine.feedback = { kind: 'wrong', symbolId: left.symbolId }
        engine.inputLockedUntil = now + CFG.wrongFeedbackMs
        engine.pendingAfterFeedback = 'retry'
      }

      // 판정 즉시 잡은 그림과 조준을 비운다. 주먹 상태는 유지해 다시 쥐어야 잡힌다.
      engine.selection = { left: null, right: null }
      for (const slot of HAND_SLOTS) {
        engine.aim[slot] = idleAim(engine.aim[slot].grabbedInFist)
        paintAim(slot, 0)
      }
      onJudgeRef.current?.(correct ? 'correct' : 'wrong')
    }

    const readHands = (engine: Engine, now: number): boolean => {
      const frame = gesturesRef.current
      let anyVisible = false

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          hidePointer(slot)
          engine.aim[slot] = idleAim()
          // 잡아 둔 그림은 그대로 둔다. 한 손으로 하나 잡고 손을 내렸다가
          // 다른 쪽을 잡아도 되게 하려면 손이 사라져도 유지해야 한다.
          continue
        }

        anyVisible = true

        // 카메라 미러링: 아이가 든 손이 화면에서도 같은 쪽에 보이도록 x를 뒤집는다.
        const x = (1 - hand.x) * W
        const y = hand.y * H
        paintPointer(slot, x, y, hand.angle, hand.fist)

        const aim = engine.aim[slot]

        if (engine.phase !== 'playing' || now < engine.inputLockedUntil) {
          // 입력 잠금 구간에서는 손만 보여 주고 조준/잡기는 진행하지 않는다.
          engine.aim[slot] = idleAim(hand.fist)
          paintAim(slot, 0)
          continue
        }

        // 주먹을 쥐는 동안에는 조준 대상을 갱신하지 않는다. 손가락을 접는 사이
        // 손바닥 중심이 밀려 옆 동물로 넘어가는 사고를 막는다.
        const hover = hand.fist ? aim.hover : hitTest(x, y, engine.round)
        const sameHover =
          hover !== null &&
          aim.hover !== null &&
          hover.side === aim.hover.side &&
          hover.symbolId === aim.hover.symbolId

        if (!sameHover) {
          engine.aim[slot] = {
            hover,
            since: hover ? now : 0,
            ready: false,
            grabbedInFist: false,
          }
          paintAim(slot, 0)
          continue
        }

        const progress = Math.min(1, (now - aim.since) / CFG.aimMs)
        const ready = progress >= 1
        paintAim(slot, progress)

        // 손을 편 상태에서 조준하고, 주먹을 쥐면 잡는다. 링이 덜 찼는데 먼저
        // 쥐었어도 다 차는 순간 한 번 잡아 준다. 한 번 쥔 동안 잡기는 한 번뿐이라
        // 계속 쥐고 있어도 다시 잡히지 않는다.
        let grabbedInFist = hand.fist ? aim.grabbedInFist : false
        if (ready && hover !== null && hand.fist && !grabbedInFist) {
          engine.selection[hover.side] = { symbolId: hover.symbolId, by: slot }
          grabbedInFist = true
          onGrabRef.current?.()
        }

        engine.aim[slot] = { hover, since: aim.since, ready, grabbedInFist }
      }

      if (anyVisible) {
        engine.lastHandSeenAt = now
      }
      return anyVisible
    }

    const startNextRound = (engine: Engine, now: number): void => {
      engine.roundCount++
      engine.round = createDobbleRound(
        engine.roundCount,
        engine.round.commonSymbolId,
      )
      engine.roundStartedAt = now
      engine.wrongInRound = 0
      engine.hintSide = null
    }

    const loop = (now: number): void => {
      // restart()가 엔진을 교체할 수 있어 매 프레임 현재 엔진을 읽는다.
      const engine = engineRef.current
      if (!engine) return

      const dt = Math.min((now - engine.lastFrameTs) / 1000, 0.05)
      const anyHand = readHands(engine, now)

      // 카메라 오류: 상태를 유지한 채 정지하고, 복구되면 손 인식부터 다시.
      if (cameraErrorRef.current) {
        if (
          engine.phase !== 'error' &&
          engine.phase !== 'paused' &&
          engine.phase !== 'ended'
        ) {
          engine.phase = 'error'
        }
      } else if (engine.phase === 'error') {
        engine.phase = 'waiting-hands'
      }

      switch (engine.phase) {
        case 'waiting-hands':
          if (anyHand) {
            engine.phase = 'countdown'
            engine.countdownEndsAt = now + CFG.countdownSec * 1000
          }
          break
        case 'countdown':
          if (!anyHand) {
            engine.phase = 'waiting-hands'
          } else if (now >= engine.countdownEndsAt) {
            engine.phase = 'playing'
            engine.roundStartedAt = now
          }
          break
        case 'playing':
          if (!anyHand && now - engine.lastHandSeenAt > CFG.handLostGraceMs) {
            // 두 손 모두 오래 사라지면 자동 일시정지.
            engine.phase = 'interrupted'
            engine.aim = emptyAim()
            engine.selection = { left: null, right: null }
            break
          }

          engine.playElapsedMs += dt * 1000

          if (engine.inputLockedUntil > 0) {
            if (now >= engine.inputLockedUntil) {
              // 연출 종료: 정답이면 새 라운드, 오답이면 같은 판으로 다시 도전.
              if (engine.pendingAfterFeedback === 'next-round') {
                startNextRound(engine, now)
              }
              engine.feedback = null
              engine.pendingAfterFeedback = null
              engine.inputLockedUntil = 0
            }
          } else {
            judge(engine, now)
          }

          if (engine.playElapsedMs >= CFG.durationSec * 1000) {
            engine.playElapsedMs = CFG.durationSec * 1000
            engine.phase = 'ended'
          }
          break
        case 'interrupted':
          if (anyHand) {
            engine.phase = 'countdown'
            engine.countdownEndsAt = now + CFG.countdownSec * 1000
          }
          break
        case 'paused':
        case 'error':
        case 'ended':
          break
      }

      engine.lastFrameTs = now

      const countdown =
        engine.phase === 'countdown'
          ? Math.max(1, Math.ceil((engine.countdownEndsAt - now) / 1000))
          : CFG.countdownSec
      const remainingSec = Math.max(
        0,
        Math.ceil(CFG.durationSec - engine.playElapsedMs / 1000),
      )

      // 프레임마다 setState하지 않도록 실제로 바뀐 값이 있을 때만 발행한다.
      const key = [
        engine.phase,
        countdown,
        remainingSec,
        engine.score,
        engine.matchedCount,
        engine.round.index,
        engine.selection.left?.symbolId,
        engine.selection.left?.by,
        engine.selection.right?.symbolId,
        engine.selection.right?.by,
        engine.aim.a.hover?.side,
        engine.aim.a.hover?.symbolId,
        engine.aim.b.hover?.side,
        engine.aim.b.hover?.symbolId,
        engine.feedback?.kind,
        engine.feedback?.symbolId,
        engine.hintSide,
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          phase: engine.phase,
          countdown,
          remainingSec,
          score: engine.score,
          matchedCount: engine.matchedCount,
          round: engine.round,
          selection: { ...engine.selection },
          hover: { a: engine.aim.a.hover, b: engine.aim.b.hover },
          feedback: engine.feedback,
          hintSide: engine.hintSide,
        })
      }

      if (engine.phase === 'ended') {
        if (!engine.ended) {
          engine.ended = true
          if (engine.cardResults.length < engine.roundCount) {
            engine.cardResults.push({
              comparisonCount: engine.wrongInRound,
              roundNo: engine.roundCount,
              solveTimeMs: null,
              status: 'INCOMPLETE',
              wrongCount: engine.wrongInRound,
            })
          }
          // 정합성 검증: 정답 수 ≤ 진행 라운드 수, 점수 = 라운드 점수 합
          const valid =
            engine.matchedCount <= engine.roundCount &&
            engine.score === engine.scoreSum
          const perfectCount = engine.cardResults.filter(
            (round) => round.status === 'COMPLETED' && round.comparisonCount === 1,
          ).length
          const excellentCount = engine.cardResults.filter(
            (round) =>
              round.status === 'COMPLETED' &&
              round.comparisonCount >= 2 &&
              round.comparisonCount <= 4,
          ).length
          const goodCount = engine.cardResults.filter(
            (round) => round.status === 'COMPLETED' && round.comparisonCount >= 5,
          ).length
          const completedReactionTimes = engine.cardResults.flatMap((round) =>
            round.status === 'COMPLETED' && round.solveTimeMs !== null
              ? [round.solveTimeMs]
              : [],
          )
          const solvedCount = perfectCount + excellentCount + goodCount
          const calculatedScore =
            perfectCount * 1000 + excellentCount * 850 + goodCount * 700
          const resultIsValid =
            valid &&
            engine.matchedCount === solvedCount &&
            engine.score === calculatedScore
          onEndRef.current({
            score: engine.score,
            matchedCount: engine.matchedCount,
            roundCount: engine.roundCount,
            hintCount: engine.hintCount,
            activePlaySec: Math.round(engine.playElapsedMs / 1000),
            ruleVersion: CFG.ruleVersion,
            perfectCount,
            excellentCount,
            goodCount,
            incompleteCount: engine.cardResults.length - engine.matchedCount,
            averageReactionTimeMs:
              completedReactionTimes.length > 0
                ? Math.round(
                    completedReactionTimes.reduce((sum, time) => sum + time, 0) /
                      completedReactionTimes.length,
                  )
                : 0,
            cardResults: [...engine.cardResults],
            valid: resultIsValid,
          })
        }
        return
      }

      rafId = requestAnimationFrame(loop)
    }

    rafId = requestAnimationFrame(loop)

    return () => {
      cancelAnimationFrame(rafId)
      engineRef.current = null
    }
  }, [active, gesturesRef, pointerRefs])

  return {
    ...snapshot,
    pointerRefs,
    pause,
    resume,
    restart,
  }
}
