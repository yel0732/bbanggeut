import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { RefObject } from 'react'

import type { HandGesturesFrame, HandSlot } from '@/hooks/useHandGestures'

import {
  RECYCLE_GAME_CONFIG as CFG,
  DESPAWN_X,
  SPAWN_X,
} from './recycleGameConfig'
import {
  binAt,
  idleAim,
  riverY,
  screenFromHand,
  stepAim,
  type FloatingItem,
  type HandAim,
} from './recycleGrab'
import {
  guideMessageFor,
  pickNextItem,
  type RecycleCategory,
  type RecycleItem,
} from './recycleItems'

export type RecyclePhase =
  /** 손이 잡히기를 기다린다 */
  | 'waiting-hands'
  /** 3초 카운트다운 */
  | 'countdown'
  /** 진행 중 */
  | 'playing'
  /** 오답 안내 중. 생성·이동·입력은 멈추고 타이머만 흐른다(RGAME-09). */
  | 'guide'
  /** 아이가 일시정지했다 */
  | 'paused'
  /** 손이 사라져 자동 중단됐다 */
  | 'interrupted'
  /** 카메라·인식 오류 */
  | 'error'
  | 'ended'

/** 정답 연출 — 잠깐 칭찬을 띄운다. */
export type RecycleCorrectFeedback = {
  itemUid: number
  itemName: string
  bin: RecycleCategory
}

/** 오답 안내 — 빵긋이가 올바른 통을 알려 준다. */
export type RecycleGuide = {
  itemName: string
  itemImageUrl: string
  message: string
}

export type RecycleGameResult = {
  score: number
  successCount: number
  wrongCount: number
  /** 놓쳐서 물길 끝으로 흘러간 개수. 아이 화면에는 쓰지 않는다. */
  missedCount: number
  activePlaySec: number
  rulesVersion: string
  /** 결과 정합성 통과 여부 */
  valid: boolean
}

export type RecycleGameState = {
  phase: RecyclePhase
  countdown: number
  remainingSec: number
  score: number
  successCount: number
  /** 화면에 그릴 쓰레기 목록 */
  items: FloatingItem[]
  correct: RecycleCorrectFeedback | null
  guide: RecycleGuide | null
  /** 손별 조준 중인 쓰레기 uid — 테두리 표시에 쓴다 */
  hover: Record<HandSlot, number | null>
  /** 손별 올라가 있는 분리수거 통 — 어느 통이 잡혀 있는지 보여 준다 */
  binHover: Record<HandSlot, RecycleCategory | null>
  /** 손 포인터 DOM 노드 — 좌표/각도/주먹 상태를 리렌더 없이 여기에 직접 쓴다. */
  pointerRefs: Record<HandSlot, RefObject<HTMLDivElement | null>>
  pause: () => void
  resume: () => void
  restart: () => void
}

type Snapshot = Omit<
  RecycleGameState,
  'pointerRefs' | 'pause' | 'resume' | 'restart'
>

type Engine = {
  phase: RecyclePhase
  items: FloatingItem[]
  nextUid: number
  /** 최근 출제한 쓰레기 코드. 연속 출현을 막는 데 쓴다. */
  recentCodes: string[]
  /** 직전 쓰레기가 놓인 높이 갈래. 연속으로 같은 갈래에 띄우지 않는다. */
  lastLane: number
  score: number
  successCount: number
  wrongCount: number
  missedCount: number
  /** 타이머가 흐른 시간(ms). 일시정지·손 놓침 시간은 포함하지 않는다. */
  playElapsedMs: number
  msUntilNextSpawn: number
  countdownEndsAt: number
  lastFrameTs: number
  lastHandSeenAt: number
  aim: Record<HandSlot, HandAim>
  /** 손별 올라가 있는 통. 매 프레임 손 위치에서 다시 구한다. */
  binHover: Record<HandSlot, RecycleCategory | null>
  correct: RecycleCorrectFeedback | null
  correctUntil: number
  guide: RecycleGuide | null
  guideUntil: number
  ended: boolean
}

const HAND_SLOTS: HandSlot[] = ['a', 'b']

function emptyAim(): Record<HandSlot, HandAim> {
  return { a: idleAim(), b: idleAim() }
}

function createEngine(now: number): Engine {
  return {
    phase: 'waiting-hands',
    items: [],
    nextUid: 1,
    recentCodes: [],
    lastLane: -1,
    score: 0,
    successCount: 0,
    wrongCount: 0,
    missedCount: 0,
    playElapsedMs: 0,
    msUntilNextSpawn: CFG.firstSpawnDelayMs,
    countdownEndsAt: 0,
    lastFrameTs: now,
    lastHandSeenAt: now,
    aim: emptyAim(),
    binHover: { a: null, b: null },
    correct: null,
    correctUntil: 0,
    guide: null,
    guideUntil: 0,
    ended: false,
  }
}

/**
 * 다음 쓰레기를 물길에 띄운다.
 *
 * 높이 갈래와 속도를 쓰레기마다 다르게 준다. 다 같으면 한 줄로 늘어서 흐르고,
 * 아이 눈에는 잡을 것이 하나로만 보인다. 직전과 같은 갈래는 피해서 바로 뒤에
 * 겹쳐 나오지 않게 한다.
 */
function spawnItem(engine: Engine, now: number): void {
  const item: RecycleItem = pickNextItem(engine.recentCodes)
  engine.recentCodes = [...engine.recentCodes.slice(-3), item.code]

  const lanes = CFG.river.lanesY
  let lane = Math.floor(Math.random() * lanes.length)
  if (lane === engine.lastLane) {
    lane =
      (lane + 1 + Math.floor(Math.random() * (lanes.length - 1))) % lanes.length
  }
  engine.lastLane = lane

  const jitter = (Math.random() * 2 - 1) * CFG.river.laneJitterPx
  const laneOffsetY = lanes[lane] + jitter
  const speedPx =
    CFG.itemSpeedPx * (1 + (Math.random() * 2 - 1) * CFG.speedJitterRatio)

  engine.items.push({
    uid: engine.nextUid++,
    code: item.code,
    name: item.name,
    category: item.category,
    imageUrl: item.imageUrl,
    x: SPAWN_X,
    y: CFG.river.y + laneOffsetY,
    riverX: SPAWN_X,
    speedPx,
    laneOffsetY,
    // 위상을 흩어 놓아야 쓰레기들이 한 몸처럼 출렁이지 않는다.
    bobPhaseMs: (now * 0.37 + engine.nextUid * 613) % CFG.river.bobPeriodMs,
    heldBy: null,
    judging: false,
  })
}

/**
 * 들고 있던 쓰레기를 물길로 돌려보낸다 (RGAME-06, RGAME-15, RGAME-16).
 *
 * 오답으로 세지 않는다. 일시정지·손 놓침·통이 아닌 자리에 놓기가 모두 같은
 * 처리다 — 아이가 손을 내린 것을 틀렸다고 하면 안 된다.
 */
function returnToRiver(engine: Engine, uid: number): void {
  const item = engine.items.find((candidate) => candidate.uid === uid)
  if (!item) return
  item.heldBy = null
  item.x = item.riverX
}

function releaseAllHeld(engine: Engine): void {
  for (const slot of HAND_SLOTS) {
    const heldUid = engine.aim[slot].heldUid
    if (heldUid !== null) {
      returnToRiver(engine, heldUid)
    }
  }
  engine.aim = emptyAim()
}

/**
 * 분리수거 게임 엔진 (RGAME).
 *
 * 손 인식 → 3초 카운트다운 → 1분 진행 → 결과 흐름을 담당한다. 물길을 가로지르는
 * 쓰레기 위에 손바닥을 올려 링이 다 차면 주먹을 쥐어 잡고, 통 위에서 손을 펴면
 * 분리수거한다.
 *
 * 시간 처리가 이 게임의 까다로운 지점이다. 오답 안내 중에는 쓰레기와 입력을
 * 멈추지만 타이머는 계속 흐른다(RGAME-09). 반면 일시정지·손 놓침·카메라 오류로
 * 멈춘 시간은 실제 플레이 시간에 넣지 않는다(RGAME-15~17). 그래서 `playing`과
 * `guide`에서만 `playElapsedMs`를 늘린다.
 *
 * 렌더 비용을 줄이기 위해 손 좌표·각도·주먹 상태는 React state로 올리지 않고
 * `pointerRefs`가 가리키는 DOM 노드에 직접 쓴다. 쓰레기 위치는 매 프레임 바뀌어
 * 스냅샷으로 발행하되, 좌표를 반올림해 같은 픽셀이면 다시 그리지 않는다.
 */
export function useRecycleGame(
  gesturesRef: RefObject<HandGesturesFrame>,
  active: boolean,
  onEnd: (result: RecycleGameResult) => void,
  handlers: {
    onGrab?: () => void
    onCorrect?: () => void
    onWrong?: () => void
    onReturn?: () => void
  } = {},
  cameraError?: boolean,
): RecycleGameState {
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
    successCount: 0,
    items: [],
    correct: null,
    guide: null,
    hover: { a: null, b: null },
    binHover: { a: null, b: null },
  }))

  const pause = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    if (
      engine.phase === 'playing' ||
      engine.phase === 'guide' ||
      engine.phase === 'interrupted'
    ) {
      // 잡고 있던 쓰레기를 물길로 돌려보낸다(RGAME-15).
      releaseAllHeld(engine)
      engine.correct = null
      engine.guide = null
      engine.phase = 'paused'
    }
  }, [])

  const resume = useCallback(() => {
    const engine = engineRef.current
    if (engine && engine.phase === 'paused') {
      // 손을 편 상태를 확인한 뒤 카운트다운부터. 남은 시간은 그대로 이어간다.
      engine.phase = 'waiting-hands'
    }
  }, [])

  const restart = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    // 임시 기록을 모두 버리고 새 1분 게임을 시작한다(RGAME-15).
    const fresh = createEngine(performance.now())
    fresh.lastFrameTs = engine.lastFrameTs
    engineRef.current = fresh
  }, [])

  useEffect(() => {
    if (!active) {
      return
    }

    /*
      엔진은 한 판에 하나만 만든다.

      이 effect는 카메라 상태(`active`)가 흔들리면 다시 돈다 — 카메라 오류 뒤
      다시 시도를 누르면 granted → requesting → granted로 지나가고, 개발 모드의
      StrictMode도 effect를 두 번 부른다. 그때마다 엔진을 새로 만들면 점수와
      남은 시간이 조용히 0으로 돌아간다. 판을 처음부터 다시 하는 것은 오직
      `restart()`뿐이다.
    */
    engineRef.current ??= createEngine(performance.now())
    // 멈춰 있던 사이 프레임 간격이 크게 벌어졌을 수 있다. 그 간격이 플레이
    // 시간으로 들어가지 않게 기준 시각을 지금으로 맞춘다.
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

    /** 통에 놓았을 때의 분리수거 판정 (RGAME-07~09). */
    const judge = (
      engine: Engine,
      item: FloatingItem,
      bin: RecycleCategory,
      now: number,
    ): void => {
      item.judging = true
      item.heldBy = null

      if (item.category === bin) {
        engine.score += CFG.scorePerCorrect
        engine.successCount++
        engine.correct = {
          itemUid: item.uid,
          itemName: item.name,
          bin,
        }
        engine.correctUntil = now + CFG.correctFeedbackMs
        // 정답은 바로 치운다. 연출은 칭찬 문구로만 남긴다.
        engine.items = engine.items.filter(
          (candidate) => candidate.uid !== item.uid,
        )
        handlersRef.current.onCorrect?.()
        return
      }

      // 오답: 점수는 깎지 않고 안내만 한다. 틀린 쓰레기는 물길로 돌리지 않는다.
      engine.wrongCount++
      engine.items = engine.items.filter(
        (candidate) => candidate.uid !== item.uid,
      )
      engine.guide = {
        itemName: item.name,
        itemImageUrl: item.imageUrl,
        message: guideMessageFor({
          code: item.code,
          name: item.name,
          category: item.category,
          imageUrl: item.imageUrl,
        }),
      }
      engine.guideUntil = now + CFG.wrongGuideMs
      engine.phase = 'guide'
      releaseAllHeld(engine)
      handlersRef.current.onWrong?.()
    }

    const readHands = (engine: Engine, now: number): boolean => {
      const frame = gesturesRef.current
      let anyVisible = false
      const inputOpen = engine.phase === 'playing'

      for (const slot of HAND_SLOTS) {
        const hand = frame[slot]

        if (!hand) {
          hidePointer(slot)
          engine.binHover[slot] = null
          continue
        }

        anyVisible = true

        const { x, y } = screenFromHand(hand)
        paintPointer(slot, x, y, hand.angle, hand.fist)

        if (!inputOpen) {
          paintAim(slot, 0)
          engine.binHover[slot] = null
          continue
        }

        // 어느 통 위에 있는지. 놓기 판정과 같은 영역을 써서, 밝아진 통에
        // 놓으면 반드시 그 통으로 들어간다.
        engine.binHover[slot] = binAt(x, y)

        const stepped = stepAim(
          engine.aim[slot],
          { x, y, fist: hand.fist },
          engine.items,
          now,
        )
        paintAim(slot, stepped.progress)

        if (stepped.grabbed) {
          stepped.grabbed.heldBy = slot
          handlersRef.current.onGrab?.()
        }

        if (stepped.released) {
          const { item, bin } = stepped.released
          if (bin) {
            judge(engine, item, bin, now)
          } else {
            returnToRiver(engine, item.uid)
            handlersRef.current.onReturn?.()
          }
        }

        engine.aim[slot] = stepped.aim

        // 들고 있는 쓰레기는 손을 따라온다.
        const heldUid = stepped.aim.heldUid
        if (heldUid !== null) {
          const held = engine.items.find(
            (candidate) => candidate.uid === heldUid,
          )
          if (held) {
            held.x = x
            held.y = y
          }
        }
      }

      if (anyVisible) {
        engine.lastHandSeenAt = now
      }
      return anyVisible
    }

    /** 손을 편 상태인지. 이어하기·재개 조건이다(RGAME-15, RGAME-16). */
    const anyOpenHand = (): boolean => {
      const frame = gesturesRef.current
      return HAND_SLOTS.some((slot) => frame[slot] && !frame[slot]!.fist)
    }

    const moveItems = (engine: Engine, dt: number, now: number): void => {
      for (const item of engine.items) {
        if (item.heldBy !== null || item.judging) continue
        item.riverX += item.speedPx * dt
        item.x = item.riverX
        item.y = riverY(item, now)
      }
      // 물길 끝까지 간 쓰레기는 제거한다(RGAME-10).
      const before = engine.items.length
      engine.items = engine.items.filter((item) => item.riverX <= DESPAWN_X)
      engine.missedCount += before - engine.items.length
    }

    const loop = (now: number): void => {
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
          }
          break
        case 'playing':
          if (!anyHand && now - engine.lastHandSeenAt > CFG.handLostGraceMs) {
            // 손이 오래 사라지면 자동 중단. 이 시간은 플레이 시간에서 빠진다.
            releaseAllHeld(engine)
            engine.phase = 'interrupted'
            break
          }

          engine.playElapsedMs += dt * 1000
          engine.msUntilNextSpawn -= dt * 1000
          if (engine.msUntilNextSpawn <= 0) {
            spawnItem(engine, now)
            engine.msUntilNextSpawn += CFG.spawnIntervalMs
          }
          moveItems(engine, dt, now)

          if (engine.correct && now >= engine.correctUntil) {
            engine.correct = null
          }
          break
        case 'guide':
          // 안내 중에도 타이머는 흐른다(RGAME-09). 생성·이동·입력은 멈춘다.
          engine.playElapsedMs += dt * 1000
          if (now >= engine.guideUntil) {
            engine.guide = null
            // 안내 중 시간이 끝났으면 재개하지 않고 바로 종료한다(RGAME-10).
            engine.phase =
              engine.playElapsedMs >= CFG.durationSec * 1000
                ? 'ended'
                : 'playing'
          }
          break
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

      if (
        engine.phase === 'playing' &&
        engine.playElapsedMs >= CFG.durationSec * 1000
      ) {
        engine.playElapsedMs = CFG.durationSec * 1000
        // 남아 있는 쓰레기를 모두 치우고 끝낸다(RGAME-10).
        releaseAllHeld(engine)
        engine.items = []
        engine.correct = null
        engine.phase = 'ended'
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

      // 쓰레기 좌표는 매 프레임 바뀐다. 반올림한 값으로 키를 만들어 같은
      // 픽셀이면 다시 그리지 않는다.
      const key = [
        engine.phase,
        countdown,
        remainingSec,
        engine.score,
        engine.successCount,
        engine.correct?.itemUid,
        engine.guide?.message,
        engine.aim.a.hoverUid,
        engine.aim.b.hoverUid,
        engine.binHover.a,
        engine.binHover.b,
        engine.items
          .map(
            (item) =>
              `${item.uid}:${Math.round(item.x)}:${Math.round(item.y)}:${item.heldBy ?? ''}`,
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
          successCount: engine.successCount,
          items: engine.items.map((item) => ({ ...item })),
          correct: engine.correct,
          guide: engine.guide,
          hover: { a: engine.aim.a.hoverUid, b: engine.aim.b.hoverUid },
          binHover: { ...engine.binHover },
        })
      }

      if (engine.phase === 'ended') {
        if (!engine.ended) {
          engine.ended = true
          const activePlaySec = Math.round(engine.playElapsedMs / 1000)
          // 정합성 검증: 점수 = 성공 개수 × 정답 점수, 시간은 0~제한시간.
          const valid =
            engine.score === engine.successCount * CFG.scorePerCorrect &&
            engine.successCount >= 0 &&
            engine.wrongCount >= 0 &&
            activePlaySec >= 0 &&
            activePlaySec <= CFG.durationSec
          onEndRef.current({
            score: engine.score,
            successCount: engine.successCount,
            wrongCount: engine.wrongCount,
            missedCount: engine.missedCount,
            activePlaySec,
            rulesVersion: CFG.rulesVersion,
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
      // 엔진은 남겨 둔다. 여기서 비우면 카메라 상태가 흔들릴 때마다 판이
      // 처음으로 돌아간다.
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
