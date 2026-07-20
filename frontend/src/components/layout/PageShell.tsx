import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

type PageShellProps = ComponentProps<'main'>

export function PageShell({ className, ...props }: PageShellProps) {
  return (
    <main
      className={cn(
        'mx-auto min-h-screen max-w-[1120px] px-5 py-14 max-[760px]:px-3.5 max-[760px]:py-8',
        className,
      )}
      {...props}
    />
  )
}
