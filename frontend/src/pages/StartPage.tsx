import { PageShell } from '@/components/layout/PageShell'
import { ButtonLink } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SectionHeader } from '@/components/ui/SectionHeader'

const onboardingSteps = [
  '부모 계정으로 시작',
  '아이 프로필 만들기',
  '프로필 선택',
  '오늘의 놀이 시작',
]

export function StartPage() {
  return (
    <PageShell className="grid content-center gap-10">
      <section className="grid grid-cols-[1.1fr_0.9fr] items-center gap-10 max-[860px]:grid-cols-1">
        <div className="grid gap-7">
          <SectionHeader
            className="[&>h1]:max-w-3xl [&>h1]:text-6xl"
            eyebrow="Play based growth diary"
            title="오늘도 놀면서 자라는 하루"
            description="아이는 게임으로 움직이고 생각하며, 캐릭터와 대화해 오늘의 이야기를 남깁니다. 부모는 그 기록을 리포트로 확인합니다."
          />

          <div className="flex flex-wrap gap-3">
            <ButtonLink size="lg" to="/profiles">
              시작하기
            </ButtonLink>
            <ButtonLink size="lg" to="/auth" variant="secondary">
              부모 로그인
            </ButtonLink>
          </div>
        </div>

        <Card className="grid gap-6" aria-label="시작 흐름">
          <div className="grid gap-2">
            <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
              First run
            </p>
            <h2 className="text-ink-950 text-2xl leading-snug font-bold">
              처음 시작할 때
            </h2>
          </div>

          <ol className="grid gap-3">
            {onboardingSteps.map((step, index) => (
              <li
                className="rounded-control bg-leaf-50 text-ink-800 flex min-h-14 items-center gap-3 px-4 font-bold"
                key={step}
              >
                <span className="bg-leaf-600 grid size-8 shrink-0 place-items-center rounded-full text-sm text-white">
                  {index + 1}
                </span>
                {step}
              </li>
            ))}
          </ol>
        </Card>
      </section>
    </PageShell>
  )
}
