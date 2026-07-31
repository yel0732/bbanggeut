import { Navigate } from 'react-router-dom'

import bgmUrl from '@/assets/audio/wordmatch-game/alphabet-gather.mp3'
import backgroundUrl from '@/assets/images/wordmatch-game/background.webp'
import { getGameById } from '@/features/games/games'
import { UpcomingGamePlay } from '@/features/games/upcoming/UpcomingGamePlay'

/**
 * 낱말 맞추기 플레이 화면.
 *
 * 배경·BGM·HUD 자리만 잡아 둔 상태다. 놀이판과 판정은 UpcomingGamePlay 안의
 * 안내 블록을 바꿔 채운다.
 */
export function WordMatchPlayPage() {
  const game = getGameById('wordmatch')

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
