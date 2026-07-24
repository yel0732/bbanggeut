import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

import type { PlayHand } from '@/hooks/useHandTracking'

import {
  FRUIT_GAME_CONFIG as CFG,
  fruitDimensions,
  pickRandomFruitType,
} from './fruitGameConfig'

export type GamePhase =
  'waiting-hand' | 'countdown' | 'playing' | 'paused' | 'ended'

export type RenderFruit = {
  id: number
  imageUrl: string
  score: number
  x: number
  y: number
  w: number
  h: number
  rotation: number
  sliced: boolean
  /** 베기 진행도 0..1 (조각 분리/사라짐 애니메이션용) */
  sliceProgress: number
  /** 베인 방향(도) — 조각이 갈라지는 각도 */
  cutAngle: number
}

export type HandScreen = {
  /** 화면(미러링) 좌표 px */
  box: { x: number; y: number; w: number; h: number }
  pointer: { x: number; y: number }
  /** 손날(베는 날) 선분, 화면(미러링) px */
  blade: { ax: number; ay: number; bx: number; by: number }
  /** 손날 끝(중지) 최근 이동 경로 — 스와이프 트레일 */
  trail: { x: number; y: number }[]
  /** 현재 프레임에서 베기 기준 속도 이상인지 */
  fast: boolean
}

export type FruitGameResult = {
  score: number
  slicedFruitCount: number
  totalFruitCount: number
  activePlaySec: number
  ruleVersion: string
}

export type FruitGameState = {
  phase: GamePhase
  countdown: number
  score: number
  remainingSec: number
  fruits: RenderFruit[]
  hand: HandScreen | null
  pause: () => void
  resume: () => void
}

type Fruit = {
  id: number
  imageUrl: string
  score: number
  x: number
  y: number
  vx: number
  vy: number
  w: number
  h: number
  radius: number
  rotation: number
  spin: number
  sliced: boolean
  slicedAt: number
  cutAngle: number
}

type Engine = {
  phase: GamePhase
  fruits: Fruit[]
  nextId: number
  score: number
  slicedCount: number
  totalCount: number
  playElapsedMs: number
  countdownEndsAt: number
  lastSpawnAt: number
  lastFrameTs: number
  prevPointer: { x: number; y: number } | null
  trail: { x: number; y: number }[]
  hand: HandScreen | null
  ended: boolean
}

const { canvasWidth: W, canvasHeight: H } = CFG

function createEngine(now: number): Engine {
  return {
    phase: 'waiting-hand',
    fruits: [],
    nextId: 1,
    score: 0,
    slicedCount: 0,
    totalCount: 0,
    playElapsedMs: 0,
    countdownEndsAt: 0,
    lastSpawnAt: now,
    lastFrameTs: now,
    prevPointer: null,
    trail: [],
    hand: null,
    ended: false,
  }
}

const TRAIL_MAX = 12

/** 점(cx,cy)과 선분(ax,ay)-(bx,by) 사이 최단 거리의 제곱. */
function segmentPointDistSq(
  ax: number,
  ay: number,
  bx: number,
  by: number,
  cx: number,
  cy: number,
): number {
  const dx = bx - ax
  const dy = by - ay
  const lenSq = dx * dx + dy * dy
  let t = lenSq > 0 ? ((cx - ax) * dx + (cy - ay) * dy) / lenSq : 0
  t = Math.max(0, Math.min(1, t))
  const px = ax + t * dx
  const py = ay + t * dy
  const ex = cx - px
  const ey = cy - py
  return ex * ex + ey * ey
}

function spawnFruit(engine: Engine): void {
  const type = pickRandomFruitType()
  const { w, h } = fruitDimensions(type)
  const x = CFG.spawnXMargin + Math.random() * (W - 2 * CFG.spawnXMargin)
  const vy =
    CFG.launchVyMin + Math.random() * (CFG.launchVyMax - CFG.launchVyMin)
  let vx = (Math.random() * 2 - 1) * CFG.launchVxAbsMax
  // 가장자리에서는 화면 안쪽으로 향하게 한다.
  if (x < W * 0.3) vx = Math.abs(vx)
  else if (x > W * 0.7) vx = -Math.abs(vx)

  engine.fruits.push({
    id: engine.nextId++,
    imageUrl: type.imageUrl,
    score: type.score,
    x,
    y: H + 90,
    vx,
    vy,
    w,
    h,
    // 충돌은 관대하게: 가로/세로 평균 반지름 사용.
    radius: (w + h) / 4,
    rotation: Math.random() * 360,
    spin: (Math.random() * 2 - 1) * 90,
    sliced: false,
    slicedAt: 0,
    cutAngle: 0,
  })
  engine.totalCount++
}

/**
 * 과일 자르기 게임 엔진 (MGAME-04/07/09/11/12/13/14/15/16/17/18).
 *
 * 카메라/손 인식이 활성화되면 플레이 손 인식 → 3초 카운트다운 → 과일 생성·베기
 * 판정·2분 타이머를 동시에 시작한다. 손 속도가 기준 이상이면서 손 영역이 과일과
 * 겹칠 때만 성공으로 처리하고, 과일당 한 번만 점수·자른 수에 반영한다.
 */
export function useFruitGame(
  handRef: RefObject<PlayHand | null>,
  active: boolean,
  onEnd: (result: FruitGameResult) => void,
  onSlice?: () => void,
): FruitGameState {
  const engineRef = useRef<Engine | null>(null)
  const onEndRef = useRef(onEnd)
  onEndRef.current = onEnd
  const onSliceRef = useRef(onSlice)
  onSliceRef.current = onSlice

  const [snapshot, setSnapshot] = useState<{
    phase: GamePhase
    countdown: number
    score: number
    remainingSec: number
    fruits: RenderFruit[]
    hand: HandScreen | null
  }>({
    phase: 'waiting-hand',
    countdown: CFG.countdownSec,
    score: 0,
    remainingSec: CFG.durationSec,
    fruits: [],
    hand: null,
  })

  const pause = useCallback(() => {
    const engine = engineRef.current
    if (engine && engine.phase === 'playing') {
      engine.phase = 'paused'
    }
  }, [])

  const resume = useCallback(() => {
    const engine = engineRef.current
    if (engine && engine.phase === 'paused') {
      // 이어하기: 다시 손을 인식하고 3초 카운트다운 후 재개 (MGAME-28).
      engine.phase = 'countdown'
      engine.countdownEndsAt = performance.now() + CFG.countdownSec * 1000
    }
  }, [])

  useEffect(() => {
    if (!active) {
      return
    }

    const engine = createEngine(performance.now())
    engineRef.current = engine
    let rafId = 0

    const readHand = (now: number): void => {
      const play = handRef.current
      if (!play) {
        engine.hand = null
        engine.prevPointer = null
        engine.trail = []
        return
      }
      const sx = (1 - play.pointer.x) * W
      const sy = play.pointer.y * H
      const boxX = (1 - play.box.maxX) * W
      const boxY = play.box.minY * H
      const boxW = (play.box.maxX - play.box.minX) * W
      const boxH = (play.box.maxY - play.box.minY) * H
      // 손날 선분 (미러링)
      const ax = (1 - play.blade.ax) * W
      const ay = play.blade.ay * H
      const bx = (1 - play.blade.bx) * W
      const by = play.blade.by * H
      // 손날 중점 이동 속도로 베기 여부 판단
      const midX = (ax + bx) / 2
      const midY = (ay + by) / 2

      const dtMs = now - engine.lastFrameTs
      let fast = false
      if (engine.prevPointer && dtMs > 0) {
        const dist = Math.hypot(
          midX - engine.prevPointer.x,
          midY - engine.prevPointer.y,
        )
        const speed = (dist / dtMs) * 1000
        fast = speed >= CFG.sliceSpeedThreshold
      }
      engine.prevPointer = { x: midX, y: midY }

      // 손날 끝(중지) 이동 경로 트레일
      engine.trail.push({ x: bx, y: by })
      if (engine.trail.length > TRAIL_MAX) {
        engine.trail.shift()
      }

      engine.hand = {
        box: { x: boxX, y: boxY, w: boxW, h: boxH },
        pointer: { x: sx, y: sy },
        blade: { ax, ay, bx, by },
        trail: [...engine.trail],
        fast,
      }
    }

    const judgeSlices = (now: number): void => {
      const hand = engine.hand
      if (!hand || !hand.fast) {
        return
      }
      for (const fruit of engine.fruits) {
        if (fruit.sliced) continue
        // 손날(선분)이 과일에 스치면 벤 것으로 처리 (MGAME-07: 손날 베기)
        const distSq = segmentPointDistSq(
          hand.blade.ax,
          hand.blade.ay,
          hand.blade.bx,
          hand.blade.by,
          fruit.x,
          fruit.y,
        )
        if (distSq <= fruit.radius * fruit.radius) {
          // 최초 성공 시 한 번만 반영, 이후 판정 대상에서 제외 (MGAME-15/16).
          fruit.sliced = true
          fruit.slicedAt = now
          fruit.cutAngle =
            (Math.atan2(
              hand.blade.by - hand.blade.ay,
              hand.blade.bx - hand.blade.ax,
            ) *
              180) /
            Math.PI
          engine.score += fruit.score
          engine.slicedCount++
          onSliceRef.current?.()
        }
      }
    }

    const step = (dt: number, now: number): void => {
      // 과일 이동 및 화면 밖 제거 (MGAME-14). 베인 과일도 조각이 떨어지도록 계속 이동.
      for (const fruit of engine.fruits) {
        fruit.vy += CFG.gravity * dt
        fruit.x += fruit.vx * dt
        fruit.y += fruit.vy * dt
        fruit.rotation += fruit.spin * dt
      }
      engine.fruits = engine.fruits.filter((fruit) => {
        if (fruit.sliced) {
          return now - fruit.slicedAt < CFG.slicedLingerMs
        }
        return fruit.y < H + 140
      })

      // 생성: 간격 경과 + 최대 표시 수 미만 (MGAME-12/13).
      if (
        now - engine.lastSpawnAt >= CFG.spawnIntervalMs &&
        engine.fruits.length < CFG.maxVisibleFruit
      ) {
        spawnFruit(engine)
        engine.lastSpawnAt = now
      }

      judgeSlices(now)
    }

    const loop = (now: number): void => {
      const rawDt = (now - engine.lastFrameTs) / 1000
      const dt = Math.min(rawDt, 0.05)

      readHand(now)

      switch (engine.phase) {
        case 'waiting-hand':
          if (engine.hand) {
            engine.phase = 'countdown'
            engine.countdownEndsAt = now + CFG.countdownSec * 1000
          }
          break
        case 'countdown':
          if (!engine.hand) {
            // 카운트다운 중 손 인식 끊기면 초기화 (MGAME-26).
            engine.phase = 'waiting-hand'
          } else if (now >= engine.countdownEndsAt) {
            engine.phase = 'playing'
            engine.lastSpawnAt = now
          }
          break
        case 'playing':
          engine.playElapsedMs += dt * 1000
          if (engine.playElapsedMs >= CFG.durationSec * 1000) {
            engine.playElapsedMs = CFG.durationSec * 1000
            engine.phase = 'ended'
          } else {
            step(dt, now)
          }
          break
        case 'paused':
        case 'ended':
          break
      }

      engine.lastFrameTs = now

      const countdownRemaining =
        engine.phase === 'countdown'
          ? Math.max(1, Math.ceil((engine.countdownEndsAt - now) / 1000))
          : CFG.countdownSec
      const remainingSec = Math.max(
        0,
        Math.ceil(CFG.durationSec - engine.playElapsedMs / 1000),
      )

      setSnapshot({
        phase: engine.phase,
        countdown: countdownRemaining,
        score: engine.score,
        remainingSec,
        hand: engine.hand,
        fruits: engine.fruits.map((fruit) => ({
          id: fruit.id,
          imageUrl: fruit.imageUrl,
          score: fruit.score,
          x: fruit.x,
          y: fruit.y,
          w: fruit.w,
          h: fruit.h,
          rotation: fruit.rotation,
          sliced: fruit.sliced,
          sliceProgress: fruit.sliced
            ? Math.min(1, (now - fruit.slicedAt) / CFG.slicedLingerMs)
            : 0,
          cutAngle: fruit.cutAngle,
        })),
      })

      if (engine.phase === 'ended') {
        if (!engine.ended) {
          engine.ended = true
          onEndRef.current({
            score: engine.score,
            slicedFruitCount: engine.slicedCount,
            totalFruitCount: engine.totalCount,
            activePlaySec: Math.round(engine.playElapsedMs / 1000),
            ruleVersion: CFG.ruleVersion,
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
  }, [active, handRef])

  return {
    phase: snapshot.phase,
    countdown: snapshot.countdown,
    score: snapshot.score,
    remainingSec: snapshot.remainingSec,
    fruits: snapshot.fruits,
    hand: snapshot.hand,
    pause,
    resume,
  }
}
