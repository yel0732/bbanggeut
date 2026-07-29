import bombUrl from '@/assets/images/fruit-game/bomb.webp'
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
const bombImage = loadImage(bombUrl)

/**
 * bomb.webp 안에서 폭탄 몸통이 차지하는 자리.
 *
 * 그림에는 도화선과 불꽃이 위로 삐져나와 있어 이미지 한가운데가 몸통 한가운데가
 * 아니다. 판정은 몸통 기준이므로 몸통 중심이 판정 좌표에 오도록 맞춰 그린다.
 * 값은 원본 알파/명도 스캔으로 잰 것이다.
 */
const BOMB_ART = {
  /** 이미지 가로/세로 비율 */
  aspect: 1083 / 1350,
  /** 몸통 중심 (이미지 가로/세로 기준 비율) */
  bodyCx: 0.444,
  bodyCy: 0.635,
  /** 몸통 반지름 (이미지 가로 기준 비율) */
  bodyR: 0.441,
} as const

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
  /** 폭탄이면 과일 그림 대신 폭탄을 그린다 */
  bomb?: boolean
}

/** 화면 전체에 얹는 연출. 과일·칼과 별개로 매 프레임 계산해 넘긴다. */
export type RenderEffects = {
  /** 피버 진행도 0..1. 0이면 안 그린다. */
  fever: number
  /** 폭발 진행도 0..1. 0이면 안 그린다. */
  blast: number
  /** 폭발 지점 (화면 px) */
  blastAt: { x: number; y: number } | null
  /** 애니메이션 위상용 시각(ms) */
  now: number
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

/**
 * 폭탄.
 *
 * 도화선 불꽃이 이미지에 이미 들어 있어 따로 그리지 않는다. 회전은 아주 조금만
 * 준다. 많이 돌면 불꽃이 아래를 향해 무엇인지 알아보기 어려워진다.
 */
function drawBomb(ctx: CanvasRenderingContext2D, fruit: RenderFruit): void {
  if (!bombImage.complete || bombImage.naturalWidth === 0) {
    return
  }

  // 몸통 반지름이 판정 반지름과 같아지도록 그림 전체 크기를 역산한다.
  const bodyRadius = Math.min(fruit.w, fruit.h) / 2
  const drawWidth = bodyRadius / BOMB_ART.bodyR
  const drawHeight = drawWidth / BOMB_ART.aspect

  ctx.save()
  ctx.translate(fruit.x, fruit.y)
  ctx.rotate(fruit.rotation * DEG)
  ctx.shadowColor = 'rgba(0,0,0,0.45)'
  ctx.shadowBlur = 14
  ctx.shadowOffsetY = 7
  ctx.drawImage(
    bombImage,
    -BOMB_ART.bodyCx * drawWidth,
    -BOMB_ART.bodyCy * drawHeight,
    drawWidth,
    drawHeight,
  )
  ctx.restore()
}

/** 피버 중 화면을 달구는 불길. 아래에서 타오르고 불티가 올라간다. */
function drawFever(
  ctx: CanvasRenderingContext2D,
  intensity: number,
  now: number,
): void {
  const { canvasHeight: H, canvasWidth: W } = CFG

  ctx.save()
  ctx.globalCompositeOperation = 'lighter'

  // 아래쪽에서 올라오는 열기
  const heat = ctx.createLinearGradient(0, H, 0, H * 0.38)
  heat.addColorStop(0, `rgba(255,92,0,${0.5 * intensity})`)
  heat.addColorStop(0.5, `rgba(255,150,20,${0.22 * intensity})`)
  heat.addColorStop(1, 'rgba(255,190,60,0)')
  ctx.fillStyle = heat
  ctx.fillRect(0, H * 0.38, W, H * 0.62)

  // 넘실거리는 불길. 사인파 두 개를 겹쳐 규칙적으로 보이지 않게 한다.
  const tongues = 9
  for (let i = 0; i < tongues; i++) {
    const x = ((i + 0.5) / tongues) * W
    const phase = now / 260 + i * 1.7
    const height =
      H * (0.16 + 0.07 * Math.sin(phase) + 0.04 * Math.sin(phase * 2.3))
    const width = W / tongues

    const flame = ctx.createLinearGradient(0, H, 0, H - height)
    flame.addColorStop(0, `rgba(255,220,120,${0.5 * intensity})`)
    flame.addColorStop(0.45, `rgba(255,120,20,${0.32 * intensity})`)
    flame.addColorStop(1, 'rgba(255,60,0,0)')
    ctx.fillStyle = flame

    ctx.beginPath()
    ctx.moveTo(x - width * 0.6, H)
    ctx.quadraticCurveTo(x - width * 0.2, H - height * 0.6, x, H - height)
    ctx.quadraticCurveTo(x + width * 0.2, H - height * 0.6, x + width * 0.6, H)
    ctx.closePath()
    ctx.fill()
  }

  // 불티 — 시간으로 위치를 계산해 상태를 들고 있지 않는다.
  const embers = 26
  for (let i = 0; i < embers; i++) {
    const seed = i * 97.13
    const life = ((now / 1000 + i * 0.37) % 2.4) / 2.4
    const x = (Math.sin(seed) * 0.5 + 0.5) * W + Math.sin(now / 400 + i) * 26
    const y = H - life * H * 0.85
    const size = (1 - life) * 4 + 1.2
    ctx.fillStyle = `rgba(255,${180 - Math.floor(life * 90)},60,${(1 - life) * 0.85 * intensity})`
    ctx.beginPath()
    ctx.arc(x, y, size, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.restore()

  // 가장자리를 붉게 눌러 화면 전체가 달아오른 느낌을 만든다.
  ctx.save()
  const vignette = ctx.createRadialGradient(
    W / 2,
    H / 2,
    H * 0.32,
    W / 2,
    H / 2,
    H * 0.86,
  )
  vignette.addColorStop(0, 'rgba(255,80,0,0)')
  vignette.addColorStop(1, `rgba(200,40,0,${0.34 * intensity})`)
  ctx.fillStyle = vignette
  ctx.fillRect(0, 0, W, H)
  ctx.restore()
}

/** 폭탄이 터진 자리. 섬광 → 불덩이 → 연기 순으로 잦아든다. */
function drawBlast(
  ctx: CanvasRenderingContext2D,
  progress: number,
  at: { x: number; y: number },
): void {
  const { canvasHeight: H, canvasWidth: W } = CFG
  const fade = 1 - progress

  ctx.save()

  // 화면 전체 섬광 — 아주 짧게 하얗게 튄다.
  if (progress < 0.18) {
    ctx.fillStyle = `rgba(255,240,210,${((0.18 - progress) / 0.18) * 0.75})`
    ctx.fillRect(0, 0, W, H)
  }

  // 불덩이
  const radius = 90 + progress * 420
  const fireball = ctx.createRadialGradient(at.x, at.y, 0, at.x, at.y, radius)
  fireball.addColorStop(0, `rgba(255,255,220,${fade * 0.95})`)
  fireball.addColorStop(0.3, `rgba(255,170,40,${fade * 0.8})`)
  fireball.addColorStop(0.65, `rgba(220,70,10,${fade * 0.5})`)
  fireball.addColorStop(1, 'rgba(90,30,10,0)')
  ctx.fillStyle = fireball
  ctx.beginPath()
  ctx.arc(at.x, at.y, radius, 0, Math.PI * 2)
  ctx.fill()

  // 퍼져 나가는 충격파 고리
  ctx.strokeStyle = `rgba(255,220,150,${fade * 0.65})`
  ctx.lineWidth = 10 * fade + 2
  ctx.beginPath()
  ctx.arc(at.x, at.y, 60 + progress * 520, 0, Math.PI * 2)
  ctx.stroke()

  // 사방으로 튀는 파편
  const shards = 14
  for (let i = 0; i < shards; i++) {
    const angle = (i / shards) * Math.PI * 2 + 0.3
    const distance = 70 + progress * 400
    const x = at.x + Math.cos(angle) * distance
    const y = at.y + Math.sin(angle) * distance + progress * progress * 120
    ctx.fillStyle = `rgba(60,60,66,${fade * 0.8})`
    ctx.beginPath()
    ctx.arc(x, y, 9 * fade + 2, 0, Math.PI * 2)
    ctx.fill()
  }

  ctx.restore()
}

/** 한 프레임을 통째로 다시 그린다. */
export function drawScene(
  ctx: CanvasRenderingContext2D,
  fruits: RenderFruit[],
  blade: RenderBlade | null,
  effects?: RenderEffects,
): void {
  ctx.clearRect(0, 0, CFG.canvasWidth, CFG.canvasHeight)

  const now = effects?.now ?? 0

  // 불길은 과일 뒤에 깔아야 과일이 불 속에서 날아오는 것처럼 보인다.
  if (effects && effects.fever > 0) {
    drawFever(ctx, effects.fever, now)
  }

  for (const fruit of fruits) {
    if (fruit.bomb) {
      // 벤 폭탄은 폭발 연출이 대신하므로 그리지 않는다.
      if (!fruit.sliced) {
        drawBomb(ctx, fruit)
      }
      continue
    }
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

  if (effects && effects.blast > 0 && effects.blastAt) {
    drawBlast(ctx, effects.blast, effects.blastAt)
  }
}
