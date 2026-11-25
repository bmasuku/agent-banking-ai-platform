import { AiBadge, DataKindBadge } from '@/components/AiBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { money } from '@/lib/utils'
import { cn } from '@/lib/utils'

type Props = {
  label: string
  value: number | string
  hint?: string
  moneyFormat?: boolean
  tone?: 'default' | 'warning' | 'danger' | 'success'
  /** Ledger = posted balances; predicted/recommended = model output. */
  kind?: 'ledger' | 'predicted' | 'recommended' | 'derived'
  className?: string
}

export function MetricCard({
  label,
  value,
  hint,
  moneyFormat,
  tone = 'default',
  kind,
  className,
}: Props) {
  const display = moneyFormat && typeof value === 'number' ? money(value) : value
  const badgeVariant =
    tone === 'warning' ? 'warning' : tone === 'danger' ? 'danger' : tone === 'success' ? 'success' : 'secondary'
  return (
    <Card
      className={cn(
        kind === 'predicted' || kind === 'recommended' ? 'border-info/25 bg-info-soft/20' : undefined,
        className,
      )}
    >
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-medium text-ink-muted">{label}</CardTitle>
          <div className="flex flex-wrap items-center justify-end gap-1">
            {kind === 'ledger' ? <DataKindBadge kind="ledger" /> : null}
            {kind === 'predicted' ? (
              <>
                <DataKindBadge kind="predicted" />
                <AiBadge />
              </>
            ) : null}
            {kind === 'recommended' ? (
              <>
                <DataKindBadge kind="recommended" />
                <AiBadge />
              </>
            ) : null}
            {kind === 'derived' ? <DataKindBadge kind="derived" /> : null}
            {tone !== 'default' ? <Badge variant={badgeVariant}>{tone}</Badge> : null}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="font-display text-2xl font-semibold tabular-nums text-ink">{display}</div>
        {hint ? <p className="mt-1 text-xs text-ink-faint">{hint}</p> : null}
      </CardContent>
    </Card>
  )
}
