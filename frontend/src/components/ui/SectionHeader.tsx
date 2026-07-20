import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

type SectionHeaderProps = ComponentProps<'section'> & {
  description?: string
  eyebrow: string
  title: string
}

export function SectionHeader({
  className,
  description,
  eyebrow,
  title,
  ...props
}: SectionHeaderProps) {
  return (
    <section className={cn('grid max-w-3xl gap-4', className)} {...props}>
      <p className="text-leaf-600 text-sm font-bold tracking-normal uppercase">
        {eyebrow}
      </p>
      <h1 className="text-ink-950 text-5xl leading-tight font-bold tracking-normal max-[760px]:text-4xl">
        {title}
      </h1>
      {description ? (
        <p className="text-ink-600 text-lg">{description}</p>
      ) : null}
    </section>
  )
}
