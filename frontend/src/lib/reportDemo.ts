import type { ReportDetailResponse } from '@/api/parentMode'

export function makeDemoReportDetail(date: string): ReportDetailResponse {
  const failed = date.endsWith('-19')
  const gameOnly = date.endsWith('-21')
  const conversationOnly = date.endsWith('-22')
  return {
    reportId: Number(date.slice(-2)) || 1,
    childId: 1,
    reportDate: date,
    aggregationPeriod: {
      startedAt: `${date}T00:00:00`,
      endedAt: `${date}T21:00:00`,
      timezone: 'Asia/Seoul',
    },
    status: failed ? 'FAILED' : 'COMPLETED',
    dataAvailability: {
      hasGameData: !failed && !conversationOnly,
      hasConversationData: !failed && !gameOnly,
    },
    gameResults:
      !failed && !conversationOnly
        ? [
            {
              gameSessionId: `demo-${date}`,
              gameType: 'DOBBLE',
              gameName: '그림 맞추기',
              playDurationSeconds: 120,
              score: 85,
              resultMetrics: { solvedCardCount: 7 },
              completedAt: `${date}T18:00:00`,
            },
          ]
        : [],
    conversationSummary:
      !failed && !gameOnly
        ? {
            diaryTitle: '즐거운 하루',
            diaryContent:
              '오늘은 친구와 재미있는 놀이를 하며 즐거운 시간을 보냈어요.',
            mainEvents: ['친구와 함께 놀이함'],
            expressedEmotions: ['즐거움'],
            interests: ['카드 게임'],
            sourceConversationCount: 1,
            generatedAt: `${date}T21:00:00`,
          }
        : null,
    failure: failed
      ? {
          code: 'GENERATION_FAILED',
          message: '리포트를 생성하지 못했어요. 다시 생성해 주세요.',
          retryCount: 3,
          canRegenerate: true,
          failedAt: `${date}T21:00:00`,
        }
      : null,
    generatedAt: failed ? null : `${date}T21:00:00`,
    createdAt: `${date}T21:00:00`,
    updatedAt: `${date}T21:00:00`,
  }
}
