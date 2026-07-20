import { PageShell } from '@/components/layout/PageShell'
import { ButtonLink } from '@/components/ui/Button'
import { SectionHeader } from '@/components/ui/SectionHeader'

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
    <PageShell className="grid max-w-[720px] content-center gap-6">
      <SectionHeader
        eyebrow={eyebrow}
        title={title}
        description={description}
      />

      <ButtonLink className="w-fit" to="/">
        홈으로 돌아가기
      </ButtonLink>
    </PageShell>
  )
}
