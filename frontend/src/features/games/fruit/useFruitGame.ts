import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

import type { DistanceHint, PlayHand } from '@/hooks/useHandTracking'

import {
  FRUIT_GAME_CONFIG as CFG,
  fruitDimensions,
  pickRandomFruitType,
} from './fruitGameConfig'
import { drawScene, type RenderBlade, type RenderFruit } from './fruitRenderer'

export type GamePhase =
  | 'waiting-hand'
  | 'countdown'
  | 'playing'
  | 'paused'
  | 'interrupted'
  | 'error'
  | 'ended'

export type { DistanceHint }

export type FruitGameResult = {
  score: number
  slicedFruitCount: number
  totalFruitCount: number
  /** 한 번에 이어 벤 최고 콤보 */
  maxCombo: number
  activePlaySec: number
  ruleVersion: string
  /** 결과 정합성 통과 여부 (MGAME-21) */
  valid: boolean
}

export type FruitGameState = {
  phase: GamePhase
  countdown: number
  score: number
  remainingSec: number
  /** 현재 이어 베고 있는 개수. 끊기면 0. */
  combo: number
  /** 현재 콤보에 적용되는 점수 배수 */
  comboMultiplier: number
  /** 피버 타임 진행 중인지 — 과일이 쏟아진다 */
  fever: boolean
  /** 손이 보일 때의 카메라 거리 힌트 */
  distance: DistanceHint | null
  /** 과일·칼을 직접 그리는 캔버스 */
  canvasRef: RefObject<HTMLCanvasElement | null>
  pause: () => void
  resume: () => void
  restart: () => void
}

/** 화면에 발행하는 상태. 값이 바뀐 프레임에만 갱신한다. */
type Snapshot = Omit<
  FruitGameState,
  'canvasRef' | 'pause' | 'resume' | 'restart'
>

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
  /** 콤보 배수까지 반영해 실제로 준 점수 */
  gained: number
}

type Blade = { ax: number; ay: number; bx: number; by: number }

type Engine = {
  phase: GamePhase
  fruits: Fruit[]
  nextId: number
  score: number
  slicedScoreSum: number
  slicedCount: number
  totalCount: number
  combo: number
  maxCombo: number
  comboExpiresAt: number
  /** 이 시각까지 피버 타임 */
  feverUntil: number
  playElapsedMs: number
  countdownEndsAt: number
  lastSpawnAt: number
  lastFrameTs: number
  lastHandSeenAt: number
  /** 화면 좌표로 옮겨 평활한 현재/직전 칼날 */
  blade: Blade | null
  prevBlade: Blade | null
  /** 칼끝 이동 기록 — 속도 계산과 트레일에 함께 쓴다 */
  tipHistory: { t: number; x: number; y: number }[]
  /** 이 시각까지는 베기 가능 상태로 본다 */
  fastUntil: number
  distance: DistanceHint | null
  ended: boolean
}

const { canvasWidth: W, canvasHeight: H } = CFG

function createEngine(now: number): Engine {
  return {
    phase: 'waiting-hand',
    fruits: [],
    nextId: 1,
    score: 0,
    slicedScoreSum: 0,
    slicedCount: 0,
    totalCount: 0,
    combo: 0,
    maxCombo: 0,
    comboExpiresAt: 0,
    feverUntil: 0,
    playElapsedMs: 0,
    countdownEndsAt: 0,
    lastSpawnAt: now,
    lastFrameTs: now,
    lastHandSeenAt: now,
    blade: null,
    prevBlade: null,
    tipHistory: [],
    fastUntil: 0,
    distance: null,
    ended: false,
  }
}

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

/** 칼날 중 실제로 베는 바깥쪽 구간만 잘라 낸다. */
function cuttingEdge(blade: Blade): Blade {
  const s = CFG.bladeEdgeStart
  return {
    ax: blade.ax + (blade.bx - blade.ax) * s,
    ay: blade.ay + (blade.by - blade.ay) * s,
    bx: blade.bx,
    by: blade.by,
  }
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
    gained: 0,
  })
  engine.totalCount++
}

/** 지금 콤보에 적용할 점수 배수. 피버 중에는 최소 배수가 보장된다. */
function comboMultiplier(combo: number, fever: boolean): number {
  const byCombo =
    combo <= 1
      ? 1
      : Math.min(CFG.comboMaxMultiplier, 1 + (combo - 1) * CFG.comboStep)
  return fever ? Math.max(byCombo, CFG.feverMinMultiplier) : byCombo
}

/**
 * 과일 자르기 게임 엔진 (MGAME-04/07/09/11/12/13/14/15/16/17/18).
 *
 * 카메라/손 인식이 활성화되면 플레이 손 인식 → 3초 카운트다운 → 과일 생성·베기
 * 판정·플레이 타이머를 동시에 시작한다. 손 속도가 기준 이상이면서 칼날이 과일과
 * 겹칠 때만 성공으로 처리하고, 과일당 한 번만 점수·자른 수에 반영한다.
 *
 * 인식은 초당 30번 남짓 들어오고 화면은 60fps로 돈다. 새 좌표가 없는 프레임에
 * 손이 멈춘 것으로 보면 베기가 절반쯤 사라지므로, 칼 위치는 시상수로 목표를
 * 따라가게 하고 속도는 짧은 시간 구간으로 잰다. 판정도 한 점이 아니라 직전
 * 프레임 칼날과 이번 프레임 칼날 사이를 훑어 빠르게 휘둘러도 놓치지 않는다.
 */
export function useFruitGame(
  handRef: RefObject<PlayHand | null>,
  active: boolean,
  onEnd: (result: FruitGameResult) => void,
  onSlice?: (combo: number) => void,
  redesignateHand?: () => void,
  cameraError?: boolean,
): FruitGameState {
  const engineRef = useRef<Engine | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)

  const onEndRef = useRef(onEnd)
  onEndRef.current = onEnd
  const onSliceRef = useRef(onSlice)
  onSliceRef.current = onSlice
  const redesignateRef = useRef(redesignateHand)
  redesignateRef.current = redesignateHand
  const cameraErrorRef = useRef(cameraError)
  cameraErrorRef.current = cameraError

  const [snapshot, setSnapshot] = useState<Snapshot>({
    phase: 'waiting-hand',
    countdown: CFG.countdownSec,
    score: 0,
    remainingSec: CFG.durationSec,
    combo: 0,
    comboMultiplier: 1,
    fever: false,
    distance: null,
  })

  const pause = useCallback(() => {
    const engine = engineRef.current
    // 진행 중이거나 손 사라짐 자동정지 상태에서 수동 일시정지 메뉴를 연다.
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
      // 이어하기: 플레이 손을 다시 지정하고 3초 카운트다운 후 재개 (MGAME-28).
      redesignateRef.current?.()
      engine.combo = 0
      engine.feverUntil = 0
      engine.phase = 'countdown'
      engine.countdownEndsAt = performance.now() + CFG.countdownSec * 1000
    }
  }, [])

  const restart = useCallback(() => {
    const engine = engineRef.current
    if (!engine) return
    // 다시 시작: 임시 점수·결과 폐기, 새 구성으로 처음부터 (MGAME-29).
    redesignateRef.current?.()
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

    /**
     * 최신 인식 결과를 화면 좌표로 옮겨 평활하고, 칼끝 속도를 갱신한다.
     *
     * 인식이 잠깐 끊겨도 handHoldMs 동안은 직전 칼을 유지해 깜빡이지 않게 한다.
     */
    const readHand = (engine: Engine, now: number, dtMs: number): void => {
      const play = handRef.current

      if (play) {
        engine.lastHandSeenAt = now
        engine.distance = play.distance
      } else if (now - engine.lastHandSeenAt > CFG.handHoldMs) {
        engine.blade = null
        engine.prevBlade = null
        engine.tipHistory = []
        engine.distance = null
        return
      }

      // 카메라 미러링: 아이가 든 손이 화면에서도 같은 쪽에 보이도록 x를 뒤집는다.
      // 칼 길이는 화면 px로 고정한다. 정규화 좌표에서 길이를 주면 세로로 뻗은
      // 칼이 가로보다 짧아지고(화면이 16:9라서), 카메라에 가까울수록 길어진다.
      let target: Blade | null = null
      if (play) {
        const wristX = (1 - play.wrist.x) * W
        const wristY = play.wrist.y * H
        const dirX = wristX - (1 - play.elbow.x) * W
        const dirY = wristY - play.elbow.y * H
        const dirLen = Math.hypot(dirX, dirY) || 1
        target = {
          ax: wristX,
          ay: wristY,
          bx: wristX + (dirX / dirLen) * CFG.bladeLengthPx,
          by: wristY + (dirY / dirLen) * CFG.bladeLengthPx,
        }
      }

      if (!engine.blade) {
        if (!target) {
          return
        }
        engine.blade = { ...target }
        engine.prevBlade = { ...target }
      } else {
        engine.prevBlade = { ...engine.blade }
        if (target) {
          // 프레임 간격이 들쭉날쭉해도 같은 속도로 따라가도록 시상수를 쓴다.
          const k = 1 - Math.exp(-dtMs / CFG.handSmoothTauMs)
          engine.blade.ax += (target.ax - engine.blade.ax) * k
          engine.blade.ay += (target.ay - engine.blade.ay) * k
          engine.blade.bx += (target.bx - engine.blade.bx) * k
          engine.blade.by += (target.by - engine.blade.by) * k
        }
      }

      const tip = engine.blade
      engine.tipHistory.push({ t: now, x: tip.bx, y: tip.by })
      while (
        engine.tipHistory.length > 2 &&
        engine.tipHistory[0].t < now - Math.max(CFG.speedWindowMs, CFG.trailMs)
      ) {
        engine.tipHistory.shift()
      }

      // 한 프레임 차이는 값이 심하게 튄다. 일정 시간 구간의 평균 속도로 본다.
      const cutoff = now - CFG.speedWindowMs
      let base = engine.tipHistory[0]
      for (const point of engine.tipHistory) {
        if (point.t <= cutoff) {
          base = point
        } else {
          break
        }
      }
      const elapsed = now - base.t
      if (elapsed > 0) {
        const speed =
          (Math.hypot(tip.bx - base.x, tip.by - base.y) / elapsed) * 1000
        if (speed >= CFG.sliceSpeedThreshold) {
          // 인식 프레임이 비어 속도가 순간 0이 되어도 베기가 끊기지 않게 유지한다.
          engine.fastUntil = now + CFG.sliceSpeedHoldMs
        }
      }
    }

    const judgeSlices = (engine: Engine, now: number): void => {
      if (!engine.blade || now >= engine.fastUntil) {
        return
      }

      const to = cuttingEdge(engine.blade)
      const from = cuttingEdge(engine.prevBlade ?? engine.blade)
      // 프레임 사이에 칼이 지나간 자리를 훑는다. 빠른 스윙에서 과일을 건너뛰지 않는다.
      const travel = Math.hypot(to.bx - from.bx, to.by - from.by)
      const steps = Math.min(8, Math.max(1, Math.ceil(travel / 28)))

      for (const fruit of engine.fruits) {
        if (fruit.sliced) continue

        const hitRadius =
          fruit.radius * CFG.hitRadiusScale + CFG.bladeThicknessPx
        let hit = false
        for (let i = 0; i <= steps && !hit; i++) {
          const t = i / steps
          const ax = from.ax + (to.ax - from.ax) * t
          const ay = from.ay + (to.ay - from.ay) * t
          const bx = from.bx + (to.bx - from.bx) * t
          const by = from.by + (to.by - from.by) * t
          hit =
            segmentPointDistSq(ax, ay, bx, by, fruit.x, fruit.y) <=
            hitRadius * hitRadius
        }
        if (!hit) continue

        // 최초 성공 시 한 번만 반영, 이후 판정 대상에서 제외 (MGAME-15/16).
        // 앞선 베기와 짧은 간격이면 콤보가 이어져 배수가 올라간다.
        if (now > engine.comboExpiresAt) {
          engine.combo = 0
        }
        engine.combo++
        engine.comboExpiresAt = now + CFG.comboWindowMs
        engine.maxCombo = Math.max(engine.maxCombo, engine.combo)

        // 콤보가 기준에 닿으면 피버가 열리고, 이어 갈 때마다 시간이 다시 찬다.
        if (engine.combo >= CFG.feverCombo) {
          engine.feverUntil = now + CFG.feverDurationMs
        }

        const gained = Math.round(
          fruit.score * comboMultiplier(engine.combo, now < engine.feverUntil),
        )
        fruit.sliced = true
        fruit.slicedAt = now
        fruit.gained = gained
        fruit.cutAngle =
          (Math.atan2(to.by - to.ay, to.bx - to.ax) * 180) / Math.PI
        engine.score += gained
        engine.slicedScoreSum += gained
        engine.slicedCount++
        onSliceRef.current?.(engine.combo)
      }
    }

    const step = (engine: Engine, dt: number, now: number): void => {
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
      // 피버 중에는 간격을 줄이고 동시 표시 수를 늘려 과일이 쏟아지게 한다.
      const fever = now < engine.feverUntil
      const spawnIntervalMs = fever
        ? CFG.feverSpawnIntervalMs
        : CFG.spawnIntervalMs
      const maxVisible = fever ? CFG.feverMaxVisibleFruit : CFG.maxVisibleFruit
      // 벤 과일 조각은 곧 사라지므로 아직 안 벤 것만 센다.
      const aliveCount = engine.fruits.reduce(
        (count, fruit) => (fruit.sliced ? count : count + 1),
        0,
      )
      if (
        now - engine.lastSpawnAt >= spawnIntervalMs &&
        aliveCount < maxVisible
      ) {
        spawnFruit(engine)
        engine.lastSpawnAt = now
      }

      judgeSlices(engine, now)

      if (engine.combo > 0 && now > engine.comboExpiresAt) {
        engine.combo = 0
      }
    }

    const paint = (engine: Engine, now: number): void => {
      const ctx = canvasRef.current?.getContext('2d')
      if (!ctx) {
        return
      }

      const fruits: RenderFruit[] = engine.fruits.map((fruit) => ({
        imageUrl: fruit.imageUrl,
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
        gained: fruit.gained,
      }))

      let blade: RenderBlade | null = null
      if (engine.blade) {
        const trailFrom = now - CFG.trailMs
        blade = {
          ...engine.blade,
          fast: now < engine.fastUntil,
          trail: engine.tipHistory
            .filter((point) => point.t >= trailFrom)
            .map((point) => ({ x: point.x, y: point.y })),
        }
      }

      drawScene(ctx, fruits, blade)
    }

    const loop = (now: number): void => {
      // restart()가 엔진을 교체할 수 있어 매 프레임 현재 엔진을 읽는다.
      const engine = engineRef.current
      if (!engine) return

      const rawDtMs = now - engine.lastFrameTs
      const dtMs = Math.min(rawDtMs, 50)
      const dt = dtMs / 1000

      readHand(engine, now, Math.max(dtMs, 1))
      const handVisible = engine.blade !== null

      // 카메라 오류: 게임 상태는 유지한 채 정지 (MGAME-30). 복구되면 손 재인식
      // 후 카운트다운으로 재개 (MGAME-31). 수동 일시정지/종료 상태는 건드리지 않음.
      if (cameraErrorRef.current) {
        if (
          engine.phase !== 'error' &&
          engine.phase !== 'paused' &&
          engine.phase !== 'ended'
        ) {
          engine.phase = 'error'
          redesignateRef.current?.()
        }
      } else if (engine.phase === 'error') {
        engine.phase = 'waiting-hand'
      }

      switch (engine.phase) {
        case 'waiting-hand':
          if (handVisible) {
            engine.phase = 'countdown'
            engine.countdownEndsAt = now + CFG.countdownSec * 1000
          }
          break
        case 'countdown':
          if (!handVisible) {
            // 카운트다운 중 손 인식 끊기면 초기화하고 손 대기 안내로 돌아간다
            // (MGAME-26). 손이 다시 보이면 카운트다운을 처음부터 진행한다.
            engine.phase = 'waiting-hand'
          } else if (now >= engine.countdownEndsAt) {
            engine.phase = 'playing'
            engine.lastSpawnAt = now
          }
          break
        case 'playing':
          if (
            !handVisible &&
            now - engine.lastHandSeenAt > CFG.handLostGraceMs
          ) {
            // 플레이 손이 사라지면 자동 일시정지 (MGAME-24). 복귀 시 재지정.
            engine.phase = 'interrupted'
            engine.combo = 0
            engine.feverUntil = 0
            redesignateRef.current?.()
            break
          }
          engine.playElapsedMs += dtMs
          if (engine.playElapsedMs >= CFG.durationSec * 1000) {
            engine.playElapsedMs = CFG.durationSec * 1000
            engine.phase = 'ended'
          } else {
            step(engine, dt, now)
          }
          break
        case 'interrupted':
          // 손이 다시 인식되면 3초 카운트다운 후 재개 (MGAME-25/26).
          if (handVisible) {
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
      paint(engine, now)

      const countdown =
        engine.phase === 'countdown'
          ? Math.max(1, Math.ceil((engine.countdownEndsAt - now) / 1000))
          : CFG.countdownSec
      const remainingSec = Math.max(
        0,
        Math.ceil(CFG.durationSec - engine.playElapsedMs / 1000),
      )

      const fever = engine.phase === 'playing' && now < engine.feverUntil

      // 프레임마다 setState하지 않도록 실제로 바뀐 값이 있을 때만 발행한다.
      // 과일과 칼은 캔버스에 직접 그리므로 여기서 다루지 않는다.
      const key = [
        engine.phase,
        countdown,
        remainingSec,
        engine.score,
        engine.combo,
        fever,
        engine.distance,
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          phase: engine.phase,
          countdown,
          score: engine.score,
          remainingSec,
          combo: engine.combo,
          comboMultiplier: comboMultiplier(engine.combo, fever),
          fever,
          distance: engine.distance,
        })
      }

      if (engine.phase === 'ended') {
        if (!engine.ended) {
          engine.ended = true
          // 정합성 검증 (MGAME-21): 자른 수 ≤ 등장 수, 점수 = 과일별 점수 합
          const valid =
            engine.slicedCount <= engine.totalCount &&
            engine.score === engine.slicedScoreSum
          onEndRef.current({
            score: engine.score,
            slicedFruitCount: engine.slicedCount,
            totalFruitCount: engine.totalCount,
            maxCombo: engine.maxCombo,
            activePlaySec: Math.round(engine.playElapsedMs / 1000),
            ruleVersion: CFG.ruleVersion,
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
      engineRef.current = null
    }
  }, [active, handRef])

  return {
    ...snapshot,
    canvasRef,
    pause,
    resume,
    restart,
  }
}
