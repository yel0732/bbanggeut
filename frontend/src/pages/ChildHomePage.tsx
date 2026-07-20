import { PageShell } from '@/components/layout/PageShell'
import { ButtonLink } from '@/components/ui/Button'
import { CardLink } from '@/components/ui/Card'
import { SectionHeader } from '@/components/ui/SectionHeader'

const childActions = [
  {
    title: '오늘의 놀이',
    description: '운동 게임과 인지 게임을 골라서 시작해요.',
    to: '/games',
    className: 'border-t-leaf-500',
  },
  {
    title: '캐릭터와 대화',
    description: '오늘 있었던 일을 캐릭터에게 들려줘요.',
    to: '/conversation',
    className: 'border-t-sun-400',
  },
  {
    title: '내 일기 보기',
    description: '지난 이야기를 다시 볼 수 있어요.',
    to: '/diaries',
    className: 'border-t-berry-400',
  },
]

export function ChildHomePage() {
  return (
    <PageShell>
      <header className="mb-10 flex items-start justify-between gap-6 max-[760px]:grid">
        <SectionHeader
          eyebrow="Child home"
          title="하린아, 오늘은 뭐 하고 놀까?"
          description="게임을 하고 나면 캐릭터가 오늘 이야기를 물어봐 줄 거예요."
        />
        <ButtonLink to="/profiles" variant="secondary">
          프로필 바꾸기
        </ButtonLink>
      </header>

      <section className="grid grid-cols-3 gap-4 max-[860px]:grid-cols-1">
        {childActions.map((action) => (
          <CardLink
            className={`grid min-h-56 content-between border-t-[6px] ${action.className}`}
            key={action.to}
            to={action.to}
          >
            <div className="grid gap-3">
              <h2 className="text-ink-950 text-3xl font-bold">
                {action.title}
              </h2>
              <p className="text-ink-600 text-lg">{action.description}</p>
            </div>
            <span className="text-leaf-700 font-bold">시작하기</span>
          </CardLink>
        ))}
      </section>
    </PageShell>
  )
}
