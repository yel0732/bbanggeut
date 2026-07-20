import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

const cardVariants = cva('rounded-control border p-7', {
  variants: {
    tone: {
      paper: 'border-leaf-100 bg-paper shadow-panel',
      leaf: 'border-leaf-100 bg-leaf-50 shadow-panel',
      sun: 'border-sun-100 bg-sun-100 shadow-panel',
    },
    interactive: {
      true: 'transition hover:-translate-y-0.5',
      false: '',
    },
  },
  defaultVariants: {
    interactive: false,
    tone: 'paper',
  },
})

type CardProps = ComponentProps<'article'> & VariantProps<typeof cardVariants>

export function Card({ className, interactive, tone, ...props }: CardProps) {
  return (
    <article
      className={cn(cardVariants({ interactive, tone }), className)}
      {...props}
    />
  )
}

type CardLinkProps = Omit<ComponentProps<typeof Link>, 'className'> &
  VariantProps<typeof cardVariants> & {
    className?: string
  }

export function CardLink({
  className,
  interactive = true,
  tone,
  ...props
}: CardLinkProps) {
  return (
    <Link
      className={cn(cardVariants({ interactive, tone }), className)}
      {...props}
    />
  )
}
