import { PageShell } from '@/components/layout/PageShell'
import { Badge } from '@/components/ui/Badge'
import { ButtonLink } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SectionHeader } from '@/components/ui/SectionHeader'

const games = [
  {
    title: '과일 베기 놀이',
    category: '운동 게임',
    description: '손과 몸을 움직이며 과일을 베고 잡아요.',
    metrics: ['반응 속도', '눈-손 협응', '대근육 활동'],
    accentClassName: 'border-t-leaf-500',
  },
  {
    title: '같은 그림 찾기',
    category: '인지 게임',
    description: '두 카드에서 같은 그림을 빠르게 찾아요.',
    metrics: ['관찰력', '집중력', '시각 변별'],
    accentClassName: 'border-t-sun-400',
  },
]

export function GameEntryPage() {
  return (
    <PageShell>
      <SectionHeader
        className="mb-10"
        eyebrow="Today play"
        title="오늘의 놀이를 골라요"
        description="게임을 시작하기 전에 움직일 공간과 카메라 권한을 확인합니다."
      />

      <section className="grid grid-cols-[1fr_0.8fr] gap-4 max-[900px]:grid-cols-1">
        <div className="grid gap-4">
          {games.map((game) => (
            <Card
              className={`grid gap-5 border-t-[6px] ${game.accentClassName}`}
              key={game.title}
            >
              <div className="grid gap-2">
                <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
                  {game.category}
                </p>
                <h2 className="text-ink-950 text-3xl font-bold">
                  {game.title}
                </h2>
                <p className="text-ink-600 text-lg">{game.description}</p>
              </div>

              <ul className="flex flex-wrap gap-2">
                {game.metrics.map((metric) => (
                  <li key={metric}>
                    <Badge>{metric}</Badge>
                  </li>
                ))}
              </ul>

              <ButtonLink className="w-fit" size="lg" to="/games/play">
                이 놀이 시작
              </ButtonLink>
            </Card>
          ))}
        </div>

        <Card className="grid content-start gap-6">
          <div className="grid gap-2">
            <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
              Before play
            </p>
            <h2 className="text-ink-950 text-2xl font-bold">시작 전 확인</h2>
          </div>
          <ul className="grid gap-3">
            <li className="rounded-control bg-leaf-50 text-ink-800 px-4 py-3 font-bold">
              카메라를 켤 수 있어야 해요.
            </li>
            <li className="rounded-control bg-leaf-50 text-ink-800 px-4 py-3 font-bold">
              팔을 움직일 공간을 비워요.
            </li>
            <li className="rounded-control bg-leaf-50 text-ink-800 px-4 py-3 font-bold">
              게임 후 캐릭터와 오늘 이야기를 나눠요.
            </li>
          </ul>
        </Card>
      </section>
    </PageShell>
  )
}
