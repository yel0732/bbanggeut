import type { ReportConversationView } from './conversationSummary'

/**
 * 대화 요약 섹션들.
 *
 * 부모 리포트는 두 곳에서 그려진다 — 목록 안 종이 화면과 리포트 상세 화면. 항목이
 * 늘어날수록 두 곳을 따로 고치면 어긋나므로 본문은 여기 하나로 둔다. 자리가 좁은
 * 종이 쪽은 `compact`로 글씨만 줄인다.
 *
 * 값이 없는 섹션은 그리지 않는다. "기록 없음"이 늘어서면 리포트가 비어 보인다.
 */
export function ConversationSummaryView({
  compact = false,
  view,
}: {
  compact?: boolean
  view: ReportConversationView
}) {
  const body = compact ? 'text-[15px] leading-6' : 'text-[17px] leading-7'
  const chip = compact ? 'text-[14px]' : 'text-[16px]'
  const note = compact ? 'text-[12px]' : 'text-[13px]'

  return (
    <div className={compact ? 'space-y-4' : 'space-y-6'}>
      {view.story ? (
        <Section compact={compact} label="오늘의 이야기">
          <p className={`${body} whitespace-pre-line`}>{view.story}</p>
        </Section>
      ) : null}

      {view.emotions.length ? (
        <Section compact={compact} label="아이가 표현한 감정과 이유">
          <ul className={body}>
            {view.emotions.map((detail) => (
              <li key={detail.emotion}>
                <span className="text-[#55783b]">{detail.emotion}</span>
                {detail.reason ? ` — ${detail.reason}` : ''}
              </li>
            ))}
          </ul>
        </Section>
      ) : null}

      {view.interests.length ? (
        <Section compact={compact} label="관심을 보인 것">
          <div className="flex flex-wrap gap-1.5">
            {view.interests.map((interest) => (
              <span
                className={`rounded-full bg-[#e4eed0] px-3 py-1 ${chip}`}
                key={interest}
              >
                {interest}
              </span>
            ))}
          </div>
        </Section>
      ) : null}

      {/*
        기억에 남는 표현.

        아이가 실제로 한 말이라는 것이 드러나야 한다. 따옴표로 감싸고 누가 한 말인지
        밝힌다 — AI가 지어낸 문장으로 읽히면 리포트를 믿을 수 없게 된다.
      */}
      {view.quotes.length ? (
        <Section compact={compact} label="기억에 남는 표현">
          <ul className={`${body} space-y-1`}>
            {view.quotes.map((quote) => (
              <li
                className="border-l-4 border-[#cddba9] pl-3 text-[#5a4a2a] italic"
                key={quote}
              >
                “{quote}”
                <span className={`ml-2 not-italic ${note} text-[#95876b]`}>
                  아이가 한 말
                </span>
              </li>
            ))}
          </ul>
        </Section>
      ) : null}

      {view.guide ? (
        <Section compact={compact} label="아이와 나눠보면 좋은 이야기">
          {view.guide.description ? (
            <p className={body}>{view.guide.description}</p>
          ) : null}
          {view.guide.questions.length ? (
            <ul className={`${body} mt-1 space-y-1`}>
              {view.guide.questions.map((question) => (
                <li key={question}>• {question}</li>
              ))}
            </ul>
          ) : null}
        </Section>
      ) : null}
    </div>
  )
}

function Section({
  children,
  compact,
  label,
}: {
  children: React.ReactNode
  compact: boolean
  label: string
}) {
  return (
    <section>
      <h4
        className={`${compact ? 'text-[17px]' : 'text-[19px]'} text-[#55783b]`}
      >
        {label}
      </h4>
      <div className="mt-1.5 text-[#675636]">{children}</div>
    </section>
  )
}
