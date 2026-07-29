import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

import type { DistanceHint, PlayHand } from '@/hooks/useHandTracking'

import {
  FRUIT_GAME_CONFIG as CFG,
  FRUIT_TYPES,
  fruitDimensions,
} from './fruitGameConfig'
import { drawScene, type RenderBlade, type RenderFruit } from './fruitRenderer'
import {
  bladeTrail,
  createBladeTracker,
  cutAngleDeg,
  hitRadiusFor,
  isSlicing,
  sweptHit,
  updateBladeTracker,
  type BladeTracker,
} from './fruitSlicing'

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
  fruitResults: {
    fruitCode: string
    generatedCount: number
    slicedCount: number
  }[]
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
  /** 피버 타임 진행 중인지 — 과일이 쏟아진다 */
  fever: boolean
  /** 폭탄에 맞아 아무것도 못 자르는 중인지 */
  stunned: boolean
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
  fruitCode: string
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
  /** 폭탄이면 점수 대신 스턴을 준다 */
  bomb: boolean
}

export type FruitGameRuntimeRule = {
  rulesVersion: string
  plannedDurationSeconds: number
  sliceSpeedThreshold: number
  fruitSpawnIntervalMs: number
  maxVisibleFruit: number
  fruits: { fruitCode: string; sizePx: number; score: number }[]
}

type Engine = {
  phase: GamePhase
  fruits: Fruit[]
  nextId: number
  score: number
  slicedScoreSum: number
  slicedCount: number
  totalCount: number
  generatedByFruit: Record<string, number>
  slicedByFruit: Record<string, number>
  combo: number
  maxCombo: number
  comboExpiresAt: number
  /** 이 시각까지 피버 타임 */
  feverUntil: number
  /** 폭탄에 맞아 이 시각까지 아무것도 못 자른다 */
  stunnedUntil: number
  /** 폭발 연출 시작 시각. 0이면 연출 없음 */
  blastAt: number
  /** 폭발이 일어난 자리 (화면 px) */
  blastPos: { x: number; y: number } | null
  playElapsedMs: number
  countdownEndsAt: number
  lastSpawnAt: number
  lastFrameTs: number
  /** 칼날 추적·베기 판정 상태. 튜토리얼 실습과 같은 모듈을 쓴다. */
  tracker: BladeTracker
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
    generatedByFruit: {},
    slicedByFruit: {},
    combo: 0,
    maxCombo: 0,
    comboExpiresAt: 0,
    feverUntil: 0,
    stunnedUntil: 0,
    blastAt: 0,
    blastPos: null,
    playElapsedMs: 0,
    countdownEndsAt: 0,
    lastSpawnAt: now,
    lastFrameTs: now,
    tracker: createBladeTracker(now),
    ended: false,
  }
}

function spawnBomb(engine: Engine, now: number): void {
  const size = CFG.bombSize
  const x = CFG.spawnXMargin + Math.random() * (W - 2 * CFG.spawnXMargin)
  const vy =
    CFG.launchVyMin + Math.random() * (CFG.launchVyMax - CFG.launchVyMin)
  let vx = (Math.random() * 2 - 1) * CFG.launchVxAbsMax
  if (x < W * 0.3) vx = Math.abs(vx)
  else if (x > W * 0.7) vx = -Math.abs(vx)

  engine.fruits.push({
    id: engine.nextId++,
    fruitCode: 'BOMB',
    imageUrl: '',
    score: 0,
    x,
    y: H + 90,
    vx,
    vy,
    w: size,
    h: size,
    // 폭탄은 판정을 과일보다 살짝 좁게 잡는다. 스치기만 해도 터지면 억울하다.
    radius: size * 0.42,
    // 도화선이 위를 향해야 폭탄으로 읽힌다. 살짝 흔들리는 정도만 준다.
    rotation: (Math.random() * 2 - 1) * 12,
    spin: (Math.random() * 2 - 1) * 22,
    sliced: false,
    slicedAt: now,
    cutAngle: 0,
    gained: 0,
    bomb: true,
  })
}

function spawnFruit(engine: Engine, rule: FruitGameRuntimeRule): void {
  const configuredTypes = rule.fruits.flatMap((configured) => {
    const localType = FRUIT_TYPES.find(
      (type) => type.id.toUpperCase() === configured.fruitCode.toUpperCase(),
    )
    return localType
      ? [
          {
            ...localType,
            baseSize: configured.sizePx,
            score: configured.score,
          },
        ]
      : []
  })
  const pool = configuredTypes.length > 0 ? configuredTypes : FRUIT_TYPES
  const type = pool[Math.floor(Math.random() * pool.length)]
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
    fruitCode: type.id.toUpperCase(),
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
    bomb: false,
  })
  engine.totalCount++
  const fruitCode = type.id.toUpperCase()
  engine.generatedByFruit[fruitCode] =
    (engine.generatedByFruit[fruitCode] ?? 0) + 1
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
  onBomb?: () => void,
  redesignateHand?: () => void,
  cameraError?: boolean,
  runtimeRule?: FruitGameRuntimeRule,
): FruitGameState {
  const rule = runtimeRule ?? {
    fruitSpawnIntervalMs: CFG.spawnIntervalMs,
    fruits: FRUIT_TYPES.map((type) => ({
      fruitCode: type.id.toUpperCase(),
      score: type.score,
      sizePx: type.baseSize,
    })),
    maxVisibleFruit: CFG.maxVisibleFruit,
    plannedDurationSeconds: CFG.durationSec,
    rulesVersion: CFG.ruleVersion,
    sliceSpeedThreshold: CFG.sliceSpeedThreshold,
  }
  const ruleRef = useRef(rule)
  ruleRef.current = rule
  const engineRef = useRef<Engine | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)

  const onEndRef = useRef(onEnd)
  onEndRef.current = onEnd
  const onSliceRef = useRef(onSlice)
  onSliceRef.current = onSlice
  const onBombRef = useRef(onBomb)
  onBombRef.current = onBomb
  const redesignateRef = useRef(redesignateHand)
  redesignateRef.current = redesignateHand
  const cameraErrorRef = useRef(cameraError)
  cameraErrorRef.current = cameraError

  const [snapshot, setSnapshot] = useState<Snapshot>({
    phase: 'waiting-hand',
    countdown: CFG.countdownSec,
    score: 0,
    remainingSec: rule.plannedDurationSeconds,
    combo: 0,
    fever: false,
    stunned: false,
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
      // 멈춰 있던 동안 폭탄 벌칙까지 흘려보내지 않는다. 재개하면 바로 벨 수 있다.
      engine.stunnedUntil = 0
      engine.blastAt = 0
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

    const judgeSlices = (engine: Engine, now: number): void => {
      // 폭탄에 맞은 뒤에는 아무것도 자를 수 없다.
      if (now < engine.stunnedUntil || !isSlicing(engine.tracker, now)) {
        return
      }

      for (const fruit of engine.fruits) {
        if (fruit.sliced) continue
        if (
          !sweptHit(
            engine.tracker,
            fruit.x,
            fruit.y,
            hitRadiusFor(fruit.radius),
          )
        )
          continue

        if (fruit.bomb) {
          // 폭탄: 점수도 자른 수도 늘지 않는다. 콤보와 피버가 날아가고 잠시 멈춘다.
          fruit.sliced = true
          fruit.slicedAt = now
          engine.stunnedUntil = now + CFG.bombStunMs
          engine.blastAt = now
          engine.blastPos = { x: fruit.x, y: fruit.y }
          engine.combo = 0
          engine.comboExpiresAt = 0
          engine.feverUntil = 0
          onBombRef.current?.()
          // 폭발 연출만 남기고 판을 비운다. 못 자르는 3초 동안 과일이 계속
          // 떠다니면 아이가 계속 팔을 휘두르다 더 답답해한다.
          engine.fruits = [fruit]
          return
        }

        // 최초 성공 시 한 번만 반영, 이후 판정 대상에서 제외 (MGAME-15/16).
        // 앞선 베기와 짧은 간격이면 콤보가 이어져 배수가 올라간다.
        if (now > engine.comboExpiresAt) {
          engine.combo = 0
        }
        engine.combo++
        engine.comboExpiresAt = now + CFG.comboWindowMs
        engine.maxCombo = Math.max(engine.maxCombo, engine.combo)

        // 피버는 한 번 열리면 정해진 시간만큼만 간다. 콤보로 연장하지 않는다.
        // 언제 끝나는지 알 수 있어야 아이가 "지금 몰아서 베는 구간"으로 쓴다.
        // 한 스윙에 여러 개를 베면 콤보가 한 번에 여러 칸 오르므로 >= 로 본다.
        if (engine.feverUntil === 0 && engine.combo >= CFG.feverCombo) {
          engine.feverUntil = now + CFG.feverDurationMs
          // 이미 떠 있던 폭탄은 치운다. 피버로 들어가는 순간 화면에 남아 있던
          // 폭탄 때문에 8초가 중간에 끊기면 억울하다.
          engine.fruits = engine.fruits.filter((other) => !other.bomb)
        }

        // 콤보는 과일 생성량(피버)에만 영향을 주며 점수는 과일 기본 점수로 계산한다.
        const gained = fruit.score
        fruit.sliced = true
        fruit.slicedAt = now
        fruit.gained = gained
        fruit.cutAngle = cutAngleDeg(engine.tracker)
        engine.score += gained
        engine.slicedScoreSum += gained
        engine.slicedCount++
        engine.slicedByFruit[fruit.fruitCode] =
          (engine.slicedByFruit[fruit.fruitCode] ?? 0) + 1
        onSliceRef.current?.(engine.combo)
      }
    }

    const step = (engine: Engine, dt: number, now: number): void => {
      // 피버가 끝나면 콤보를 초기화해 새 콤보를 달성해야 다시 시작되게 한다.
      if (engine.feverUntil > 0 && now >= engine.feverUntil) {
        engine.feverUntil = 0
        engine.combo = 0
        engine.comboExpiresAt = 0
      }
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
        : ruleRef.current.fruitSpawnIntervalMs
      const maxVisible = fever
        ? CFG.feverMaxVisibleFruit
        : ruleRef.current.maxVisibleFruit
      // 벤 과일 조각은 곧 사라지므로 아직 안 벤 것만 센다.
      const aliveCount = engine.fruits.reduce(
        (count, fruit) => (fruit.sliced ? count : count + 1),
        0,
      )
      // 폭탄에 맞은 동안은 아무것도 올려 보내지 않는다. 못 자르는데 쌓이기만
      // 하면 억울하다. 멈춤이 풀리는 순간부터 다시 시작한다.
      if (now < engine.stunnedUntil) {
        engine.lastSpawnAt = engine.stunnedUntil
      } else if (
        now - engine.lastSpawnAt >= spawnIntervalMs &&
        aliveCount < maxVisible
      ) {
        // 피버 중에는 폭탄을 내보내지 않는다. 쏟아지는 과일을 정신없이 베는
        // 구간이라 폭탄을 가려낼 틈이 없고, 애써 만든 콤보가 운으로 끊긴다.
        if (!fever && Math.random() < CFG.bombSpawnChance) {
          spawnBomb(engine, now)
        } else {
          spawnFruit(engine, ruleRef.current)
        }
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
        bomb: fruit.bomb,
      }))

      let blade: RenderBlade | null = null
      if (engine.tracker.blade) {
        blade = {
          ...engine.tracker.blade,
          fast: isSlicing(engine.tracker, now),
          trail: bladeTrail(engine.tracker, now),
        }
      }

      // 피버는 켜고 끌 때 세기를 부드럽게 올렸다 내려 뚝 끊기지 않게 한다.
      const feverLeft = engine.feverUntil - now
      const feverIntensity =
        feverLeft <= 0 ? 0 : Math.min(1, feverLeft / CFG.feverFadeMs)
      const blastElapsed =
        engine.blastAt === 0 ? Infinity : now - engine.blastAt
      const blast =
        blastElapsed >= CFG.bombBlastMs ? 0 : blastElapsed / CFG.bombBlastMs

      drawScene(ctx, fruits, blade, {
        fever: feverIntensity,
        blast,
        blastAt: engine.blastPos,
        now,
      })
    }

    const loop = (now: number): void => {
      // restart()가 엔진을 교체할 수 있어 매 프레임 현재 엔진을 읽는다.
      const engine = engineRef.current
      if (!engine) return

      const rawDtMs = now - engine.lastFrameTs
      const dtMs = Math.min(rawDtMs, 50)
      const dt = dtMs / 1000

      updateBladeTracker(
        engine.tracker,
        handRef.current,
        now,
        Math.max(dtMs, 1),
        ruleRef.current.sliceSpeedThreshold,
      )
      const handVisible = engine.tracker.blade !== null

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
            now - engine.tracker.lastHandSeenAt > CFG.handLostGraceMs
          ) {
            // 플레이 손이 사라지면 자동 일시정지 (MGAME-24). 복귀 시 재지정.
            engine.phase = 'interrupted'
            engine.combo = 0
            engine.feverUntil = 0
            engine.stunnedUntil = 0
            engine.blastAt = 0
            redesignateRef.current?.()
            break
          }
          engine.playElapsedMs += dtMs
          if (
            engine.playElapsedMs >=
            ruleRef.current.plannedDurationSeconds * 1000
          ) {
            engine.playElapsedMs = ruleRef.current.plannedDurationSeconds * 1000
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
        Math.ceil(
          ruleRef.current.plannedDurationSeconds - engine.playElapsedMs / 1000,
        ),
      )

      const fever = engine.phase === 'playing' && now < engine.feverUntil
      const stunned = engine.phase === 'playing' && now < engine.stunnedUntil

      // 프레임마다 setState하지 않도록 실제로 바뀐 값이 있을 때만 발행한다.
      // 과일과 칼은 캔버스에 직접 그리므로 여기서 다루지 않는다.
      const key = [
        engine.phase,
        countdown,
        remainingSec,
        engine.score,
        engine.combo,
        fever,
        stunned,
        engine.tracker.distance,
      ].join('|')

      if (key !== publishedKey) {
        publishedKey = key
        setSnapshot({
          phase: engine.phase,
          countdown,
          score: engine.score,
          remainingSec,
          combo: engine.combo,
          fever,
          stunned,
          distance: engine.tracker.distance,
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
            ruleVersion: ruleRef.current.rulesVersion,
            fruitResults: Object.entries(engine.generatedByFruit).map(
              ([fruitCode, generatedCount]) => ({
                fruitCode,
                generatedCount,
                slicedCount: engine.slicedByFruit[fruitCode] ?? 0,
              }),
            ),
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
