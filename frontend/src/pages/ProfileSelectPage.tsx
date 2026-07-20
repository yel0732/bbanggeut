import { PageShell } from '@/components/layout/PageShell'
import { ButtonLink } from '@/components/ui/Button'
import { Card, CardLink } from '@/components/ui/Card'
import { SectionHeader } from '@/components/ui/SectionHeader'

const childProfiles = [
  {
    name: '하린',
    age: '7세',
    note: '오늘의 놀이 준비 완료',
    toneClassName: 'bg-leaf-50 border-leaf-100',
  },
  {
    name: '도윤',
    age: '6세',
    note: '프로필을 선택해 시작해요',
    toneClassName: 'bg-sun-100 border-sun-100',
  },
]

export function ProfileSelectPage() {
  return (
    <PageShell>
      <SectionHeader
        className="mb-10"
        eyebrow="Profile"
        title="누가 시작할까요?"
        description="아이 프로필을 선택하면 아이 모드로, 부모 모드는 인증 후 들어갑니다."
      />

      <section className="grid grid-cols-[1fr_0.75fr] gap-4 max-[860px]:grid-cols-1">
        <div className="grid grid-cols-2 gap-4 max-[640px]:grid-cols-1">
          {childProfiles.map((profile) => (
            <CardLink
              className={`grid min-h-64 content-between ${profile.toneClassName}`}
              key={profile.name}
              to="/child"
            >
              <div className="grid gap-3">
                <div className="bg-paper grid size-20 place-items-center rounded-full text-3xl font-bold">
                  {profile.name.slice(0, 1)}
                </div>
                <div>
                  <h2 className="text-ink-950 text-3xl font-bold">
                    {profile.name}
                  </h2>
                  <p className="text-ink-600 font-bold">{profile.age}</p>
                </div>
              </div>
              <p className="text-ink-700 font-bold">{profile.note}</p>
            </CardLink>
          ))}
        </div>

        <Card className="grid content-between gap-8">
          <div className="grid gap-3">
            <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
              Parent
            </p>
            <h2 className="text-ink-950 text-2xl leading-snug font-bold">
              부모 모드
            </h2>
            <p className="text-ink-600">
              리포트, 일기, 아이 프로필 관리는 부모 인증 후 확인할 수 있어요.
            </p>
          </div>

          <div className="grid gap-3">
            <ButtonLink to="/auth">부모 모드 진입</ButtonLink>
            <ButtonLink to="/profiles/new" variant="secondary">
              아이 프로필 추가
            </ButtonLink>
          </div>
        </Card>
      </section>
    </PageShell>
  )
}
