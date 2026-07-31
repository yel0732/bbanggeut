import dobbleCardUrl from '@/assets/images/game-entry/dobble-card.webp'
import fruitCardUrl from '@/assets/images/game-entry/fruit-card.webp'
import recycleCardUrl from '@/assets/images/game-entry/recycle-card.webp'
import wordmatchCardUrl from '@/assets/images/game-entry/wordmatch-card.webp'

export type GameId = 'fruit' | 'dobble' | 'wordmatch' | 'recycle'

/** 게임 선택 화면 페이지. 1은 `/games`, 2는 `/games/more`. */
export type GamePage = 1 | 2

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
  /** 실제 플레이 화면 구현 여부. false면 플레이 진입 시 게임 방법으로 돌린다. */
  playable: boolean
  /** 몇 번째 게임 선택 화면에 놓을지 */
  page: GamePage
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
    positionClassName: 'top-[93px] left-[154px]',
    playable: true,
    page: 1,
  },
  {
    id: 'dobble',
    label: '그림 맞추기',
    tagline: '같은 그림을 찾아 반짝이는 카드를 맞춰요.',
    howTo:
      '두 카드에 똑같이 있는 그림은 하나예요. 왼손과 오른손으로 각각 그 그림을 짚고 잠깐 멈추면 정답!',
    cardImageUrl: dobbleCardUrl,
    floatClassName: '[animation-delay:-1.7s]',
    positionClassName: 'top-[93px] right-[154px]',
    playable: true,
    page: 1,
  },
  // 아래 두 게임은 배경·BGM·HUD 자리만 잡은 플레이 화면이 있다. 놀이판과
  // 판정은 아직 없어서 게임 방법의 인식·실습 단계도 안내만 보여 준다.
  {
    id: 'wordmatch',
    label: '낱말 맞추기',
    tagline: '그림을 보고 알맞은 낱말을 찾아요.',
    howTo: '그림이 무엇인지 보고, 어울리는 낱말을 골라 보세요!',
    cardImageUrl: wordmatchCardUrl,
    floatClassName: '[animation-delay:-0.4s]',
    positionClassName: 'top-[93px] left-[154px]',
    playable: true,
    page: 2,
  },
  {
    id: 'recycle',
    label: '숲 속 청소하기',
    tagline: '숲에 떨어진 쓰레기를 알맞은 통에 담아요.',
    howTo: '쓰레기를 하나씩 집어서 어울리는 통에 쏙 넣어 보세요!',
    cardImageUrl: recycleCardUrl,
    floatClassName: '[animation-delay:-1.7s]',
    positionClassName: 'top-[93px] right-[154px]',
    playable: true,
    page: 2,
  },
]

export function getGameById(id: string | undefined): GameInfo | undefined {
  return GAMES.find((game) => game.id === id)
}

export function getGamesByPage(page: GamePage): GameInfo[] {
  return GAMES.filter((game) => game.page === page)
}
