import { authFetch } from '@/api/auth'
import { apiUrl } from '@/api/config'
import {
  loadActiveChildProfileId,
  loadChildProfiles,
} from '@/lib/childProfiles'

export type CompletedGameId = 'fruit' | 'dobble'

export type FruitNinjaRule = {
  rulesVersion: string
  plannedDurationSeconds: number
  sliceSpeedThreshold: number
  fruitSpawnIntervalMs: number
  maxVisibleFruit: number
  fruits: {
    fruitCode: string
    name: string
    sizePx: number
    score: number
  }[]
}

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

export async function saveCompletedGameSession(
  input: SaveCompletedGameInput,
) {
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
    const error = (await response
      .json()
      .catch(() => null)) as { message?: string } | null
    throw new Error(
      error?.message ?? '게임 기록을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  return response.json() as Promise<{
    gameSessionId: string
    clientPlayId: string
    score: number
    createdAt: string
  }>
}

async function resolveBackendChildId() {
  const activeProfileId = loadActiveChildProfileId()
  const numericId = Number(activeProfileId)
  if (Number.isSafeInteger(numericId) && numericId > 0) return numericId

  const activeProfile = loadChildProfiles().find(
    (profile) => profile.id === activeProfileId,
  )
  if (!activeProfile) {
    throw new Error('게임 기록을 저장할 아이 정보를 찾지 못했어요.')
  }

  const response = await authFetch(apiUrl('/api/children'))
  if (!response.ok) {
    throw new Error('아이 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
  }

  const children = (await response.json()) as {
    childId: number
    name: string
  }[]
  const matches = children.filter((child) => child.name === activeProfile.name)
  if (matches.length !== 1) {
    throw new Error('선택한 아이와 서버의 아이 정보를 연결하지 못했어요.')
  }
  return matches[0].childId
}

export async function getActiveFruitNinjaRule() {
  const response = await authFetch(
    apiUrl('/api/games/fruit-ninja/rules/active'),
  )
  if (!response.ok) {
    throw new Error('과일 게임 규칙을 불러오지 못했어요.')
  }
  return response.json() as Promise<FruitNinjaRule>
}

/** Spring LocalDateTime 요청 형식에 맞춘 현재 로컬 시각. */
export function nowAsLocalDateTime() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 23)
}
