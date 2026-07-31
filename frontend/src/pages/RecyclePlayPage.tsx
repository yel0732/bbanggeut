import { Navigate } from 'react-router-dom'

// 이 게임 전용 BGM은 아직 없다. 같은 유아 노래인 낱말 맞추기 곡을 임시로 쓴다.
// 전용 곡이 생기면 이 import만 바꾸면 된다.
import bgmUrl from '@/assets/audio/wordmatch-game/alphabet-gather.mp3'
import backgroundUrl from '@/assets/images/recycle-game/background.webp'
import { getGameById } from '@/features/games/games'
import { UpcomingGamePlay } from '@/features/games/upcoming/UpcomingGamePlay'

/**
 * 숲 속 청소하기 플레이 화면.
 *
 * 배경·BGM·HUD 자리만 잡아 둔 상태다. 놀이판과 판정은 UpcomingGamePlay 안의
 * 안내 블록을 바꿔 채운다.
 */
export function RecyclePlayPage() {
  const game = getGameById('recycle')

  if (!game) {
    return <Navigate replace to="/games" />
  }

  return (
    <UpcomingGamePlay
      backgroundUrl={backgroundUrl}
      bgmUrl={bgmUrl}
      game={game}
    />
  )
}
