import {
  DOBBLE_GAME_CONFIG as CFG,
  SYMBOL_BASE_SIZE,
  SYMBOL_SLOTS,
} from './dobbleGameConfig'
import { DOBBLE_SYMBOLS, type DobbleSymbol } from './dobbleSymbols'

export type CardSide = 'left' | 'right'

/** 나무판 한쪽에 놓인 그림 하나. 좌표는 그 영역의 좌상단 기준 px. */
export type PlacedSymbol = {
  symbolId: string
  name: string
  imageUrl: string
  x: number
  y: number
  w: number
  h: number
  /** 표시 회전(도) — 판에 흩어 놓은 느낌 */
  rotation: number
  /** 손 포인터 히트 판정 반경(px) */
  hitRadius: number
}

export type DobbleRound = {
  index: number
  /** 두 쪽이 공유하는 단 하나의 심볼 */
  commonSymbolId: string
  left: PlacedSymbol[]
  right: PlacedSymbol[]
}

function shuffle<T>(items: T[]): T[] {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

function jitter(range: number): number {
  return (Math.random() * 2 - 1) * range
}

/** 슬롯에 심볼을 얹는다. 슬롯 순서도 섞어 정답 위치가 굳지 않게 한다. */
function placeSymbols(symbols: DobbleSymbol[], side: CardSide): PlacedSymbol[] {
  const slots = shuffle(SYMBOL_SLOTS)
  const area = CFG.area[side]

  return symbols.map((symbol, index) => {
    const slot = slots[index]
    // 심볼마다 크기를 살짝 다르게 해 "같은 그림 찾기"가 크기 비교로 풀리지 않게 한다.
    const size = SYMBOL_BASE_SIZE * (0.94 + Math.random() * 0.12)
    const w = symbol.aspect >= 1 ? size : size * symbol.aspect
    const h = symbol.aspect >= 1 ? size / symbol.aspect : size

    // 지터는 배치를 자연스럽게 하되 그림이 판 밖으로 나가지 않는 범위로 제한한다.
    const cx = (slot.x + jitter(0.02)) * area.w
    const cy = (slot.y + jitter(0.02)) * area.h

    return {
      symbolId: symbol.id,
      name: symbol.name,
      imageUrl: symbol.imageUrl,
      x: Math.min(Math.max(cx, w / 2), area.w - w / 2),
      y: Math.min(Math.max(cy, h / 2), area.h - h / 2),
      w,
      h,
      rotation: jitter(7),
      hitRadius: size * CFG.hitRadiusScale,
    }
  })
}

/**
 * 공통 그림이 정확히 하나인 좌우 5마리씩을 만든다.
 *
 * 한쪽 동물 수를 n(=5)이라 하면 서로 다른 심볼 2n-1(=9)개를 뽑아
 * `[공통 1개] + [왼쪽 전용 4개] + [오른쪽 전용 4개]`로 나눈다. 두 전용 집합은
 * 서로소이고 공통 심볼을 포함하지 않으므로 교집합은 항상 공통 심볼 하나뿐이다.
 * 필요한 9개는 카탈로그(20개) 안에 들어가고, 매 라운드 무작위로 다시 뽑는다.
 *
 * @param previousCommonId 직전 라운드의 공통 심볼. 같은 정답이 연속되지 않게 제외한다.
 */
export function createDobbleRound(
  index: number,
  previousCommonId?: string,
): DobbleRound {
  const n = CFG.symbolsPerSide
  const pool = shuffle(DOBBLE_SYMBOLS)
  // 직전 공통 심볼을 뒤로 밀어 연속 정답을 피한다.
  const commonIndex = pool.findIndex((symbol) => symbol.id !== previousCommonId)
  const common = pool[commonIndex === -1 ? 0 : commonIndex]
  const others = pool.filter((symbol) => symbol.id !== common.id)

  const leftOnly = others.slice(0, n - 1)
  const rightOnly = others.slice(n - 1, 2 * (n - 1))

  return {
    index,
    commonSymbolId: common.id,
    left: placeSymbols(shuffle([common, ...leftOnly]), 'left'),
    right: placeSymbols(shuffle([common, ...rightOnly]), 'right'),
  }
}

/** 나무판 한쪽 영역. */
export function cardArea(side: CardSide): {
  x: number
  y: number
  w: number
  h: number
} {
  return CFG.area[side]
}
