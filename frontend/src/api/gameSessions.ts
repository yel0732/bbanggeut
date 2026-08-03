import { authFetch } from '@/api/auth'
import { resolveBackendChildId } from '@/api/children'
import { apiUrl } from '@/api/config'

export type CompletedGameId = 'fruit' | 'dobble' | 'recycle' | 'wordmatch'

/**
 * 서버의 `game` 테이블 id.
 *
 * 낱말 맞추기 명세 문서는 WORD_MATCH를 9, RECYCLING을 10으로 바꾸자고 적었지만,
 * 실제 마이그레이션은 RECYCLING을 9로 두고 WORD_MATCH를 10으로 넣었다
 * (V10, V12). 문서가 아니라 DB를 따른다.
 */
const BACKEND_GAME_IDS: Record<CompletedGameId, number> = {
  dobble: 7,
  fruit: 8,
  recycle: 9,
  wordmatch: 10,
}

const BACKEND_GAME_TYPES: Record<CompletedGameId, string> = {
  dobble: 'DOBBLE',
  fruit: 'FRUIT_NINJA',
  recycle: 'RECYCLING',
  wordmatch: 'WORD_MATCH',
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
    | {
        gameId: 'recycle'
        recyclingResult: {
          rulesVersion: string
          successfulCount: number
          wrongCount: number
        }
      }
    | {
        gameId: 'wordmatch'
        /**
         * 필드 이름은 서버 DTO(`SaveWordMatchResultRequest`)를 따른다.
         *
         * 명세 문서는 `roundResults`/`roundNo`/`problemId`로 적었지만 실제
         * 서버는 `problemResults`/`questionOrder`/`problemCode`를 받는다.
         */
        wordMatchResult: {
          rulesVersion: string
          solvedWordCount: number
          incompleteWordCount: number
          wrongCount: number
          hintUsedCount: number
          problemResults: {
            problemCode: string
            questionOrder: number
            wordLength: number
            status: 'COMPLETED' | 'INCOMPLETE'
            wrongCount: number
            hintUsed: boolean
            earnedScore: number
            solveTimeMs: number | null
          }[]
        }
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
      gameType: BACKEND_GAME_TYPES[input.gameId],
      cardResults: input.gameId === 'dobble' ? input.cardResults : null,
      fruitNinjaResult:
        input.gameId === 'fruit' ? input.fruitNinjaResult : null,
      recyclingResult:
        input.gameId === 'recycle' ? input.recyclingResult : null,
      wordMatchResult:
        input.gameId === 'wordmatch' ? input.wordMatchResult : null,
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
