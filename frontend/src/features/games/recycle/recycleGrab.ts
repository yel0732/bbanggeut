import { RECYCLE_GAME_CONFIG as CFG } from './recycleGameConfig'
import { RECYCLE_BINS, type RecycleCategory } from './recycleItems'

/**
 * 쓰레기 조준·잡기·놓기 판정.
 *
 * 본 게임과 튜토리얼 실습이 이 파일을 함께 쓴다. 연습에서 잡히던 방식이 본
 * 게임에서 다르면 아이는 배운 대로 해도 안 된다고 느낀다.
 *
 * 좌표는 모두 1280x720 캔버스 기준 화면(미러링) px다.
 */

/** 물 위에 떠 있거나 손에 들려 있는 쓰레기 한 개. */
export type FloatingItem = {
  /** 이 판에서만 쓰는 고유 번호. 같은 종류가 여러 개 떠 있을 수 있다. */
  uid: number
  code: string
  name: string
  category: RecycleCategory
  imageUrl: string
  /** 중심 x (캔버스 px) */
  x: number
  /** 중심 y (캔버스 px) */
  y: number
  /** 물길에서의 x. 잡았다 놓으면 이 자리로 돌아간다(RGAME-06). */
  riverX: number
  /** 이 쓰레기의 이동 속도(px/초). 생길 때 정해지고 바뀌지 않는다. */
  speedPx: number
  /** 물길 기준 높이에서 벗어난 정도(px). 갈래를 나누는 값이다. */
  laneOffsetY: number
  /** 물결에 흔들리는 위상. 쓰레기마다 달라야 같이 출렁이지 않는다. */
  bobPhaseMs: number
  /** 이 쓰레기를 들고 있는 손. 없으면 물에 떠 있다. */
  heldBy: 'a' | 'b' | null
  /** 판정 연출 중인지. 이 동안 추가 입력을 받지 않는다(RGAME-07). */
  judging: boolean
}

export type HandAim = {
  /** 조준 중인 쓰레기 uid */
  hoverUid: number | null
  /** hover가 시작된 시각 */
  since: number
  /** 링이 다 차서 잡을 수 있는 상태인지 */
  ready: boolean
  /** 이번에 주먹을 쥔 동안 이미 잡았는지 — 손을 펴야 다시 잡을 수 있다 */
  grabbedInFist: boolean
  /** 이 손이 들고 있는 쓰레기 uid */
  heldUid: number | null
}

export function idleAim(overrides: Partial<HandAim> = {}): HandAim {
  return {
    hoverUid: null,
    since: 0,
    ready: false,
    grabbedInFist: false,
    heldUid: null,
    ...overrides,
  }
}

export function hitRadius(): number {
  return CFG.itemSize * CFG.hitRadiusScale
}

/** 자기 갈래 높이에서 물결에 따라 흔들리는 y. 잡히면 흔들리지 않는다. */
export function riverY(item: FloatingItem, now: number): number {
  const phase =
    ((now + item.bobPhaseMs) % CFG.river.bobPeriodMs) / CFG.river.bobPeriodMs
  return (
    CFG.river.y +
    item.laneOffsetY +
    Math.sin(phase * Math.PI * 2) * CFG.river.bobAmplitude
  )
}

/**
 * 손바닥이 올라간 쓰레기.
 *
 * 겹쳐 있으면 더 가까운 쪽을 고른다. 이미 다른 손이 들고 있거나 판정 중인
 * 쓰레기는 고르지 않는다.
 */
export function hitTest(
  x: number,
  y: number,
  items: FloatingItem[],
): FloatingItem | null {
  const radius = hitRadius()
  let best: FloatingItem | null = null
  let bestDistSq = Infinity

  for (const item of items) {
    if (item.heldBy !== null || item.judging) continue
    const dx = x - item.x
    const dy = y - item.y
    const distSq = dx * dx + dy * dy
    if (distSq <= radius * radius && distSq < bestDistSq) {
      bestDistSq = distSq
      best = item
    }
  }

  return best
}

/**
 * 손이 놓은 자리의 분리수거 통.
 *
 * 통 입구보다 위쪽까지 넉넉하게 잡는다(`dropPadTop`). 아이 손은 통 안까지
 * 정확히 내려오지 않는데 거기서 놓아도 안 들어가면 왜 안 되는지 알 수 없다.
 */
export function binAt(x: number, y: number): RecycleCategory | null {
  const { bin } = CFG
  const top = bin.y - bin.dropPadTop
  const bottom = bin.y + bin.height

  if (y < top || y > bottom) {
    return null
  }

  for (const candidate of RECYCLE_BINS) {
    const half = bin.width / 2
    if (x >= candidate.centerX - half && x <= candidate.centerX + half) {
      return candidate.category
    }
  }

  return null
}

export type AimStep = {
  aim: HandAim
  /** 조준 링 진행도(0~1) */
  progress: number
  /** 이번 프레임에 잡은 쓰레기 */
  grabbed: FloatingItem | null
  /** 이번 프레임에 놓은 쓰레기와 놓은 자리 */
  released: { item: FloatingItem; bin: RecycleCategory | null } | null
}

/**
 * 한 손의 조준·잡기·놓기 한 프레임.
 *
 * 손을 편 채로 쓰레기 위에 머물면 링이 차고, 다 찬 뒤 주먹을 쥐면 잡는다.
 * 주먹을 유지하면 쓰레기가 따라오고, 손을 펴면 그 자리에 놓는다(RGAME-05).
 * 한 손은 한 번에 하나만 들 수 있다.
 */
export function stepAim(
  previous: HandAim,
  input: { x: number; y: number; fist: boolean },
  items: FloatingItem[],
  now: number,
): AimStep {
  const held =
    previous.heldUid === null
      ? null
      : (items.find((item) => item.uid === previous.heldUid) ?? null)

  // 들고 있던 쓰레기가 사라졌으면(판정 완료 등) 손을 비운다.
  if (previous.heldUid !== null && !held) {
    return {
      aim: idleAim({ grabbedInFist: input.fist }),
      progress: 0,
      grabbed: null,
      released: null,
    }
  }

  if (held) {
    // 손을 펴면 그 자리에 놓는다. 통 위가 아니면 물길로 돌아간다(RGAME-06).
    if (!input.fist) {
      return {
        aim: idleAim(),
        progress: 0,
        grabbed: null,
        released: { item: held, bin: binAt(input.x, input.y) },
      }
    }
    // 주먹을 유지하는 동안에는 손을 따라온다.
    return {
      aim: { ...previous, hoverUid: held.uid, ready: true },
      progress: 1,
      grabbed: null,
      released: null,
    }
  }

  // 주먹을 쥐는 동안에는 조준 대상을 갱신하지 않는다. 손가락을 접는 사이
  // 손바닥 중심이 밀려 옆 쓰레기로 넘어가는 사고를 막는다.
  const hover = input.fist
    ? (items.find((item) => item.uid === previous.hoverUid) ?? null)
    : hitTest(input.x, input.y, items)
  const sameHover = hover !== null && hover.uid === previous.hoverUid

  if (!sameHover) {
    return {
      aim: idleAim({
        hoverUid: hover?.uid ?? null,
        since: hover ? now : 0,
      }),
      progress: 0,
      grabbed: null,
      released: null,
    }
  }

  const progress = Math.min(1, (now - previous.since) / CFG.aimMs)
  const ready = progress >= 1

  // 링이 덜 찼는데 먼저 쥐었어도 다 차는 순간 한 번 잡아 준다.
  let grabbedInFist = input.fist ? previous.grabbedInFist : false
  let grabbed: FloatingItem | null = null
  if (ready && hover && input.fist && !grabbedInFist) {
    grabbed = hover
    grabbedInFist = true
  }

  return {
    aim: {
      hoverUid: hover.uid,
      since: previous.since,
      ready,
      grabbedInFist,
      heldUid: grabbed ? grabbed.uid : null,
    },
    progress,
    grabbed,
    released: null,
  }
}
