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
        <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
          {eyebrow}
        </p>
        <h1 className="text-ink-950 text-5xl leading-tight font-bold tracking-normal max-[760px]:text-4xl">
          {title}
        </h1>
        <p className="text-ink-600 text-lg">{description}</p>
      </div>

      <Link
        className="rounded-control bg-leaf-600 hover:bg-leaf-700 w-fit px-4 py-2 font-bold text-white transition"
        to="/"
      >
        홈으로 돌아가기
      </Link>
    </main>
  )
}
