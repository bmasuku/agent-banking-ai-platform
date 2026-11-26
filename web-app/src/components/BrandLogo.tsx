import { cn } from '@/lib/utils'

type Props = {
  className?: string
  /** Compact mark for tight nav headers */
  compact?: boolean
}

/** Official EcoCash wordmark from www.ecocash.co.zw */
export function BrandLogo({ className, compact }: Props) {
  return (
    <img
      src="/brand/ecocash-logo.png"
      alt="EcoCash"
      className={cn(compact ? 'h-7 w-auto' : 'h-9 w-auto', className)}
      draggable={false}
    />
  )
}

export function BrandMark({ className }: { className?: string }) {
  return (
    <img
      src="/brand/ecocash-app-icon.png"
      alt=""
      aria-hidden
      className={cn('h-10 w-10 rounded-xl object-cover shadow-sm shadow-forest/20', className)}
      draggable={false}
    />
  )
}
