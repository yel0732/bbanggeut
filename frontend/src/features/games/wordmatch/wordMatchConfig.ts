export const WORD_MATCH_CONFIG = {
  width: 1280,
  height: 720,
  aimMs: 300,
  durationSec: 90,
  countdownSec: 3,
  cardSize: 136,
  trayLeft: 141,
  trayTop: 483,
  trayGap: 11,
  slotY: 325,
} as const

export type WordMatchProblem = {
  answer: string
  cards: string[]
  imageUrl: string
  label: string
}

export const WORD_MATCH_PRACTICE: WordMatchProblem = {
  answer: '사과',
  cards: ['사', '과', '나', '비', '자', '차', '가'],
  imageUrl: appleUrl,
  label: '사과',
}

export function cardCenter(index: number): { x: number; y: number } {
  const { cardSize, trayGap, trayLeft, trayTop } = WORD_MATCH_CONFIG

  return {
    x: trayLeft + cardSize / 2 + index * (cardSize + trayGap),
    y: trayTop + cardSize / 2,
  }
}

export function slotCenter(
  index: number,
  slotCount: number,
): { x: number; y: number } {
  const gap = 147
  const first = 642 - ((slotCount - 1) * gap) / 2
  return { x: first + index * gap, y: WORD_MATCH_CONFIG.slotY }
}
import appleUrl from '@/assets/images/fruit-game/apple.webp'
