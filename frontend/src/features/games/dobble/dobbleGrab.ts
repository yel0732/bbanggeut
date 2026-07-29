import { cardArea, type CardSide, type DobbleRound } from './dobbleCards'
import { DOBBLE_GAME_CONFIG as CFG } from './dobbleGameConfig'

/**
 * 그림 조준·잡기 판정.
 *
 * 본 게임과 튜토리얼 실습이 이 파일을 함께 쓴다. 연습에서 잡히던 방식이 본
 * 게임에서 다르면 아이는 배운 대로 해도 안 된다고 느낀다.
 *
 * 좌표는 모두 1280x720 캔버스 기준 화면(미러링) px다.
 */

export type HandHover = {
  side: CardSide
  symbolId: string
}

export type HandAim = {
  hover: HandHover | null
  /** hover가 시작된 시각 */
  since: number
  /** 링이 다 차서 잡을 수 있는 상태인지 */
  ready: boolean
  /** 이번에 주먹을 쥔 동안 이미 잡았는지 — 손을 펴야 다시 잡을 수 있다 */
  grabbedInFist: boolean
}

export function idleAim(grabbedInFist = false): HandAim {
  return { hover: null, since: 0, ready: false, grabbedInFist }
}

/** 손바닥이 올라간 그림. 히트 영역이 겹치면 더 가까운 쪽을 고른다. */
export function hitTest(
  x: number,
  y: number,
  round: DobbleRound,
): HandHover | null {
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
 * 한 손의 조준·잡기 한 프레임.
 *
 * 손을 편 채로 그림 위에 머물면 링이 차고, 다 찬 뒤 주먹을 쥐면 잡는다. 한 번
 * 쥔 동안 잡기는 한 번뿐이라 계속 쥐고 있어도 다시 잡히지 않는다.
 */
export function stepAim(
  previous: HandAim,
  input: { x: number; y: number; fist: boolean },
  round: DobbleRound,
  now: number,
): { aim: HandAim; progress: number; grabbed: HandHover | null } {
  // 주먹을 쥐는 동안에는 조준 대상을 갱신하지 않는다. 손가락을 접는 사이
  // 손바닥 중심이 밀려 옆 동물로 넘어가는 사고를 막는다.
  const hover = input.fist ? previous.hover : hitTest(input.x, input.y, round)
  const sameHover =
    hover !== null &&
    previous.hover !== null &&
    hover.side === previous.hover.side &&
    hover.symbolId === previous.hover.symbolId

  if (!sameHover) {
    return {
      aim: {
        hover,
        since: hover ? now : 0,
        ready: false,
        grabbedInFist: false,
      },
      progress: 0,
      grabbed: null,
    }
  }

  const progress = Math.min(1, (now - previous.since) / CFG.aimMs)
  const ready = progress >= 1

  // 링이 덜 찼는데 먼저 쥐었어도 다 차는 순간 한 번 잡아 준다.
  let grabbedInFist = input.fist ? previous.grabbedInFist : false
  let grabbed: HandHover | null = null
  if (ready && hover !== null && input.fist && !grabbedInFist) {
    grabbed = hover
    grabbedInFist = true
  }

  return {
    aim: { hover, since: previous.since, ready, grabbedInFist },
    progress,
    grabbed,
  }
}
