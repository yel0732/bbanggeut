import type { ReportDetailResponse, ReportGameSummary } from '@/api/parentMode'

/**
 * 화면에 내보일 게임 기록을 고른다.
 *
 * 묶는 일은 서버가 한다. 다만 묶어 보내기 전 서버가 아직 붙어 있을 수 있어,
 * gameSummaries가 없으면 회차별 기록을 같은 모양으로 옮겨 놓는다. 그래야 서버가
 * 바뀌는 사이에도 부모 화면에서 게임 칸이 통째로 비지 않는다.
 *
 * 옛 응답에는 몇 판 했는지가 없다. 세어서 지어내지 않고 1로 두어, 화면이 없는
 * 사실을 만들어 내지 않게 한다 — 같은 게임을 두 번 한 날은 첫 판만 남는다.
 */
export function toGameSummaries(
  report: ReportDetailResponse,
): ReportGameSummary[] {
  if (report.gameSummaries?.length) return report.gameSummaries

  return report.gameResults.map((game) => ({
    gameType: game.gameType,
    gameName: game.gameName,
    playCount: 1,
    totalPlayDurationSeconds: game.playDurationSeconds,
    highestScore: game.score,
    resultMetrics: game.resultMetrics,
  }))
}
