import { Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Visible marker for values or copy produced by an AI / predictive model. */
export function AiBadge({
  className,
  label = 'AI',
}: {
  className?: string
  label?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md border border-info/30 bg-info-soft px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-info',
        className,
      )}
      title="Generated or scored by an AI / predictive model"
    >
      <Sparkles className="h-3 w-3" aria-hidden />
      {label}
    </span>
  )
}

/** Distinguishes ledger-backed balances from forecasts and recommendations. */
export function DataKindBadge({
  kind,
  className,
}: {
  kind: 'ledger' | 'predicted' | 'recommended' | 'derived'
  className?: string
}) {
  const styles: Record<typeof kind, string> = {
    ledger: 'border-success/25 bg-success-soft text-success',
    predicted: 'border-info/30 bg-info-soft text-info',
    recommended: 'border-warning/30 bg-warning-soft text-warning',
    derived: 'border-line bg-sand-deep/60 text-ink-muted',
  }
  const labels: Record<typeof kind, string> = {
    ledger: 'Ledger',
    predicted: 'Predicted',
    recommended: 'Recommended',
    derived: 'Derived',
  }
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-md border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
        styles[kind],
        className,
      )}
    >
      {labels[kind]}
    </span>
  )
}
