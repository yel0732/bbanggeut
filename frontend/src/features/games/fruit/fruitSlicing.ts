import type { DistanceHint, PlayHand } from '@/hooks/useHandTracking'

import { FRUIT_GAME_CONFIG as CFG } from './fruitGameConfig'

/**
 * 칼날 추적과 베기 판정.
 *
 * 본편과 튜토리얼 실습이 이 파일을 함께 쓴다. 연습에서 되던 동작이 본 게임에서
 * 안 되면 아이는 자기가 잘못한 줄 안다. 판정이 두 벌로 갈라지지 않도록 상태와
 * 계산을 여기 한곳에 모은다.
 *
 * 좌표는 모두 화면(미러링) px다. 정규화 좌표를 그대로 쓰면 화면이 16:9라서
 * 세로 움직임이 가로보다 짧게 측정된다.
 */

const { canvasWidth: W, canvasHeight: H } = CFG

export type Blade = { ax: number; ay: number; bx: number; by: number }

export type BladeTracker = {
  /** 평활된 현재 칼날. 손이 없으면 null */
  blade: Blade | null
  /** 직전 프레임 칼날 — 사이를 훑어 빠른 스윙을 놓치지 않는다 */
  prevBlade: Blade | null
  /** 칼끝 이동 기록. 속도 계산과 잔상에 함께 쓴다 */
  tipHistory: { t: number; x: number; y: number }[]
  /** 이 시각까지는 베기 가능 상태로 본다 */
  fastUntil: number
  /** 마지막으로 손이 실제로 잡힌 시각 */
  lastHandSeenAt: number
  distance: DistanceHint | null
}

export function createBladeTracker(now: number): BladeTracker {
  return {
    blade: null,
    prevBlade: null,
    tipHistory: [],
    fastUntil: 0,
    lastHandSeenAt: now,
    distance: null,
  }
}

/** 인식 좌표를 화면 px 칼날로 옮긴다. 길이는 거리·방향과 무관하게 고정한다. */
function bladeFromHand(play: PlayHand): Blade {
  // 카메라 미러링: 아이가 든 손이 화면에서도 같은 쪽에 보이도록 x를 뒤집는다.
  const wristX = (1 - play.wrist.x) * W
  const wristY = play.wrist.y * H
  const dirX = wristX - (1 - play.elbow.x) * W
  const dirY = wristY - play.elbow.y * H
  const dirLen = Math.hypot(dirX, dirY) || 1
  return {
    ax: wristX,
    ay: wristY,
    bx: wristX + (dirX / dirLen) * CFG.bladeLengthPx,
    by: wristY + (dirY / dirLen) * CFG.bladeLengthPx,
  }
}

/**
 * 최신 인식 결과로 칼날과 속도를 갱신한다.
 *
 * 인식은 초당 30번 남짓 들어오는데 화면은 60fps로 돈다. 새 좌표가 없는 프레임에
 * 손이 멈춘 것으로 보면 베기가 절반쯤 사라지므로, 칼은 시상수로 목표를 따라가고
 * 속도는 짧은 시간 구간의 평균으로 잰다. 인식이 잠깐 끊겨도 handHoldMs 동안은
 * 직전 칼을 유지해 깜빡이지 않게 한다.
 */
export function updateBladeTracker(
  tracker: BladeTracker,
  play: PlayHand | null,
  now: number,
  dtMs: number,
  speedThreshold: number,
): void {
  if (play) {
    tracker.lastHandSeenAt = now
    tracker.distance = play.distance
  } else if (now - tracker.lastHandSeenAt > CFG.handHoldMs) {
    tracker.blade = null
    tracker.prevBlade = null
    tracker.tipHistory = []
    tracker.distance = null
    return
  }

  const target = play ? bladeFromHand(play) : null

  if (!tracker.blade) {
    if (!target) {
      return
    }
    tracker.blade = { ...target }
    tracker.prevBlade = { ...target }
  } else {
    tracker.prevBlade = { ...tracker.blade }
    if (target) {
      // 프레임 간격이 들쭉날쭉해도 같은 속도로 따라가도록 시상수를 쓴다.
      const k = 1 - Math.exp(-dtMs / CFG.handSmoothTauMs)
      tracker.blade.ax += (target.ax - tracker.blade.ax) * k
      tracker.blade.ay += (target.ay - tracker.blade.ay) * k
      tracker.blade.bx += (target.bx - tracker.blade.bx) * k
      tracker.blade.by += (target.by - tracker.blade.by) * k
    }
  }

  const tip = tracker.blade
  tracker.tipHistory.push({ t: now, x: tip.bx, y: tip.by })
  while (
    tracker.tipHistory.length > 2 &&
    tracker.tipHistory[0].t < now - Math.max(CFG.speedWindowMs, CFG.trailMs)
  ) {
    tracker.tipHistory.shift()
  }

  // 한 프레임 차이는 값이 심하게 튄다. 일정 시간 구간의 평균 속도로 본다.
  const cutoff = now - CFG.speedWindowMs
  let base = tracker.tipHistory[0]
  for (const point of tracker.tipHistory) {
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
    if (speed >= speedThreshold) {
      // 인식 프레임이 비어 속도가 순간 0이 되어도 베기가 끊기지 않게 유지한다.
      tracker.fastUntil = now + CFG.sliceSpeedHoldMs
    }
  }
}

/** 지금 베기가 성립하는 속도인지. */
export function isSlicing(tracker: BladeTracker, now: number): boolean {
  return tracker.blade !== null && now < tracker.fastUntil
}

/** 잔상으로 그릴 최근 칼끝 경로. */
export function bladeTrail(
  tracker: BladeTracker,
  now: number,
): { x: number; y: number }[] {
  const from = now - CFG.trailMs
  return tracker.tipHistory
    .filter((point) => point.t >= from)
    .map((point) => ({ x: point.x, y: point.y }))
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

/** 과일 반지름에 칼날 두께까지 더한 실제 판정 반경. */
export function hitRadiusFor(fruitRadius: number): number {
  return fruitRadius * CFG.hitRadiusScale + CFG.bladeThicknessPx
}

/**
 * 직전 프레임 칼날과 이번 프레임 칼날 사이를 훑어 과일에 닿았는지 본다.
 *
 * 한 점만 검사하면 빠르게 휘두를 때 칼이 과일을 뛰어넘는다.
 */
export function sweptHit(
  tracker: BladeTracker,
  cx: number,
  cy: number,
  hitRadius: number,
): boolean {
  if (!tracker.blade) {
    return false
  }
  const to = cuttingEdge(tracker.blade)
  const from = cuttingEdge(tracker.prevBlade ?? tracker.blade)
  const travel = Math.hypot(to.bx - from.bx, to.by - from.by)
  const steps = Math.min(8, Math.max(1, Math.ceil(travel / 28)))

  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    const ax = from.ax + (to.ax - from.ax) * t
    const ay = from.ay + (to.ay - from.ay) * t
    const bx = from.bx + (to.bx - from.bx) * t
    const by = from.by + (to.by - from.by) * t
    if (segmentPointDistSq(ax, ay, bx, by, cx, cy) <= hitRadius * hitRadius) {
      return true
    }
  }
  return false
}

/** 벤 방향(도). 과일 조각이 갈라지는 각도로 쓴다. */
export function cutAngleDeg(tracker: BladeTracker): number {
  if (!tracker.blade) {
    return 0
  }
  const edge = cuttingEdge(tracker.blade)
  return (Math.atan2(edge.by - edge.ay, edge.bx - edge.ax) * 180) / Math.PI
}
