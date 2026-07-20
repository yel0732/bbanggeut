import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

type BadgeProps = ComponentProps<'span'>

export function Badge({ className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'rounded-control bg-leaf-50 text-ink-700 px-3 py-2 text-sm font-bold',
        className,
      )}
      {...props}
    />
  )
}
