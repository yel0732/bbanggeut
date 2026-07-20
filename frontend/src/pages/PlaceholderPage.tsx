import { Link } from 'react-router-dom'

type PlaceholderPageProps = {
  description: string
  eyebrow: string
  title: string
}

export function PlaceholderPage({
  description,
  eyebrow,
  title,
}: PlaceholderPageProps) {
  return (
    <main className="mx-auto grid min-h-screen max-w-[720px] content-center gap-6 px-5 py-14">
      <div className="grid gap-3">
        <p className="text-sm font-bold tracking-normal text-emerald-700 uppercase">
          {eyebrow}
        </p>
        <h1 className="text-5xl leading-tight font-bold tracking-normal text-slate-950 max-[760px]:text-4xl">
          {title}
        </h1>
        <p className="text-lg text-slate-600">{description}</p>
      </div>

      <Link
        className="w-fit rounded-lg bg-emerald-600 px-4 py-2 font-bold text-white transition hover:bg-emerald-700"
        to="/"
      >
        홈으로 돌아가기
      </Link>
    </main>
  )
}
