import { authFetch } from '@/api/auth'
import { resolveBackendChildId } from '@/api/children'
import { apiUrl } from '@/api/config'

export type CompletedGameId = 'fruit' | 'dobble'

const BACKEND_GAME_IDS: Record<CompletedGameId, number> = {
  dobble: 7,
  fruit: 8,
}

type SaveCompletedGameBase = {
  clientPlayId: string
  selectedAt: string
  playStartedAt: string
  completedAt: string
  playDurationSeconds: number
  score: number
}

export type SaveCompletedGameInput = SaveCompletedGameBase &
  (
    | {
        gameId: 'fruit'
        fruitNinjaResult: {
          rulesVersion: string
          totalFruitCount: number
          slicedFruitCount: number
          fruitResults: {
            fruitCode: string
            generatedCount: number
            slicedCount: number
          }[]
        }
      }
    | {
        gameId: 'dobble'
        cardResults: {
          roundNo: number
          comparisonCount: number
          wrongCount: number
          status: 'COMPLETED' | 'INCOMPLETE'
          solveTimeMs: number | null
        }[]
      }
  )

export async function saveCompletedGameSession(input: SaveCompletedGameInput) {
  const childId = await resolveBackendChildId()

  const response = await authFetch(apiUrl('/api/game-sessions'), {
    body: JSON.stringify({
      childId,
      gameId: BACKEND_GAME_IDS[input.gameId],
      clientPlayId: input.clientPlayId,
      difficultyCode: null,
      selectedAt: input.selectedAt,
      playStartedAt: input.playStartedAt,
      completedAt: input.completedAt,
      playDurationSeconds: input.playDurationSeconds,
      score: input.score,
      gameType: input.gameId === 'fruit' ? 'FRUIT_NINJA' : 'DOBBLE',
      cardResults: input.gameId === 'dobble' ? input.cardResults : null,
      fruitNinjaResult:
        input.gameId === 'fruit' ? input.fruitNinjaResult : null,
    }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  })

  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as {
      message?: string
    } | null
    throw new Error(
      error?.message ??
        '게임 기록을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  return response.json() as Promise<{
    gameSessionId: string
    clientPlayId: string
    score: number
    createdAt: string
  }>
}

/** Spring LocalDateTime 요청 형식에 맞춘 현재 로컬 시각. */
export function nowAsLocalDateTime() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 23)
}
