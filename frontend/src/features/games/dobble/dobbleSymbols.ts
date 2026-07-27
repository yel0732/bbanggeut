import bearUrl from '@/assets/images/dobble-game/bear.webp'
import chickUrl from '@/assets/images/dobble-game/chick.webp'
import deerUrl from '@/assets/images/dobble-game/deer.webp'
import dinosaurUrl from '@/assets/images/dobble-game/dinosaur.webp'
import dogUrl from '@/assets/images/dobble-game/dog.webp'
import elephantUrl from '@/assets/images/dobble-game/elephant.webp'
import foxUrl from '@/assets/images/dobble-game/fox.webp'
import frogUrl from '@/assets/images/dobble-game/frog.webp'
import hedgehogUrl from '@/assets/images/dobble-game/hedgehog.webp'
import koalaUrl from '@/assets/images/dobble-game/koala.webp'
import monkeyUrl from '@/assets/images/dobble-game/monkey.webp'
import otterUrl from '@/assets/images/dobble-game/otter.webp'
import pandaUrl from '@/assets/images/dobble-game/panda.webp'
import pangolinUrl from '@/assets/images/dobble-game/pangolin.webp'
import penguinUrl from '@/assets/images/dobble-game/penguin.webp'
import pigUrl from '@/assets/images/dobble-game/pig.webp'
import rabbitUrl from '@/assets/images/dobble-game/rabbit.webp'
import sheepUrl from '@/assets/images/dobble-game/sheep.webp'
import squirrelUrl from '@/assets/images/dobble-game/squirrel.webp'
import tigerUrl from '@/assets/images/dobble-game/tiger.webp'

/**
 * 도블(그림 맞추기) 게임 심볼 카탈로그.
 *
 * 원본 PNG(장당 0.7~1.6MB)를 투명 여백 트림 → 최대 변 256px → WebP(q82)로
 * 최적화해 장당 9~15KB, 20장 합계 약 240KB로 줄였다. 카드에 표시되는 최대
 * 크기(약 132px * 캔버스 스케일 * DPR)를 커버하는 해상도라 확대 시에도
 * 흐려지지 않는다.
 *
 * `aspect`는 트림 후 실제 가로/세로 비율이다. 카드 배치에서 이 값으로 폭·높이를
 * 계산하므로 렌더 시 이미지 로드 전에도 레이아웃이 흔들리지 않는다.
 */
export type DobbleSymbol = {
  id: string
  /** 아이 화면 대체 텍스트에 쓰는 이름 */
  name: string
  imageUrl: string
  /** 트림 후 가로/세로 비율 (w / h) */
  aspect: number
}

export const DOBBLE_SYMBOLS: DobbleSymbol[] = [
  { id: 'bear', name: '곰', imageUrl: bearUrl, aspect: 0.746 },
  { id: 'chick', name: '병아리', imageUrl: chickUrl, aspect: 0.723 },
  { id: 'deer', name: '아기사슴', imageUrl: deerUrl, aspect: 0.879 },
  { id: 'dinosaur', name: '공룡', imageUrl: dinosaurUrl, aspect: 1.053 },
  { id: 'dog', name: '강아지', imageUrl: dogUrl, aspect: 1.243 },
  { id: 'elephant', name: '코끼리', imageUrl: elephantUrl, aspect: 1.267 },
  { id: 'fox', name: '여우', imageUrl: foxUrl, aspect: 1.231 },
  { id: 'frog', name: '개구리', imageUrl: frogUrl, aspect: 1.274 },
  { id: 'hedgehog', name: '고슴도치', imageUrl: hedgehogUrl, aspect: 1.267 },
  { id: 'koala', name: '코알라', imageUrl: koalaUrl, aspect: 1.067 },
  { id: 'monkey', name: '원숭이', imageUrl: monkeyUrl, aspect: 0.887 },
  { id: 'otter', name: '수달', imageUrl: otterUrl, aspect: 0.84 },
  { id: 'panda', name: '판다', imageUrl: pandaUrl, aspect: 0.797 },
  { id: 'pangolin', name: '천산갑', imageUrl: pangolinUrl, aspect: 1.376 },
  { id: 'penguin', name: '펭귄', imageUrl: penguinUrl, aspect: 0.824 },
  { id: 'pig', name: '돼지', imageUrl: pigUrl, aspect: 1.158 },
  { id: 'rabbit', name: '토끼', imageUrl: rabbitUrl, aspect: 0.563 },
  { id: 'sheep', name: '양', imageUrl: sheepUrl, aspect: 1.024 },
  { id: 'squirrel', name: '다람쥐', imageUrl: squirrelUrl, aspect: 0.68 },
  { id: 'tiger', name: '호랑이', imageUrl: tigerUrl, aspect: 1.118 },
]

const SYMBOL_BY_ID = new Map(
  DOBBLE_SYMBOLS.map((symbol) => [symbol.id, symbol]),
)

export function getDobbleSymbol(id: string): DobbleSymbol | undefined {
  return SYMBOL_BY_ID.get(id)
}

/**
 * 심볼 이미지를 미리 디코딩한다.
 *
 * 라운드가 바뀔 때 새 이미지가 늦게 뜨면 아이가 빈 카드를 보게 되므로 게임
 * 진입 시(안내 화면 포함) 20장을 모두 디코딩까지 끝내 둔다. 총 240KB로 가볍다.
 */
export function preloadDobbleSymbols(): Promise<void> {
  return Promise.all(
    DOBBLE_SYMBOLS.map(async (symbol) => {
      const image = new Image()
      image.src = symbol.imageUrl
      try {
        await image.decode()
      } catch {
        // 디코딩 실패는 렌더를 막지 않는다. <img>가 다시 로드한다.
      }
    }),
  ).then(() => undefined)
}
