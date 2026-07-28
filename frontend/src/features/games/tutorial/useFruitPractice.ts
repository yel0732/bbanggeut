import { useEffect, useRef, useState, type RefObject } from 'react'

import {
  FRUIT_GAME_CONFIG as CFG,
  FRUIT_TYPES,
  fruitDimensions,
} from '@/features/games/fruit/fruitGameConfig'
import {
  drawScene,
  type RenderBlade,
  type RenderFruit,
} from '@/features/games/fruit/fruitRenderer'
import {
  bladeTrail,
  createBladeTracker,
  cutAngleDeg,
  hitRadiusFor,
  isSlicing,
  sweptHit,
  updateBladeTracker,
  type BladeTracker,
} from '@/features/games/fruit/fruitSlicing'
import type { PlayHand } from '@/hooks/useHandTracking'

/**
 * 튜토리얼 과일 자르기 연습.
 *
 * 판정은 본 게임과 똑같다 — `fruitSlicing`의 같은 함수를 쓴다. 연습에서 되던
 * 동작이 본 게임에서 안 되면 아이는 자기가 잘못한 줄 알기 때문이다.
 *
 * 다른 점은 규모뿐이다. 점수도 시간 제한도 콤보도 없고, 정해진 개수만 자르면
 * 끝난다. 놓친 과일은 다시 올려 보내 아이가 성공할 때까지 기다린다.
 */

const { canvasWidth: W, canvasHeight: H } = CFG

/** 연습에 쓸 과일. 크고 느려서 처음 해 보는 아이도 맞힐 수 있다. */
const PRACTICE_FRUIT_IDS = ['watermelon', 'orange', 'apple']
/** 한 번에 한 개만 올려 보낸다. 여러 개가 뜨면 어디를 봐야 할지 모른다. */
const SPAWN_GAP_MS = 900
/** 연습 전용 발사 속도. 본편보다 완만해 화면에 오래 머문다. */
const LAUNCH_VY = -820
const LAUNCH_GRAVITY = 640

type PracticeFruit = {
  id: number
  imageUrl: string
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
  score: number
}

type Engine = {
  fruits: PracticeFruit[]
  nextId: number
  slicedCount: number
  /** 지금까지 몇 번째 과일을 올려 보냈는지 */
  spawnIndex: number
  nextSpawnAt: number
  lastFrameTs: number
  tracker: BladeTracker
  done: boolean
}

export type FruitPracticeState = {
  canvasRef: RefObject<HTMLCanvasElement | null>
  /** 지금까지 자른 개수 */
  slicedCount: number
  /** 몇 개를 잘라야 통과인지 */
  targetCount: number
  /** 방금 잘라서 칭찬을 띄우는 중인지 */
  praising: boolean
}

/** 벤 직후 칭찬 문구를 띄우는 시간(ms) */
const PRAISE_MS = 900

function pickFruitType(index: number) {
  const id = PRACTICE_FRUIT_IDS[index % PRACTICE_FRUIT_IDS.length]
  return FRUIT_TYPES.find((type) => type.id === id) ?? FRUIT_TYPES[0]
}

function spawnFruit(engine: Engine): void {
  const type = pickFruitType(engine.spawnIndex)
  const { h, w } = fruitDimensions(type)
  // 가운데를 중심으로 조금씩 어긋나게 띄운다. 늘 같은 자리면 연습이 안 된다.
  const offsets = [0, -180, 180]
  const x = W / 2 + offsets[engine.spawnIndex % offsets.length]

  engine.fruits.push({
    id: engine.nextId++,
    imageUrl: type.imageUrl,
    x,
    y: H + 90,
    vx: 0,
    vy: LAUNCH_VY,
    w,
    h,
    radius: (w + h) / 4,
    rotation: Math.random() * 360,
    spin: (Math.random() * 2 - 1) * 60,
    sliced: false,
    slicedAt: 0,
    cutAngle: 0,
    score: type.score,
  })
  engine.spawnIndex++
}

export function useFruitPractice({
  active,
  handRef,
  onSlice,
  onComplete,
  targetCount,
}: {
  active: boolean
  handRef: RefObject<PlayHand | null>
  onSlice?: () => void
  onComplete: () => void
  targetCount: number
}): FruitPracticeState {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [progress, setProgress] = useState({ slicedCount: 0, praising: false })

  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete
  const onSliceRef = useRef(onSlice)
  onSliceRef.current = onSlice

  useEffect(() => {
    if (!active) {
      setProgress({ slicedCount: 0, praising: false })
      return
    }

    const now = performance.now()
    const engine: Engine = {
      fruits: [],
      nextId: 1,
      slicedCount: 0,
      spawnIndex: 0,
      nextSpawnAt: now + SPAWN_GAP_MS,
      lastFrameTs: now,
      tracker: createBladeTracker(now),
      done: false,
    }

    let rafId = 0
    let doneTimer = 0
    let praiseUntil = 0
    let publishedKey = ''

    const loop = (frameNow: number): void => {
      const dtMs = Math.min(frameNow - engine.lastFrameTs, 50)
      engine.lastFrameTs = frameNow
      const dt = dtMs / 1000

      updateBladeTracker(
        engine.tracker,
        handRef.current,
        frameNow,
        Math.max(dtMs, 1),
        CFG.sliceSpeedThreshold,
      )

      for (const fruit of engine.fruits) {
        fruit.vy += LAUNCH_GRAVITY * dt
        fruit.x += fruit.vx * dt
        fruit.y += fruit.vy * dt
        fruit.rotation += fruit.spin * dt
      }

      // 못 자르고 떨어진 과일은 조용히 치우고 다시 올려 보낸다.
      let missed = false
      engine.fruits = engine.fruits.filter((fruit) => {
        if (fruit.sliced) {
          return frameNow - fruit.slicedAt < CFG.slicedLingerMs
        }
        if (fruit.y >= H + 140) {
          missed = true
          return false
        }
        return true
      })
      if (missed) {
        // 놓쳐도 그냥 다음 과일을 올려 준다. 못 자르면 진도가 멈추는 게 아니라
        // 계속 기회가 오는 쪽이 아이한테 낫다.
        engine.nextSpawnAt = frameNow + SPAWN_GAP_MS
      }

      const aliveCount = engine.fruits.reduce(
        (count, fruit) => (fruit.sliced ? count : count + 1),
        0,
      )
      if (
        !engine.done &&
        aliveCount === 0 &&
        frameNow >= engine.nextSpawnAt &&
        engine.slicedCount < targetCount
      ) {
        spawnFruit(engine)
      }

      if (isSlicing(engine.tracker, frameNow)) {
        for (const fruit of engine.fruits) {
          if (fruit.sliced) continue
          if (
            !sweptHit(
              engine.tracker,
              fruit.x,
              fruit.y,
              hitRadiusFor(fruit.radius),
            )
          ) {
            continue
          }
          fruit.sliced = true
          fruit.slicedAt = frameNow
          fruit.cutAngle = cutAngleDeg(engine.tracker)
          engine.slicedCount++
          engine.nextSpawnAt = frameNow + SPAWN_GAP_MS
          praiseUntil = frameNow + PRAISE_MS
          onSliceRef.current?.()
        }
      }

      const praising = frameNow < praiseUntil
      const key = engine.slicedCount + '|' + praising
      if (key !== publishedKey) {
        publishedKey = key
        setProgress({ slicedCount: engine.slicedCount, praising })
      }

      const ctx = canvasRef.current?.getContext('2d')
      if (ctx) {
        const fruits: RenderFruit[] = engine.fruits.map((fruit) => ({
          imageUrl: fruit.imageUrl,
          x: fruit.x,
          y: fruit.y,
          w: fruit.w,
          h: fruit.h,
          rotation: fruit.rotation,
          sliced: fruit.sliced,
          sliceProgress: fruit.sliced
            ? Math.min(1, (frameNow - fruit.slicedAt) / CFG.slicedLingerMs)
            : 0,
          cutAngle: fruit.cutAngle,
          gained: fruit.score,
        }))

        let blade: RenderBlade | null = null
        if (engine.tracker.blade) {
          blade = {
            ...engine.tracker.blade,
            fast: isSlicing(engine.tracker, frameNow),
            trail: bladeTrail(engine.tracker, frameNow),
          }
        }
        drawScene(ctx, fruits, blade)
      }

      // 마지막 조각이 사라지는 것까지 보여 주고 넘어간다.
      if (!engine.done && engine.slicedCount >= targetCount) {
        engine.done = true
        doneTimer = window.setTimeout(
          () => onCompleteRef.current(),
          CFG.slicedLingerMs,
        )
      }

      rafId = requestAnimationFrame(loop)
    }

    rafId = requestAnimationFrame(loop)
    return () => {
      cancelAnimationFrame(rafId)
      // 통과 직후 화면을 벗어나면 다음 단계로 밀지 않는다.
      window.clearTimeout(doneTimer)
    }
  }, [active, handRef, targetCount])

  return { canvasRef, targetCount, ...progress }
}
