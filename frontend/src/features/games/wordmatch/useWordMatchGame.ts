import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { RefObject } from 'react'

import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

import { WORD_MATCH_CONFIG as CFG } from './wordMatchConfig'
import {
  idleAim,
  screenFromHand,
  slotAt,
  stepAim,
  type HandAim,
  type LetterTile,
} from './wordMatchGrab'
import {
  buildLetterCards,
  pickNextProblem,
  wordLengthForSolvedCount,
  type WordProblem,
} from './wordMatchProblems'

export type WordMatchPhase =
  /** 손이 잡히기를 기다린다 */
  | 'waiting-hands'
  /** 3초 카운트다운 */
  | 'countdown'
  /** 진행 중 */
  | 'playing'
  /** 아이가 일시정지했다 */
  | 'paused'
  /** 손이 사라져 자동 중단됐다 */
  | 'interrupted'
  /** 카메라·인식 오류 */
  | 'error'
  | 'ended'

export type WordMatchJudge = 'correct' | 'wrong'

/** 서버에 보내는 문제별 결과 (WGAME-15). */
export type WordMatchProblemResult = {
  problemCode: string
  questionOrder: number
  wordLength: number
  status: 'COMPLETED' | 'INCOMPLETE'
  wrongCount: number
  hintUsed: boolean
  earnedScore: number
  solveTimeMs: number | null
}

export type WordMatchResult = {
  score: number
  solvedWordCount: number
  incompleteWordCount: number
  wrongCount: number
  hintUsedCount: number
  activePlaySec: number
  rulesVersion: string
  problemResults: WordMatchProblemResult[]
  /** 결과 정합성 통과 여부 */
  valid: boolean
}

export type WordMatchGameState = {
  phase: WordMatchPhase
  countdown: number
  remainingSec: number
  score: number
  solvedWordCount: number
  /** 지금 문제. 없으면 낼 문제가 떨어진 것이다. */
  problem: WordProblem | null
  /** 화면에 그릴 글자들 */
  tiles: LetterTile[]
  /** 방금 판정 결과. 연출 중에만 값이 있다. */
  judge: WordMatchJudge | null
  /** 힌트로 반짝일 정답 칸 번호와 글자 uid (WGAME-13) */
  hint: { slotIndex: number; tileUid: number } | null
  /** 손별 조준 중인 글자 uid */
  hover: Record<HandSlot, number | null>
  /** 손별 올라가 있는 정답 칸 번호 */
  slotHover: Record<HandSlot, number | null>
  /** 손 포인터 DOM 노드 — 좌표/각도/주먹 상태를 리렌더 없이 여기에 직접 쓴다. */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
  pause: () => void
  resume: () => void
  restart: () => void
}

type Snapshot = Omit<
  WordMatchGameState,
  'pointerRefs' | 'pause' | 'resume' | 'restart'
>

type Round = {
  problem: WordProblem
  tiles: LetterTile[]
  startedAt: number
  wrongCount: number
  hintUsed: boolean
  hint: { slotIndex: number; tileUid: number } | null
}

type Engine = {
  phase: WordMatchPhase
  round: Round | null
  questionOrder: number
  score: number
  solvedWordCount: number
  results: WordMatchProblemResult[]
  usedCodes: string[]
  /** 타이머가 흐른 시간(ms). 일시정지·손 놓침 시간은 포함하지 않는다. */
  playElapsedMs: number
  countdownEndsAt: number
  lastFrameTs: number
  lastHandSeenAt: number
  aim: Record<HandSlot, HandAim>
  slotHover: Record<HandSlot, number | null>
  judge: WordMatchJudge | null
  /** 이 시각까지는 새 입력을 받지 않는다 (중복 판정 방지) */
  inputLockedUntil: number
  /** 연출이 끝난 뒤 할 일 */
  pendingAfterJudge: 'next-problem' | 'retry' | null
  /** 낼 문제가 떨어졌는지. 남은 시간이 있어도 여기서 끝낸다. */
  outOfProblems: boolean
  ended: boolean
}

const HAND_SLOTS: HandSlot[] = ['a', 'b']

function emptyAim(): Record<HandSlot, HandAim> {
  return { a: idleAim(), b: idleAim() }
}

/** 문제 하나를 차려 놓는다. 글자 카드를 섞어 한 줄로 세운다. */
function createRound(problem: WordProblem, now: number): Round {
  const letters = buildLetterCards(problem)

  return {
    problem,
    tiles: letters.map((letter, index) => ({
      uid: index + 1,
      letter,
      trayIndex: index,
      slotIndex: null,
      locked: false,
      heldBy: null,
      handX: 0,
      handY: 0,
    })),
    startedAt: now,
    wrongCount: 0,
    hintUsed: false,
    hint: null,
  }
}

function createEngine(now: number): Engine {
  return {
    phase: 'waiting-hands',
    round: null,
    questionOrder: 0,
    score: 0,
    solvedWordCount: 0,
    results: [],
    usedCodes: [],
    playElapsedMs: 0,
    countdownEndsAt: 0,
    lastFrameTs: now,
    lastHandSeenAt: now,
    aim: emptyAim(),
    slotHover: { a: null, b: null },
    judge: null,
    inputLockedUntil: 0,
    pendingAfterJudge: null,
    outOfProblems: false,
    ended: false,
  }
}

/**
 * 다음 문제를 차린다. 낼 문제가 없으면 그 사실을 기록한다.
 *
 * 서버가 한 판에 같은 문제 코드를 두 번 받으면 결과를 거부하므로, 문제를 다시
 * 낼 수는 없다. 문제 목록이 바닥나면 남은 시간이 있어도 끝낸다.
 */
function startNextProblem(engine: Engine, now: number): void {
  const targetLength = wordLengthForSolvedCount(engine.solvedWordCount)
  const problem = pickNextProblem(engine.usedCodes, targetLength)

  if (!problem) {
    engine.round = null
    engine.outOfProblems = true
    return
  }

  engine.questionOrder++
  engine.usedCodes = [...engine.usedCodes, problem.code]
  engine.round = createRound(problem, now)
}

/** 타이머를 한 프레임만큼 흘린다. 제한 시간을 넘기지 않는다. */
function advanceTimer(engine: Engine, dt: number): number {
  return Math.min(engine.playElapsedMs + dt * 1000, CFG.durationSec * 1000)
}

/** 들고 있던 글자를 원래 자리로 돌려보낸다 (WGAME-05, WGAME-17, WGAME-18). */
function releaseAllHeld(engine: Engine): void {
  const round = engine.round
  for (const slot of HAND_SLOTS) {
    const heldUid = engine.aim[slot].heldUid
    if (heldUid !== null && round) {
      const tile = round.tiles.find((candidate) => candidate.uid === heldUid)
      if (tile) tile.heldBy = null
    }
  }
  engine.aim = emptyAim()
  engine.slotHover = { a: null, b: null }
}

/**
 * 낱말 맞추기 게임 엔진 (WGAME).
 *
 * 손 인식 → 3초 카운트다운 → 1분 30초 진행 → 결과 흐름을 담당한다. 글자 카드를
 * 집어 정답 칸에 놓고, 칸이 다 채워지면 왼쪽부터 조합해 정답과 비교한다.
 *
 * 오답이어도 점수를 깎지 않는다. 자리와 글자가 모두 맞은 칸만 고정하고 나머지는
 * 카드 자리로 돌려보내 다시 해 보게 한다(WGAME-09). 같은 문제에서 두 번 틀리면
 * 아직 못 채운 칸 하나와 그 칸에 들어갈 카드를 함께 반짝인다(WGAME-13).
 *
 * 렌더 비용을 줄이기 위해 손 좌표·각도·주먹 상태는 React state로 올리지 않고
 * `pointerRefs`가 가리키는 DOM 노드에 직접 쓴다.
 */
export function useWordMatchGame(
  gesturesRef: RefObject<HandGesturesFrame>,
  active: boolean,
  onEnd: (result: WordMatchResult) => void,
  handlers: {
    onGrab?: () => void
    onPlace?: () => void
    onCorrect?: (word: string) => void
    onWrong?: () => void
  } = {},
  cameraError?: boolean,
): WordMatchGameState {
  const engineRef = useRef<Engine | null>(null)
  const pointerARef = useRef<HTMLDivElement | null>(null)
  const pointerBRef = useRef<HTMLDivElement | null>(null)
  const pointerRefs = useMemo(() => ({ a: pointerARef, b: pointerBRef }), [])

  const onEndRef = useRef(onEnd)
  onEndRef.current = onEnd
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers
  const cameraErrorRef = useRef(cameraError)
  cameraErrorRef.current = cameraError

  const [snapshot, setSnapshot] = useState<Snapshot>(() => ({
    phase: 'waiting-hands',
    countdown: CFG.countdownSec,
    remainingSec: CFG.durationSec,
    score: 0,
    solvedWordCount: 0,
    problem: null,
    tiles: [],
    judge: null,
    hint: null,
    hover: { a: null, b: null },
    slotHover: { a: null, b: null },
  }))

  const pause = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    if (engine.phase === 'playing' || engine.phase === 'interrupted') {
      releaseAllHeld(engine)
      engine.phase = 'paused'
    }
  }, [])

  const resume = useCallback(() => {
    const engine = engineRef.current
    if (engine && engine.phase === 'paused') {
      // 남은 시간은 그대로 이어간다. 손을 편 상태를 확인한 뒤 카운트다운부터.
      engine.phase = 'waiting-hands'
    }
  }, [])

  const restart = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    // 임시 기록을 모두 버리고 새 문제·새 타이머로 시작한다(WGAME-17).
    const fresh = createEngine(performance.now())
    fresh.lastFrameTs = engine.lastFrameTs
    engineRef.current = fresh
  }, [])

  useEffect(() => {
    if (!active) {
      return
    }

    /*
      엔진은 한 판에 하나만 만든다. 이 effect는 카메라 상태가 흔들리면 다시
      도는데, 그때마다 새로 만들면 점수와 남은 시간이 조용히 0으로 돌아간다.
      처음부터 다시 하는 것은 restart()뿐이다.
    */
    engineRef.current ??= createEngine(performance.now())
    engineRef.current.lastFrameTs = performance.now()
    let rafId = 0
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

    const hidePointer = (slot: HandSlot): void => {
      const el = pointerRefs[slot].current
      if (el) el.style.opacity = '0'
    }

    const paintAim = (slot: HandSlot, progress: number): void => {
      pointerRefs[slot].current?.style.setProperty('--aim', progress.toFixed(3))
    }

    /** 힌트 켜기 — 못 채운 칸 하나와 그 칸에 들어갈 카드를 고른다(WGAME-13). */
    const turnOnHint = (round: Round): void => {
      const answer = [...round.problem.answer]

      for (let index = 0; index < answer.length; index++) {
        const filled = round.tiles.find(
          (tile) => tile.slotIndex === index && tile.letter === answer[index],
        )
        if (filled) continue

        // 그 글자를 가진 카드 중 아직 안 놓인 것을 고른다.
        const candidate = round.tiles.find(
          (tile) =>
            tile.letter === answer[index] &&
            !tile.locked &&
            tile.heldBy === null,
        )
        if (candidate) {
          round.hint = { slotIndex: index, tileUid: candidate.uid }
          round.hintUsed = true
          return
        }
      }
    }

    /** 칸이 다 채워졌을 때의 낱말 판정 (WGAME-07~09). */
    const judgeWord = (engine: Engine, now: number): void => {
      const round = engine.round
      if (!round) return

      const answer = [...round.problem.answer]
      const placed = answer.map((_, index) =>
        round.tiles.find((tile) => tile.slotIndex === index),
      )
      // 빈칸이 남아 있으면 판정하지 않는다.
      if (placed.some((tile) => !tile)) return

      const guess = placed.map((tile) => tile!.letter).join('')

      if (guess === round.problem.answer) {
        const earned = round.hintUsed ? CFG.scoreWithHint : CFG.scoreWithoutHint
        engine.score += earned
        engine.solvedWordCount++
        engine.results.push({
          problemCode: round.problem.code,
          questionOrder: engine.questionOrder,
          wordLength: answer.length,
          status: 'COMPLETED',
          wrongCount: round.wrongCount,
          hintUsed: round.hintUsed,
          earnedScore: earned,
          // 서버가 1 이상을 요구한다. 순간에 맞혀도 0이 되지 않게 최소 1을 준다.
          solveTimeMs: Math.max(1, Math.round(now - round.startedAt)),
        })
        engine.judge = 'correct'
        engine.inputLockedUntil = now + CFG.correctFeedbackMs
        engine.pendingAfterJudge = 'next-problem'
        handlersRef.current.onCorrect?.(round.problem.answer)
        return
      }

      // 오답: 점수는 깎지 않는다. 맞은 자리만 고정하고 나머지는 카드로 돌린다.
      round.wrongCount++
      for (let index = 0; index < answer.length; index++) {
        const tile = placed[index]!
        if (tile.letter === answer[index]) {
          tile.locked = true
        } else {
          tile.slotIndex = null
        }
      }
      if (!round.hintUsed && round.wrongCount >= CFG.hintWrongThreshold) {
        turnOnHint(round)
      }
      engine.judge = 'wrong'
      engine.inputLockedUntil = now + CFG.wrongFeedbackMs
      engine.pendingAfterJudge = 'retry'
      handlersRef.current.onWrong?.()
    }

    const readHands = (engine: Engine, now: number): boolean => {
      const frame = gesturesRef.current
      const round = engine.round
      let anyVisible = false
      const inputOpen =
        engine.phase === 'playing' && now >= engine.inputLockedUntil && !!round

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          hidePointer(slot)
          engine.slotHover[slot] = null
          continue
        }

        anyVisible = true
        const { x, y } = screenFromHand(hand)
        paintPointer(slot, x, y, hand.angle, hand.fist)

        if (!inputOpen || !round) {
          paintAim(slot, 0)
          engine.slotHover[slot] = null
          continue
        }

        const wordLength = round.problem.answer.length
        engine.slotHover[slot] = slotAt(x, y, wordLength)

        const stepped = stepAim(
          engine.aim[slot],
          { x, y, fist: hand.fist },
          round.tiles,
          wordLength,
          now,
        )
        paintAim(slot, stepped.progress)

        if (stepped.grabbed) {
          stepped.grabbed.heldBy = slot
          stepped.grabbed.slotIndex = null
          // 힌트로 가리키던 카드를 집으면 안내를 끝낸다.
          if (round.hint?.tileUid === stepped.grabbed.uid) {
            round.hint = null
          }
          handlersRef.current.onGrab?.()
        }

        if (stepped.released) {
          const { tile, slotIndex } = stepped.released
          tile.heldBy = null

          if (slotIndex === null) {
            // 칸이 아닌 곳에 놓으면 카드 자리로 돌아간다.
            tile.slotIndex = null
          } else {
            // 이미 글자가 있는 칸이면 기존 글자를 카드로 돌린다(WGAME-06).
            const occupant = round.tiles.find(
              (candidate) =>
                candidate.uid !== tile.uid && candidate.slotIndex === slotIndex,
            )
            if (occupant?.locked) {
              // 고정된 글자는 밀어낼 수 없다. 집었던 글자를 카드로 돌린다.
              tile.slotIndex = null
            } else {
              if (occupant) occupant.slotIndex = null
              tile.slotIndex = slotIndex
              handlersRef.current.onPlace?.()
            }
          }
        }

        engine.aim[slot] = stepped.aim

        const heldUid = stepped.aim.heldUid
        if (heldUid !== null) {
          const heldTile = round.tiles.find(
            (candidate) => candidate.uid === heldUid,
          )
          if (heldTile) {
            heldTile.handX = x
            heldTile.handY = y
          }
        }
      }

      if (anyVisible) {
        engine.lastHandSeenAt = now
      }
      return anyVisible
    }

    /** 손을 편 상태인지. 재개 조건이다(WGAME-17, WGAME-18). */
    const anyOpenHand = (): boolean => {
      const frame = gesturesRef.current
      return HAND_SLOTS.some((slot) => frame[slot] && !frame[slot]!.fist)
    }

    /** 진행 중이던 문제를 미완료로 기록한다(WGAME-10). */
    const recordIncomplete = (engine: Engine): void => {
      const round = engine.round
      if (!round) return
      engine.results.push({
        problemCode: round.problem.code,
        questionOrder: engine.questionOrder,
        wordLength: round.problem.answer.length,
        status: 'INCOMPLETE',
        wrongCount: round.wrongCount,
        // 서버는 힌트를 썼으면 오답 2회 이상을 요구한다. 미완료 문제는 점수가
        // 0이라 힌트 사용을 기록해도 얻는 것이 없어 false로 보낸다.
        hintUsed: false,
        earnedScore: 0,
        solveTimeMs: null,
      })
    }

    const finish = (engine: Engine, withIncomplete: boolean): void => {
      if (withIncomplete) {
        recordIncomplete(engine)
      }
      releaseAllHeld(engine)
      engine.round = null
      engine.judge = null
      engine.phase = 'ended'
    }

    const loop = (now: number): void => {
      const engine = engineRef.current
      if (!engine) return

      const dt = Math.min((now - engine.lastFrameTs) / 1000, 0.05)
      const anyHand = readHands(engine, now)

      if (cameraErrorRef.current) {
        if (
          engine.phase !== 'error' &&
          engine.phase !== 'paused' &&
          engine.phase !== 'ended'
        ) {
          releaseAllHeld(engine)
          engine.phase = 'error'
        }
      } else if (engine.phase === 'error') {
        engine.phase = 'waiting-hands'
      }

      switch (engine.phase) {
        case 'waiting-hands':
          if (anyHand && anyOpenHand()) {
            engine.phase = 'countdown'
            engine.countdownEndsAt = now + CFG.countdownSec * 1000
          }
          break
        case 'countdown':
          if (!anyHand) {
            engine.phase = 'waiting-hands'
          } else if (now >= engine.countdownEndsAt) {
            engine.phase = 'playing'
            if (!engine.round && !engine.outOfProblems) {
              startNextProblem(engine, now)
            }
          }
          break
        case 'playing': {
          if (!anyHand && now - engine.lastHandSeenAt > CFG.handLostGraceMs) {
            // 손이 오래 사라지면 자동 중단. 이 시간은 플레이 시간에서 빠진다.
            releaseAllHeld(engine)
            engine.phase = 'interrupted'
            break
          }

          engine.playElapsedMs = advanceTimer(engine, dt)

          if (engine.inputLockedUntil > 0) {
            if (now >= engine.inputLockedUntil) {
              if (engine.pendingAfterJudge === 'next-problem') {
                startNextProblem(engine, now)
              }
              engine.judge = null
              engine.pendingAfterJudge = null
              engine.inputLockedUntil = 0
            }
          } else if (engine.round) {
            judgeWord(engine, now)
          }

          // 낼 문제가 떨어지면 남은 시간이 있어도 끝낸다. 미완료 문제는 없다.
          if (engine.outOfProblems && !engine.round) {
            finish(engine, false)
            break
          }

          /*
            시간 종료. 정답 판정과 겹치면 정답을 먼저 본다(WGAME-10) — 위에서
            판정을 이미 처리했고 연출 중이면 inputLockedUntil이 남아 있으므로,
            연출이 끝나기를 기다린 뒤 종료한다.
          */
          if (engine.playElapsedMs >= CFG.durationSec * 1000) {
            if (engine.inputLockedUntil > 0 && now < engine.inputLockedUntil) {
              break
            }
            finish(engine, true)
          }
          break
        }
        case 'interrupted':
          if (anyHand && anyOpenHand()) {
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

      const round = engine.round
      const key = [
        engine.phase,
        countdown,
        remainingSec,
        engine.score,
        engine.solvedWordCount,
        round?.problem.code,
        engine.judge,
        round?.hint?.slotIndex,
        engine.aim.a.hoverUid,
        engine.aim.b.hoverUid,
        engine.slotHover.a,
        engine.slotHover.b,
        round?.tiles
          .map(
            (tile) =>
              `${tile.uid}:${tile.slotIndex ?? ''}:${tile.locked ? 'L' : ''}:${tile.heldBy ?? ''}:${Math.round(tile.handX)}:${Math.round(tile.handY)}`,
          )
          .join(','),
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          phase: engine.phase,
          countdown,
          remainingSec,
          score: engine.score,
          solvedWordCount: engine.solvedWordCount,
          problem: round?.problem ?? null,
          tiles: round ? round.tiles.map((tile) => ({ ...tile })) : [],
          judge: engine.judge,
          hint: round?.hint ?? null,
          hover: { a: engine.aim.a.hoverUid, b: engine.aim.b.hoverUid },
          slotHover: { ...engine.slotHover },
        })
      }

      if (engine.phase === 'ended') {
        if (!engine.ended) {
          engine.ended = true
          const results = [...engine.results]
          const solved = results.filter(
            (item) => item.status === 'COMPLETED',
          ).length
          const incomplete = results.filter(
            (item) => item.status === 'INCOMPLETE',
          ).length
          const wrong = results.reduce((sum, item) => sum + item.wrongCount, 0)
          const hintUsed = results.filter((item) => item.hintUsed).length
          const scoreSum = results.reduce(
            (sum, item) => sum + item.earnedScore,
            0,
          )
          const activePlaySec = Math.round(engine.playElapsedMs / 1000)

          /*
            서버가 검증하는 것과 같은 조건을 먼저 확인한다. 어긋난 결과를 보내면
            거부되므로, 여기서 걸러 저장 실패로 넘긴다.
          */
          const valid =
            results.length > 0 &&
            scoreSum === engine.score &&
            solved === engine.solvedWordCount &&
            incomplete <= 1 &&
            activePlaySec >= 0 &&
            activePlaySec <= CFG.durationSec &&
            results.every(
              (item, index) =>
                item.questionOrder === index + 1 &&
                (item.status === 'COMPLETED'
                  ? item.solveTimeMs !== null && item.solveTimeMs >= 1
                  : item.solveTimeMs === null && item.earnedScore === 0) &&
                (!item.hintUsed || item.wrongCount >= CFG.hintWrongThreshold),
            ) &&
            // 미완료 문제는 마지막 순번에만 올 수 있다.
            results.every(
              (item, index) =>
                item.status === 'COMPLETED' || index === results.length - 1,
            )

          onEndRef.current({
            score: engine.score,
            solvedWordCount: solved,
            incompleteWordCount: incomplete,
            wrongCount: wrong,
            hintUsedCount: hintUsed,
            activePlaySec,
            rulesVersion: CFG.rulesVersion,
            problemResults: results,
            valid,
          })
        }
        return
      }

      rafId = requestAnimationFrame(loop)
    }

    rafId = requestAnimationFrame(loop)

    return () => {
      cancelAnimationFrame(rafId)
      // 엔진은 남겨 둔다. 카메라 상태가 흔들릴 때마다 판이 처음으로 돌아가면 안 된다.
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
