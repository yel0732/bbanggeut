import type {
  ReportConversationSummary,
  ReportEmotionDetail,
} from '@/api/parentMode'

/**
 * 대화 요약을 화면이 그리기 좋은 모양으로 다듬는다.
 *
 * 서버가 한 문단짜리 일기에서 섹션형 리포트로 옮겨가는 중이라, 어느 쪽 응답이 와도
 * 같은 모양으로 나오게 여기서 한 번 정리한다. 화면 컴포넌트는 이 결과만 본다.
 */

export type ReportConversationView = {
  /** 오늘의 이야기 */
  story: string
  /** 아이가 표현한 감정과 이유 */
  emotions: ReportEmotionDetail[]
  /** 관심을 보인 것 */
  interests: string[]
  /** 기억에 남는 표현 — 아이가 실제로 한 말 */
  quotes: string[]
  /** 아이와 나눠보면 좋은 이야기 */
  guide: {
    title: string
    description: string
    questions: string[]
  } | null
}

function cleanList(values: readonly string[] | null | undefined): string[] {
  return (values ?? []).map((value) => value.trim()).filter(Boolean)
}

/**
 * 대화에서 실제로 건진 내용이 있는지.
 *
 * 서버는 대화가 한 건도 없어도 그날 게임 기록으로 요약 본문을 채워 보낼 때가 있다.
 * 본문만 보고 판단하면 그 게임 이야기가 대화 요약 자리에 앉는다. 그래서 대화에서만
 * 나올 수 있는 것들(감정·관심사·아이 말·주요 사건)을 기준으로 삼는다.
 */
function hasConversationContent(summary: ReportConversationSummary): boolean {
  const emotionNames = cleanList(summary.expressedEmotions)
  const emotionDetails = (summary.emotionDetails ?? []).filter((detail) =>
    detail.emotion?.trim(),
  )
  return (
    emotionDetails.length > 0 ||
    emotionNames.length > 0 ||
    cleanList(summary.interests).length > 0 ||
    cleanList(summary.notableQuotes).length > 0 ||
    cleanList(summary.mainEvents).length > 0
  )
}

/**
 * 화면에 그릴 대화 요약. 보여 줄 대화가 없으면 null이다.
 *
 * 대화 세션이 0건이거나 건진 내용이 없으면 대화가 없었던 것으로 본다. 빈 요약을
 * 띄우면 부모는 "대화를 했는데 남은 게 없다"로 읽지만, 실제로는 대화 자체가 없었던
 * 날이 대부분이다.
 */
export function toConversationView(
  summary: ReportConversationSummary | null | undefined,
): ReportConversationView | null {
  if (!summary) return null
  if (summary.sourceConversationCount <= 0) return null
  if (!hasConversationContent(summary)) return null

  // 옛 응답은 감정 이름만 준다. 까닭 자리는 비워 두고 이름만 보여 준다.
  const emotions: ReportEmotionDetail[] = (summary.emotionDetails ?? [])
    .map((detail) => ({
      emotion: detail.emotion?.trim() ?? '',
      reason: detail.reason?.trim() ?? '',
    }))
    .filter((detail) => detail.emotion)
  const fallbackEmotions = cleanList(summary.expressedEmotions).map(
    (emotion) => ({ emotion, reason: '' }),
  )

  const guide = summary.conversationGuide
  const questions = cleanList(guide?.questions)
  const description = guide?.description?.trim() ?? ''

  return {
    story: (summary.dailyStory ?? summary.diaryContent ?? '').trim(),
    emotions: emotions.length ? emotions : fallbackEmotions,
    interests: cleanList(summary.interests),
    quotes: cleanList(summary.notableQuotes),
    guide:
      description || questions.length
        ? {
            title: guide?.title?.trim() || '아이와 나눠보면 좋은 이야기',
            description,
            questions,
          }
        : null,
  }
}
