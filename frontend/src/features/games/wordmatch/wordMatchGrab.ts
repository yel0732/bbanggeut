import {
  SLOT_ASPECT,
  SLOT_INNER_RATIO,
  WORD_MATCH_CONFIG as CFG,
} from './wordMatchConfig'

/**
 * 글자 조준·잡기·놓기 판정과 배치 계산.
 *
 * 본 게임과 (나중에 붙을) 튜토리얼 실습이 이 파일을 함께 쓸 수 있게 화면과
 * 분리해 둔다. 좌표는 모두 1280x720 캔버스 기준 화면(미러링) px다.
 */

/** 글자 한 장. 카드 자리에 있거나, 정답 칸에 놓였거나, 손에 들려 있다. */
export type LetterTile = {
  /** 이 문제에서만 쓰는 고유 번호. 같은 글자가 두 장일 수 있다. */
  uid: number
  letter: string
  /** 원래 카드 자리 번호(0부터). 놓기에 실패하면 이 자리로 돌아간다. */
  trayIndex: number
  /** 놓여 있는 정답 칸 번호. null이면 카드 자리에 있다. */
  slotIndex: number | null
  /**
   * 오답 판정에서 자리와 글자가 맞아 고정된 글자(WGAME-09).
   *
   * 고정되면 다시 잡을 수 없다.
   */
  locked: boolean
  /** 이 글자를 들고 있는 손. 없으면 놓여 있다. */
  heldBy: 'a' | 'b' | null
  /** 손에 들려 있을 때의 화면 좌표 */
  handX: number
  handY: number
}

export type HandAim = {
  /** 조준 중인 글자 uid */
  hoverUid: number | null
  /** hover가 시작된 시각 */
  since: number
  /** 링이 다 차서 잡을 수 있는 상태인지 */
  ready: boolean
  /** 이번에 주먹을 쥔 동안 이미 잡았는지 — 손을 펴야 다시 잡을 수 있다 */
  grabbedInFist: boolean
  /** 이 손이 들고 있는 글자 uid */
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

/** 정답 칸 한 개의 표시 크기 */
export function slotSize(): { width: number; height: number } {
  return { width: CFG.slot.height * SLOT_ASPECT, height: CFG.slot.height }
}

/**
 * 정답 칸의 나무 테두리 안쪽 크기.
 *
 * 칸에 놓인 글자를 이 크기로 그린다. 그러면 칸을 키우든 줄이든 글자가 테두리
 * 안에 꼭 맞고, 나무 틈으로 삐져나오지 않는다.
 *
 * 정사각형으로 맞춘다. 글자 카드가 정사각이라 가로세로가 다른 상자에 넣으면
 * 한쪽이 남거나 잘린다.
 */
export function slotInnerSize(): number {
  const { width, height } = slotSize()
  return Math.min(
    width * SLOT_INNER_RATIO.width,
    height * SLOT_INNER_RATIO.height,
  )
}

/**
 * 그 글자를 그릴 한 변 크기.
 *
 * 나무 띠에 있거나 손에 들려 있으면 카드 크기, 정답 칸에 놓여 있으면 칸 안쪽
 * 크기다. 놓는 순간 칸에 앉는 것처럼 보인다.
 */
export function tileSize(tile: LetterTile): number {
  return tile.slotIndex !== null && tile.heldBy === null
    ? slotInnerSize()
    : CFG.tray.cardSize
}

/**
 * 카메라 손 좌표를 화면 좌표로 옮긴다.
 *
 * 미러링과 도달 범위 넓히기를 함께 한다. 아이는 카메라 프레임 가장자리까지 팔을
 * 뻗지 않아서, 그대로 옮기면 양쪽 끝 카드에 손이 닿지 않는다.
 */
export function screenFromHand(hand: { x: number; y: number }): {
  x: number
  y: number
} {
  const gain = CFG.reachGain
  const stretch = (value: number) =>
    Math.min(1, Math.max(0, 0.5 + (value - 0.5) * gain))

  return {
    x: stretch(1 - hand.x) * CFG.canvasWidth,
    y: stretch(hand.y) * CFG.canvasHeight,
  }
}

/** 문제 그림 가운데 좌표. 위 판 가운데에 맞춘다. */
export function pictureCenter(): { x: number; y: number } {
  return {
    x: CFG.board.x + CFG.board.width / 2,
    y: CFG.picture.centerY,
  }
}

/**
 * 정답 칸 하나의 가운데 좌표.
 *
 * 낱말 길이만큼 가로로 늘어놓고 화면 가운데에 맞춘다. 위 판이 아니라 판 사이
 * 잔디에 서 있으므로 판 폭이 아니라 화면 폭을 기준으로 잡는다.
 */
export function slotCenter(
  slotIndex: number,
  wordLength: number,
): { x: number; y: number } {
  const { width } = slotSize()
  const total = wordLength * width + (wordLength - 1) * CFG.slot.gap
  const startX = CFG.canvasWidth / 2 - total / 2

  return {
    x: startX + slotIndex * (width + CFG.slot.gap) + width / 2,
    y: CFG.slot.centerY,
  }
}

/**
 * 글자 카드 자리 하나의 가운데 좌표.
 *
 * 나무 띠 안에 한 줄로 늘어놓는다(WGAME-12).
 */
export function trayCenter(trayIndex: number): { x: number; y: number } {
  const { cardSize, gap } = CFG.tray
  const count = CFG.letterCardCount
  const total = count * cardSize + (count - 1) * gap
  const startX = CFG.tray.x + CFG.tray.width / 2 - total / 2

  return {
    x: startX + trayIndex * (cardSize + gap) + cardSize / 2,
    y: CFG.tray.y + CFG.tray.height / 2,
  }
}

/** 그 글자가 지금 그려지는 자리. 손에 들려 있으면 손 좌표다. */
export function tileCenter(
  tile: LetterTile,
  wordLength: number,
): { x: number; y: number } {
  if (tile.heldBy !== null) {
    return { x: tile.handX, y: tile.handY }
  }
  if (tile.slotIndex !== null) {
    return slotCenter(tile.slotIndex, wordLength)
  }
  return trayCenter(tile.trayIndex)
}

export function hitRadius(): number {
  return CFG.tray.cardSize * CFG.hitRadiusScale
}

/**
 * 손바닥이 올라간 글자.
 *
 * 카드 자리에 있는 글자와 정답 칸에 놓인 글자를 모두 집을 수 있다(WGAME-06).
 * 고정된 글자와 다른 손이 든 글자는 고르지 않는다.
 */
export function hitTestTile(
  x: number,
  y: number,
  tiles: LetterTile[],
  wordLength: number,
): LetterTile | null {
  const radius = hitRadius()
  let best: LetterTile | null = null
  let bestDistSq = Infinity

  for (const tile of tiles) {
    if (tile.heldBy !== null || tile.locked) continue
    const center = tileCenter(tile, wordLength)
    const dx = x - center.x
    const dy = y - center.y
    const distSq = dx * dx + dy * dy
    if (distSq <= radius * radius && distSq < bestDistSq) {
      bestDistSq = distSq
      best = tile
    }
  }

  return best
}

/**
 * 손이 놓은 자리의 정답 칸 번호.
 *
 * 칸보다 넉넉하게 잡는다. 아이 손은 칸 안까지 정확히 들어오지 않는데 거기서
 * 놓아도 안 들어가면 왜 안 되는지 알 수 없다.
 */
export function slotAt(
  x: number,
  y: number,
  wordLength: number,
): number | null {
  const { width, height } = slotSize()
  const halfW = width * 0.62
  const halfH = height * 0.7

  for (let index = 0; index < wordLength; index++) {
    const center = slotCenter(index, wordLength)
    if (Math.abs(x - center.x) <= halfW && Math.abs(y - center.y) <= halfH) {
      return index
    }
  }

  return null
}

export type AimStep = {
  aim: HandAim
  /** 조준 링 진행도(0~1) */
  progress: number
  /** 이번 프레임에 잡은 글자 */
  grabbed: LetterTile | null
  /** 이번 프레임에 놓은 글자와 놓은 칸 (칸이 아니면 null) */
  released: { tile: LetterTile; slotIndex: number | null } | null
}

/**
 * 한 손의 조준·잡기·놓기 한 프레임 (WGAME-05, WGAME-06).
 *
 * 손을 편 채로 글자 위에 머물면 링이 차고, 다 찬 뒤 주먹을 쥐면 잡는다. 주먹을
 * 유지하면 글자가 따라오고, 손을 펴면 그 자리에 놓는다. 한 번 쥔 동안 잡기는
 * 한 번뿐이라 계속 쥐고 있어도 다른 글자가 딸려오지 않는다.
 */
export function stepAim(
  previous: HandAim,
  input: { x: number; y: number; fist: boolean },
  tiles: LetterTile[],
  wordLength: number,
  now: number,
): AimStep {
  const held =
    previous.heldUid === null
      ? null
      : (tiles.find((tile) => tile.uid === previous.heldUid) ?? null)

  // 들고 있던 글자가 사라졌으면(문제 교체 등) 손을 비운다.
  if (previous.heldUid !== null && !held) {
    return {
      aim: idleAim({ grabbedInFist: input.fist }),
      progress: 0,
      grabbed: null,
      released: null,
    }
  }

  if (held) {
    if (!input.fist) {
      return {
        aim: idleAim(),
        progress: 0,
        grabbed: null,
        released: {
          tile: held,
          slotIndex: slotAt(input.x, input.y, wordLength),
        },
      }
    }
    return {
      aim: { ...previous, hoverUid: held.uid, ready: true },
      progress: 1,
      grabbed: null,
      released: null,
    }
  }

  // 주먹을 쥐는 동안에는 조준 대상을 갱신하지 않는다. 손가락을 접는 사이 손바닥
  // 중심이 밀려 옆 글자로 넘어가는 사고를 막는다.
  const hover = input.fist
    ? (tiles.find((tile) => tile.uid === previous.hoverUid) ?? null)
    : hitTestTile(input.x, input.y, tiles, wordLength)
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
  let grabbed: LetterTile | null = null
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
