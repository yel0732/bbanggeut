import leafSwordUrl from '@/assets/images/fruit-game/leaf-sword.webp'

import { FRUIT_GAME_CONFIG as CFG, FRUIT_TYPES } from './fruitGameConfig'

/**
 * 과일 자르기 플레이 화면 그리기.
 *
 * 과일·칼·트레일은 매 프레임 위치가 바뀐다. DOM으로 그리면 프레임마다 React
 * 리렌더가 돌아 동작이 끊겨 보이므로 canvas 2D에 직접 그린다. 게임 상태 중
 * 실제로 값이 바뀔 때만 바뀌는 것(점수·남은 시간·콤보 등)만 React가 맡는다.
 */

const DEG = Math.PI / 180

// leaf-sword.webp 내 손잡이 끝/칼끝 앵커 (이미지 비율 좌표)
const SWORD_SIZE = 360
const SWORD_HANDLE = { x: 0.8, y: 0.82 }
const SWORD_TIP = { x: 0.14, y: 0.16 }
/** 검 이미지가 원래 길이보다 작아지지 않게 하는 하한 배율 */
const SWORD_MIN_SCALE = 0.62

const imageCache = new Map<string, HTMLImageElement>()

function loadImage(url: string): HTMLImageElement {
  const cached = imageCache.get(url)
  if (cached) {
    return cached
  }
  const image = new Image()
  image.decoding = 'async'
  image.src = url
  imageCache.set(url, image)
  return image
}

// 첫 과일이 뜰 때 이미지가 비어 보이지 않도록 미리 받아 둔다.
FRUIT_TYPES.forEach((type) => loadImage(type.imageUrl))
const swordImage = loadImage(leafSwordUrl)

export type RenderFruit = {
  imageUrl: string
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
  /** 실제로 얻은 점수 (콤보 배수 반영) */
  gained: number
}

export type RenderBlade = {
  ax: number
  ay: number
  bx: number
  by: number
  /** 베기 기준 속도 이상인지 — 잔상/광택을 강하게 준다 */
  fast: boolean
  trail: { x: number; y: number }[]
}

function drawWholeFruit(
  ctx: CanvasRenderingContext2D,
  fruit: RenderFruit,
  image: HTMLImageElement,
): void {
  ctx.save()
  ctx.translate(fruit.x, fruit.y)
  ctx.rotate(fruit.rotation * DEG)
  ctx.shadowColor = 'rgba(20,50,20,0.4)'
  ctx.shadowBlur = 8
  ctx.shadowOffsetY = 6
  ctx.drawImage(image, -fruit.w / 2, -fruit.h / 2, fruit.w, fruit.h)
  ctx.restore()
}

function drawSlicedFruit(
  ctx: CanvasRenderingContext2D,
  fruit: RenderFruit,
  image: HTMLImageElement,
): void {
  const p = fruit.sliceProgress
  const gap = p * (fruit.h * 0.4 + 26)
  const halfW = fruit.w / 2
  const halfH = fruit.h / 2

  ctx.save()
  ctx.globalAlpha = 1 - p * 0.9
  ctx.translate(fruit.x, fruit.y)
  ctx.rotate(fruit.cutAngle * DEG)

  // 벤 선을 기준으로 위/아래 조각이 서로 반대로 밀려 나간다.
  ctx.save()
  ctx.translate(0, -gap)
  ctx.rotate(-p * 10 * DEG)
  ctx.beginPath()
  ctx.rect(-halfW, -halfH, fruit.w, halfH)
  ctx.clip()
  ctx.drawImage(image, -halfW, -halfH, fruit.w, fruit.h)
  ctx.restore()

  ctx.save()
  ctx.translate(0, gap)
  ctx.rotate(p * 10 * DEG)
  ctx.beginPath()
  ctx.rect(-halfW, 0, fruit.w, halfH)
  ctx.clip()
  ctx.drawImage(image, -halfW, -halfH, fruit.w, fruit.h)
  ctx.restore()

  // 벤 직후 하얀 섬광
  if (p < 0.4) {
    ctx.globalAlpha = (1 - p * 0.9) * ((0.4 - p) / 0.4)
    ctx.fillStyle = '#ffffff'
    ctx.shadowColor = '#ffffff'
    ctx.shadowBlur = 10
    ctx.fillRect(-fruit.w * 0.6, -2.5, fruit.w * 1.2, 5)
  }
  ctx.restore()

  // 획득 점수는 회전과 무관하게 위로 떠오른다.
  ctx.save()
  ctx.globalAlpha = 1 - p
  ctx.font = "34px 'Jua', sans-serif"
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = '#ffe27a'
  ctx.shadowColor = 'rgba(0,0,0,0.45)'
  ctx.shadowBlur = 8
  ctx.shadowOffsetY = 3
  ctx.fillText(`+${fruit.gained}`, fruit.x, fruit.y - 40 - p * 46)
  ctx.restore()
}

function drawTrail(ctx: CanvasRenderingContext2D, blade: RenderBlade): void {
  const points = blade.trail
  if (points.length < 2) {
    return
  }

  ctx.save()
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = blade.fast
    ? 'rgba(214,245,106,0.65)'
    : 'rgba(255,255,255,0.4)'
  const maxWidth = blade.fast ? 18 : 10

  for (let i = 1; i < points.length; i++) {
    // 오래된 점일수록 가늘게 — 휘두른 방향이 눈에 들어온다.
    const t = i / (points.length - 1)
    ctx.lineWidth = maxWidth * (0.3 + 0.7 * t)
    ctx.beginPath()
    ctx.moveTo(points[i - 1].x, points[i - 1].y)
    ctx.lineTo(points[i].x, points[i].y)
    ctx.stroke()
  }
  ctx.restore()
}

function drawSword(ctx: CanvasRenderingContext2D, blade: RenderBlade): void {
  if (!swordImage.complete || swordImage.naturalWidth === 0) {
    return
  }

  // 이미지의 손잡이 앵커를 손목에, 칼끝 앵커를 손끝에 맞추는 유사변환.
  const handleX = SWORD_HANDLE.x * SWORD_SIZE
  const handleY = SWORD_HANDLE.y * SWORD_SIZE
  const imageVecX = (SWORD_TIP.x - SWORD_HANDLE.x) * SWORD_SIZE
  const imageVecY = (SWORD_TIP.y - SWORD_HANDLE.y) * SWORD_SIZE
  const imageLen = Math.hypot(imageVecX, imageVecY) || 1
  const targetVecX = blade.bx - blade.ax
  const targetVecY = blade.by - blade.ay
  const targetLen = Math.hypot(targetVecX, targetVecY) || 1
  const scale = Math.max(targetLen / imageLen, SWORD_MIN_SCALE)
  const rotation =
    Math.atan2(targetVecY, targetVecX) - Math.atan2(imageVecY, imageVecX)

  ctx.save()
  if (blade.fast) {
    ctx.shadowColor = 'rgba(214,245,106,0.9)'
    ctx.shadowBlur = 14
  } else {
    ctx.shadowColor = 'rgba(0,0,0,0.3)'
    ctx.shadowBlur = 6
    ctx.shadowOffsetY = 4
  }
  ctx.translate(blade.ax, blade.ay)
  ctx.rotate(rotation)
  ctx.scale(scale, scale)
  ctx.drawImage(swordImage, -handleX, -handleY, SWORD_SIZE, SWORD_SIZE)
  ctx.restore()
}

/** 한 프레임을 통째로 다시 그린다. */
export function drawScene(
  ctx: CanvasRenderingContext2D,
  fruits: RenderFruit[],
  blade: RenderBlade | null,
): void {
  ctx.clearRect(0, 0, CFG.canvasWidth, CFG.canvasHeight)

  for (const fruit of fruits) {
    const image = loadImage(fruit.imageUrl)
    if (!image.complete || image.naturalWidth === 0) {
      continue
    }
    if (fruit.sliced) {
      drawSlicedFruit(ctx, fruit, image)
    } else {
      drawWholeFruit(ctx, fruit, image)
    }
  }

  if (blade) {
    drawTrail(ctx, blade)
    drawSword(ctx, blade)
  }
}
