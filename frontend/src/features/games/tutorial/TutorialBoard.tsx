import type { ReactNode } from 'react'

/**
 * 튜토리얼 공용 껍데기.
 *
 * 나무 보드 안쪽을 제목 / 진행 점 / 본문 / 버튼 4단으로 나눈다. 단계마다
 * 달라지는 것은 본문과 버튼뿐이라 나머지는 여기서 한 번만 그린다.
 *
 * 말풍선은 이미지 에셋 대신 CSS로 그린다. 문구 길이가 단계마다 달라서 고정
 * 비율 이미지에 넣으면 글자가 넘치거나 여백이 남는다.
 */

const jua = { fontFamily: "'Jua', sans-serif" } as const

/**
 * 보드 안쪽(테두리 장식을 뺀 영역) 높이(px).
 *
 * 나무 보드 이미지 크기와 아래 inset 비율에서 나온 값이다. 본문 높이를 px로
 * 계산해 넘기려면 이 값이 필요하다.
 */
const CONTENT_HEIGHT = 562
const TITLE_HEIGHT = 44
const DOTS_BLOCK_HEIGHT = 20
const FOOTER_HEIGHT = 84
/** 제목·점·본문·버튼 사이 간격 합 */
const GAP_TOTAL = 20

export function TutorialBoard({
  body,
  footer,
  showTitle,
  stepCount,
  stepIndex,
}: {
  body: ReactNode
  footer: ReactNode
  /** 소개·시작 단계에만 제목을 둔다. 나머지는 그 자리를 본문에 넘긴다. */
  showTitle: boolean
  stepCount: number
  stepIndex: number
}) {
  // 본문 높이를 px로 못박는다. flex-1로만 두면 안쪽 캔버스의 `height: 100%`가
  // 기댈 확정 높이가 없어 캔버스가 고유 크기(720px)로 펼쳐지고 보드를 뚫는다.
  const bodyHeight =
    CONTENT_HEIGHT -
    (showTitle ? TITLE_HEIGHT : 0) -
    DOTS_BLOCK_HEIGHT -
    FOOTER_HEIGHT -
    GAP_TOTAL

  return (
    <div className="absolute top-[12%] right-[11%] bottom-[9%] left-[11%] flex flex-col items-center">
      {showTitle ? (
        <h2
          className="text-[36px] leading-none text-[#6f4a1f] [text-shadow:0_2px_0_rgba(255,255,255,0.5)]"
          style={jua}
        >
          게임 방법
        </h2>
      ) : null}

      <StepDots current={stepIndex} total={stepCount} />

      <div
        className="mt-2 flex w-full items-center justify-center overflow-hidden"
        style={{ height: bodyHeight }}
      >
        {body}
      </div>

      {/* 버튼 배치는 단계마다 다르다(가운데 모으기 / 좌우로 벌리기). 자리만 준다. */}
      <div
        className="mt-3 flex w-full items-center"
        style={{ height: FOOTER_HEIGHT }}
      >
        {footer}
      </div>
    </div>
  )
}

/** 몇 번째 단계인지 알려 주는 점. 글자를 못 읽는 아이도 진행도를 본다. */
function StepDots({ current, total }: { current: number; total: number }) {
  return (
    <div className="mt-2 flex items-center gap-2">
      {Array.from({ length: total }, (_, index) => (
        <span
          className="block rounded-full transition-all"
          key={index}
          style={{
            width: index === current ? 26 : 12,
            height: 12,
            background: index <= current ? '#6cbc3f' : '#d8c39d',
            boxShadow:
              index === current ? '0 2px 5px rgba(60,110,30,0.45)' : 'none',
          }}
        />
      ))}
    </div>
  )
}

/**
 * 빵긋 말풍선.
 *
 * `side`는 빵긋이 말풍선의 어느 쪽에 서 있는지다. 꼬리가 빵긋 쪽을 향해야
 * 누가 말하는지 헷갈리지 않는다.
 *
 * `break-keep`(word-break: keep-all)이 핵심이다. 기본값이면 한글이 글자 단위로
 * 끊겨 "올라오는 과일" 이 "올라오는 과" / "일" 처럼 갈라진다. keep-all이면 띄어
 * 쓰기에서만 줄이 바뀐다.
 */
export function SpeechBubble({
  side = 'left',
  size = 'large',
  speech,
}: {
  side?: 'left' | 'right' | 'top'
  size?: 'large' | 'small'
  speech: string
}) {
  const large = size === 'large'

  return (
    <div className="relative">
      <div
        className="rounded-[26px] border-[3px] border-[#e2caa0] bg-white/92 text-center break-keep whitespace-pre-line text-[#6f4a1f] shadow-[0_6px_14px_rgba(120,80,30,0.18)]"
        style={{
          ...jua,
          padding: large ? '20px 30px' : '14px 24px',
          fontSize: large ? 32 : 26,
          lineHeight: 1.4,
        }}
      >
        {speech}
      </div>
      {/* 꼬리 — 테두리 색 삼각형 위에 흰 삼각형을 겹쳐 테두리처럼 보이게 한다. */}
      {side === 'left' ? (
        <>
          <span className="absolute top-1/2 -left-[14px] size-0 -translate-y-1/2 border-y-[12px] border-r-[14px] border-y-transparent border-r-[#e2caa0]" />
          <span className="absolute top-1/2 -left-[9px] size-0 -translate-y-1/2 border-y-[9px] border-r-[11px] border-y-transparent border-r-white/92" />
        </>
      ) : null}
      {side === 'right' ? (
        <>
          <span className="absolute top-1/2 -right-[14px] size-0 -translate-y-1/2 border-y-[12px] border-l-[14px] border-y-transparent border-l-[#e2caa0]" />
          <span className="absolute top-1/2 -right-[9px] size-0 -translate-y-1/2 border-y-[9px] border-l-[11px] border-y-transparent border-l-white/92" />
        </>
      ) : null}
      {side === 'top' ? (
        <>
          <span className="absolute -bottom-[14px] left-1/2 size-0 -translate-x-1/2 border-x-[12px] border-t-[14px] border-x-transparent border-t-[#e2caa0]" />
          <span className="absolute -bottom-[9px] left-1/2 size-0 -translate-x-1/2 border-x-[9px] border-t-[11px] border-x-transparent border-t-white/92" />
        </>
      ) : null}
    </div>
  )
}

/** 데모·실습이 들어가는 패널. 도블 미리보기와 같은 테두리를 쓴다. */
export function TutorialPanel({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-full min-h-0 w-full flex-col items-center justify-center gap-2 overflow-hidden rounded-[24px] border-[3px] border-dashed border-[#c39a5d] bg-[#fffaec]/70 p-3 shadow-[inset_0_2px_10px_rgba(120,80,30,0.12)]">
      {children}
    </div>
  )
}
