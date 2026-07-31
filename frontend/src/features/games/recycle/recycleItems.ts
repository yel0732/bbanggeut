import canBinUrl from '@/assets/images/recycle-game/bins/can.webp'
import glassBinUrl from '@/assets/images/recycle-game/bins/glass.webp'
import paperBinUrl from '@/assets/images/recycle-game/bins/paper.webp'
import plasticBinUrl from '@/assets/images/recycle-game/bins/plastic.webp'
import canDrinkUrl from '@/assets/images/recycle-game/items/can-drink.webp'
import canJuiceUrl from '@/assets/images/recycle-game/items/can-juice.webp'
import canSodaUrl from '@/assets/images/recycle-game/items/can-soda.webp'
import canTinUrl from '@/assets/images/recycle-game/items/can-tin.webp'
import canTunaUrl from '@/assets/images/recycle-game/items/can-tuna.webp'
import glassBottleUrl from '@/assets/images/recycle-game/items/glass-bottle.webp'
import glassBrownBottleUrl from '@/assets/images/recycle-game/items/glass-brown-bottle.webp'
import glassGreenBottleUrl from '@/assets/images/recycle-game/items/glass-green-bottle.webp'
import glassJarUrl from '@/assets/images/recycle-game/items/glass-jar.webp'
import glassMilkBottleUrl from '@/assets/images/recycle-game/items/glass-milk-bottle.webp'
import paperBagUrl from '@/assets/images/recycle-game/items/paper-bag.webp'
import paperBoxUrl from '@/assets/images/recycle-game/items/paper-box.webp'
import paperEggTrayUrl from '@/assets/images/recycle-game/items/paper-egg-tray.webp'
import paperNewspaperUrl from '@/assets/images/recycle-game/items/paper-newspaper.webp'
import paperShoppingBagUrl from '@/assets/images/recycle-game/items/paper-shopping-bag.webp'
import plasticCupUrl from '@/assets/images/recycle-game/items/plastic-cup.webp'
import plasticDetergentUrl from '@/assets/images/recycle-game/items/plastic-detergent.webp'
import plasticPumpUrl from '@/assets/images/recycle-game/items/plastic-pump.webp'
import plasticSodaBottleUrl from '@/assets/images/recycle-game/items/plastic-soda-bottle.webp'
import plasticWaterBottleUrl from '@/assets/images/recycle-game/items/plastic-water-bottle.webp'

import { RECYCLE_GAME_CONFIG as CFG } from './recycleGameConfig'

/**
 * 분리수거 쓰레기 카탈로그 (RGAME-11, RGAME-12).
 *
 * 서버의 `RECYCLING_ITEM` 테이블과 같은 정보를 담는다. 백엔드가 카탈로그를
 * 내려주기 전까지는 이 목록으로 출제한다. `code`는 서버 `item_code`와 맞춰야
 * 나중에 카탈로그를 서버에서 받아올 때 이미지 연결이 그대로 유지된다.
 *
 * 이름은 오답 안내 문구에 그대로 들어간다("{이름}은 {통 이름}에 넣으면 돼!").
 * 그래서 그림만 보고 아이가 부를 이름으로 적는다.
 */

/** 서버 `category` 코드와 같다. */
export type RecycleCategory = 'CAN' | 'PLASTIC' | 'PAPER' | 'GLASS'

export type RecycleItem = {
  code: string
  name: string
  category: RecycleCategory
  imageUrl: string
}

export type RecycleBin = {
  category: RecycleCategory
  /** 통 그림에 그려져 있는 글자. 안내 문구도 이 이름을 쓴다. */
  label: string
  imageUrl: string
  /** 통 가운데 x (캔버스 px) */
  centerX: number
}

/**
 * 분리수거 통.
 *
 * 글자·색·대표 그림이 모두 통 이미지 안에 그려져 있어 화면에서 따로 얹지
 * 않는다(RGAME-03). `label`은 통 그림에 실제로 적힌 글자와 같아야 한다 —
 * 안내 문구가 이 이름을 쓰기 때문에, 아이가 보는 글자와 듣는 말이 달라지면
 * 어디에 넣으라는 것인지 알 수 없다.
 */
export const RECYCLE_BINS: RecycleBin[] = [
  {
    category: 'CAN',
    label: '캔',
    imageUrl: canBinUrl,
    centerX: CFG.bin.centersX[0],
  },
  {
    category: 'PLASTIC',
    label: '플라스틱',
    imageUrl: plasticBinUrl,
    centerX: CFG.bin.centersX[1],
  },
  {
    category: 'PAPER',
    label: '종이',
    imageUrl: paperBinUrl,
    centerX: CFG.bin.centersX[2],
  },
  {
    category: 'GLASS',
    label: '유리',
    imageUrl: glassBinUrl,
    centerX: CFG.bin.centersX[3],
  },
]

export const RECYCLE_ITEMS: RecycleItem[] = [
  // 캔
  {
    code: 'CAN_TIN',
    name: '통조림 캔',
    category: 'CAN',
    imageUrl: canTinUrl,
  },
  {
    code: 'CAN_DRINK',
    name: '음료수 캔',
    category: 'CAN',
    imageUrl: canDrinkUrl,
  },
  {
    code: 'CAN_TUNA',
    name: '참치 캔',
    category: 'CAN',
    imageUrl: canTunaUrl,
  },
  {
    code: 'CAN_JUICE',
    name: '주스 캔',
    category: 'CAN',
    imageUrl: canJuiceUrl,
  },
  {
    code: 'CAN_SODA',
    name: '탄산음료 캔',
    category: 'CAN',
    imageUrl: canSodaUrl,
  },
  // 플라스틱
  {
    code: 'PLASTIC_WATER_BOTTLE',
    name: '물 페트병',
    category: 'PLASTIC',
    imageUrl: plasticWaterBottleUrl,
  },
  {
    code: 'PLASTIC_SODA_BOTTLE',
    name: '음료수 페트병',
    category: 'PLASTIC',
    imageUrl: plasticSodaBottleUrl,
  },
  {
    code: 'PLASTIC_CUP',
    name: '플라스틱 컵',
    category: 'PLASTIC',
    imageUrl: plasticCupUrl,
  },
  {
    code: 'PLASTIC_DETERGENT',
    name: '세제 통',
    category: 'PLASTIC',
    imageUrl: plasticDetergentUrl,
  },
  {
    code: 'PLASTIC_PUMP',
    name: '샴푸 통',
    category: 'PLASTIC',
    imageUrl: plasticPumpUrl,
  },
  // 종이
  {
    code: 'PAPER_NEWSPAPER',
    name: '신문지',
    category: 'PAPER',
    imageUrl: paperNewspaperUrl,
  },
  {
    code: 'PAPER_BOX',
    name: '종이 상자',
    category: 'PAPER',
    imageUrl: paperBoxUrl,
  },
  {
    code: 'PAPER_BAG',
    name: '종이봉투',
    category: 'PAPER',
    imageUrl: paperBagUrl,
  },
  {
    code: 'PAPER_SHOPPING_BAG',
    name: '종이 가방',
    category: 'PAPER',
    imageUrl: paperShoppingBagUrl,
  },
  {
    code: 'PAPER_EGG_TRAY',
    name: '계란판',
    category: 'PAPER',
    imageUrl: paperEggTrayUrl,
  },
  // 유리
  {
    code: 'GLASS_BOTTLE',
    name: '유리병',
    category: 'GLASS',
    imageUrl: glassBottleUrl,
  },
  {
    code: 'GLASS_JAR',
    name: '유리 잼병',
    category: 'GLASS',
    imageUrl: glassJarUrl,
  },
  {
    code: 'GLASS_MILK_BOTTLE',
    name: '우유 유리병',
    category: 'GLASS',
    imageUrl: glassMilkBottleUrl,
  },
  {
    code: 'GLASS_BROWN_BOTTLE',
    name: '갈색 유리병',
    category: 'GLASS',
    imageUrl: glassBrownBottleUrl,
  },
  {
    code: 'GLASS_GREEN_BOTTLE',
    name: '초록 유리병',
    category: 'GLASS',
    imageUrl: glassGreenBottleUrl,
  },
]

const BIN_BY_CATEGORY = new Map(
  RECYCLE_BINS.map((bin) => [bin.category, bin] as const),
)

/** 그 통이 서 있는 가운데 x. 통 위에 연출을 얹을 때 쓴다. */
export function binCenterX(category: RecycleCategory): number {
  return binFor(category).centerX
}

export function binFor(category: RecycleCategory): RecycleBin {
  const bin = BIN_BY_CATEGORY.get(category)
  if (!bin) {
    // 카테고리는 네 개로 고정이라 여기 오면 카탈로그가 잘못된 것이다.
    throw new Error(`분리수거 통을 찾지 못했습니다: ${category}`)
  }
  return bin
}

/** 오답 안내 문구 (RGAME-09). 쓰레기가 가야 할 통을 알려 준다. */
export function guideMessageFor(item: RecycleItem): string {
  return `${item.name}은 ${binFor(item.category).label} 통에 넣으면 돼!`
}

/**
 * 다음에 나올 쓰레기.
 *
 * 같은 쓰레기가 계속 나오면 아이가 배울 것이 없어, 직전 몇 개와 겹치면 다시
 * 뽑는다(RGAME-12). 무한 반복을 막기 위해 시도 횟수를 제한한다.
 */
export function pickNextItem(recentCodes: string[]): RecycleItem {
  const blocked = new Set(recentCodes.slice(-CFG.maxSameItemStreak))

  for (let attempt = 0; attempt < 8; attempt++) {
    const candidate =
      RECYCLE_ITEMS[Math.floor(Math.random() * RECYCLE_ITEMS.length)]
    if (!blocked.has(candidate.code)) {
      return candidate
    }
  }

  return RECYCLE_ITEMS[Math.floor(Math.random() * RECYCLE_ITEMS.length)]
}

/** 쓰레기 그림을 미리 디코딩해 둔다. 떠내려오는 순간 늦게 뜨지 않게 한다. */
export async function preloadRecycleImages(): Promise<void> {
  const urls = [
    ...RECYCLE_ITEMS.map((item) => item.imageUrl),
    ...RECYCLE_BINS.map((bin) => bin.imageUrl),
  ]

  await Promise.all(
    urls.map(
      (url) =>
        new Promise<void>((resolve) => {
          const image = new Image()
          image.onload = () => resolve()
          image.onerror = () => resolve()
          image.src = url
        }),
    ),
  )
}
