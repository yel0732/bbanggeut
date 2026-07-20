import { Link } from 'react-router-dom'

const childFlow = ['운동 게임', '인지 게임', '캐릭터 대화', '일기 저장']
const parentFlow = ['게임 결과', '일기 원문', '하루 코멘트', '성장 리포트']

const navigationItems = [
  { label: '아이 홈', to: '/child' },
  { label: '부모 홈', to: '/parent' },
  { label: '일기', to: '/diaries' },
  { label: '리포트', to: '/reports' },
]

export function HomePage() {
  return (
    <main className="mx-auto min-h-screen max-w-[1120px] px-5 py-14 max-[760px]:px-3.5 max-[760px]:py-8">
      <nav className="mb-10 flex flex-wrap gap-2" aria-label="주요 화면">
        {navigationItems.map((item) => (
          <Link
            className="rounded-control border-leaf-100 bg-paper text-ink-700 shadow-leaf-700/5 hover:border-leaf-300 hover:text-leaf-700 border px-4 py-2 text-sm font-bold shadow-sm transition"
            key={item.to}
            to={item.to}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <section className="grid max-w-3xl gap-5">
        <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
          Play based growth diary
        </p>
        <h1 className="text-ink-950 max-w-4xl text-6xl leading-tight font-bold tracking-normal max-[760px]:text-4xl">
          놀이로 남기는 아이 성장 기록
        </h1>
        <p className="text-ink-600 max-w-2xl text-lg">
          아이는 게임으로 놀고 캐릭터와 대화하며 하루를 남기고, 부모는 게임
          결과와 일기를 바탕으로 성장 리포트를 확인합니다.
        </p>
      </section>

      <section
        className="mt-12 grid grid-cols-2 gap-4 max-[760px]:grid-cols-1"
        aria-label="서비스 흐름"
      >
        <FlowPanel
          accentClassName="border-t-leaf-500"
          items={childFlow}
          kicker="Child"
          title="아이 화면"
        />
        <FlowPanel
          accentClassName="border-t-sun-400"
          items={parentFlow}
          kicker="Parent"
          title="부모 화면"
        />
      </section>

      <section
        className="shadow-panel rounded-control border-leaf-100 bg-paper mt-4 flex items-center justify-between gap-6 border p-7 max-[760px]:grid"
        aria-label="프론트 개발 환경"
      >
        <div>
          <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
            Frontend stack
          </p>
          <h2 className="text-ink-950 text-2xl leading-snug font-bold">
            React + TypeScript + Vite + Tailwind CSS
          </h2>
        </div>
        <p className="text-ink-600 max-w-md text-right max-[760px]:text-left">
          라우팅, 경로 alias, 환경변수 예시, 포맷터까지 초기 개발 환경을
          준비했습니다.
        </p>
      </section>
    </main>
  )
}

type FlowPanelProps = {
  accentClassName: string
  items: string[]
  kicker: string
  title: string
}

function FlowPanel({ accentClassName, items, kicker, title }: FlowPanelProps) {
  return (
    <article
      className={`shadow-panel rounded-control border-leaf-100 bg-paper grid gap-7 border border-t-[6px] p-7 ${accentClassName}`}
    >
      <div>
        <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
          {kicker}
        </p>
        <h2 className="text-ink-950 text-2xl leading-snug font-bold">
          {title}
        </h2>
      </div>
      <ol className="grid gap-3">
        {items.map((item) => (
          <li
            className="rounded-control bg-leaf-50 text-ink-800 flex min-h-12 items-center px-4 font-bold"
            key={item}
          >
            {item}
          </li>
        ))}
      </ol>
    </article>
  )
}
