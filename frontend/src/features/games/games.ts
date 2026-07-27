import dobbleCardUrl from '@/assets/images/game-entry/dobble-card.webp'
import fruitCardUrl from '@/assets/images/game-entry/fruit-card.webp'

export type GameId = 'fruit' | 'dobble'

export type GameInfo = {
  id: GameId
  /** 카드/헤더에 노출되는 게임 이름 */
  label: string
  /** 게임 선택 화면 말풍선용 한 줄 소개 */
  tagline: string
  /** 게임 방법 화면 설명 문구 */
  howTo: string
  cardImageUrl: string
  /** 게임 선택 화면 카드 애니메이션 지연 */
  floatClassName: string
  /** 게임 선택 화면 카드 배치 */
  positionClassName: string
  /** 실제 플레이 화면 구현 여부 */
  playable: boolean
}

export const GAMES: GameInfo[] = [
  {
    id: 'fruit',
    label: '과일 자르기',
    tagline: '날아오는 과일을 손으로 슥슥 잘라요.',
    howTo:
      '손을 화면에 보여 주면 카메라가 손을 따라가요. 날아오는 과일 위로 손을 빠르게 휘저어 슝슝 잘라 보세요!',
    cardImageUrl: fruitCardUrl,
    floatClassName: '[animation-delay:-0.4s]',
    positionClassName: 'top-[78px] left-[154px]',
    playable: true,
  },
  {
    id: 'dobble',
    label: '그림 맞추기',
    tagline: '같은 그림을 찾아 반짝이는 카드를 맞춰요.',
    howTo:
      '두 카드에 똑같이 있는 그림은 하나예요. 왼손과 오른손으로 각각 그 그림을 짚고 잠깐 멈추면 정답!',
    cardImageUrl: dobbleCardUrl,
    floatClassName: '[animation-delay:-1.7s]',
    positionClassName: 'top-[78px] right-[154px]',
    playable: true,
  },
]

export function getGameById(id: string | undefined): GameInfo | undefined {
  return GAMES.find((game) => game.id === id)
}
