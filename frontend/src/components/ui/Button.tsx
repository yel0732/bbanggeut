import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'rounded-control inline-flex items-center justify-center font-bold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-leaf-600 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-leaf-600 text-white hover:bg-leaf-700',
        secondary:
          'border border-leaf-100 bg-paper text-ink-700 hover:border-leaf-300 hover:text-leaf-700',
        ghost: 'text-leaf-700 hover:bg-leaf-50',
      },
      size: {
        sm: 'px-4 py-2 text-sm',
        md: 'px-4 py-3',
        lg: 'px-5 py-3',
      },
    },
    defaultVariants: {
      size: 'md',
      variant: 'primary',
    },
  },
)

type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants>

export function Button({ className, size, variant, ...props }: ButtonProps) {
  return (
    <button
      className={cn(buttonVariants({ size, variant }), className)}
      {...props}
    />
  )
}

type ButtonLinkProps = Omit<ComponentProps<typeof Link>, 'className'> &
  VariantProps<typeof buttonVariants> & {
    className?: string
  }

export function ButtonLink({
  className,
  size,
  variant,
  ...props
}: ButtonLinkProps) {
  return (
    <Link
      className={cn(buttonVariants({ size, variant }), className)}
      {...props}
    />
  )
}
