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
            className="rounded-lg border border-emerald-100 bg-white px-4 py-2 text-sm font-bold text-slate-700 shadow-sm shadow-emerald-950/5 transition hover:border-emerald-300 hover:text-emerald-700"
            key={item.to}
            to={item.to}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <section className="grid max-w-3xl gap-5">
        <p className="text-sm font-bold tracking-normal text-emerald-700 uppercase">
          Play based growth diary
        </p>
        <h1 className="max-w-4xl text-6xl leading-tight font-bold tracking-normal text-slate-950 max-[760px]:text-4xl">
          놀이로 남기는 아이 성장 기록
        </h1>
        <p className="max-w-2xl text-lg text-slate-600">
          아이는 게임으로 놀고 캐릭터와 대화하며 하루를 남기고, 부모는 게임
          결과와 일기를 바탕으로 성장 리포트를 확인합니다.
        </p>
      </section>

      <section
        className="mt-12 grid grid-cols-2 gap-4 max-[760px]:grid-cols-1"
        aria-label="서비스 흐름"
      >
        <FlowPanel
          accentClassName="border-t-emerald-500"
          items={childFlow}
          kicker="Child"
          title="아이 화면"
        />
        <FlowPanel
          accentClassName="border-t-orange-400"
          items={parentFlow}
          kicker="Parent"
          title="부모 화면"
        />
      </section>

      <section
        className="mt-4 flex items-center justify-between gap-6 rounded-lg border border-emerald-100 bg-white p-7 shadow-xl shadow-emerald-950/10 max-[760px]:grid"
        aria-label="프론트 개발 환경"
      >
        <div>
          <p className="text-sm font-bold tracking-normal text-emerald-700 uppercase">
            Frontend stack
          </p>
          <h2 className="text-2xl leading-snug font-bold text-slate-950">
            React + TypeScript + Vite + Tailwind CSS
          </h2>
        </div>
        <p className="max-w-md text-right text-slate-600 max-[760px]:text-left">
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
      className={`grid gap-7 rounded-lg border border-t-[6px] border-emerald-100 bg-white p-7 shadow-xl shadow-emerald-950/10 ${accentClassName}`}
    >
      <div>
        <p className="text-sm font-bold tracking-normal text-emerald-700 uppercase">
          {kicker}
        </p>
        <h2 className="text-2xl leading-snug font-bold text-slate-950">
          {title}
        </h2>
      </div>
      <ol className="grid gap-3">
        {items.map((item) => (
          <li
            className="flex min-h-12 items-center rounded-lg bg-emerald-50 px-4 font-bold text-slate-800"
            key={item}
          >
            {item}
          </li>
        ))}
      </ol>
    </article>
  )
}
