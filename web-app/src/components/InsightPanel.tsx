import { AiBadge, DataKindBadge } from '@/components/AiBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export function InsightPanel({
  title = 'Insight',
  explanation,
  footer,
  source = 'ai',
  className,
}: {
  title?: string
  explanation: string
  footer?: string
  /** `ai` shows the AI badge; `system` is for ledger/API-derived narratives. */
  source?: 'ai' | 'system'
  className?: string
}) {
  return (
    <Card className={cn('overflow-hidden border-forest/20', className)}>
      <div className={cn('h-1', source === 'ai' ? 'bg-info' : 'bg-forest-soft')} />
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-xs font-semibold uppercase tracking-wide text-forest">{title}</CardTitle>
          {source === 'ai' ? <AiBadge /> : <DataKindBadge kind="derived" />}
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-sm leading-relaxed text-ink">{explanation}</p>
        {footer ? <p className="mt-2 text-xs text-ink-faint">{footer}</p> : null}
      </CardContent>
    </Card>
  )
}
