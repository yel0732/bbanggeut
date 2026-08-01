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
            dailyStory:
              '오늘 아이는 친구와 블록 성을 만든 이야기를 들려주었어요.',
            mainEvents: ['친구와 함께 놀이함'],
            expressedEmotions: ['즐거움'],
            emotionDetails: [
              { emotion: '속상함', reason: '처음 만든 탑이 무너져서' },
              { emotion: '뿌듯함', reason: '친구와 다시 쌓아 올려서' },
            ],
            interests: ['블록', '친구와 함께 만들기'],
            notableQuotes: ['민수가 밑을 잡아줘서 다시 만들었어.'],
            conversationGuide: {
              title: '아이와 나눠보면 좋은 이야기',
              description:
                '친구와 함께 다시 도전했던 경험에 관심을 보여주세요.',
              questions: [
                '친구가 도와줬을 때 어떤 기분이 들었어?',
                '다음에는 친구와 무엇을 만들고 싶어?',
              ],
            },
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
