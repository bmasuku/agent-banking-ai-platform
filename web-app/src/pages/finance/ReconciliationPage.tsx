import { useMutation, useQueries } from '@tanstack/react-query'
import { financeApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { ErrorAlert } from '@/components/ErrorAlert'
import { InsightPanel } from '@/components/InsightPanel'
import { LoadingBlock } from '@/components/LoadingBlock'
import { MetricCard } from '@/components/MetricCard'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { money } from '@/lib/utils'
import { asNumber, formatInstant, pageContent } from '@/pages/finance/helpers'
import { toast } from 'sonner'

export function ReconciliationPage() {
  const results = useQueries({
    queries: [
      { queryKey: ['finance', 'trial-balance'], queryFn: () => financeApi.trialBalance() },
      { queryKey: ['finance', 'float-position'], queryFn: () => financeApi.floatPosition() },
      { queryKey: ['finance', 'replenishments'], queryFn: () => financeApi.replenishments({ size: 15 }) },
    ],
  })

  const penaltiesMutation = useMutation({
    mutationFn: () => financeApi.runPenalties(),
    onSuccess: (data) => {
      const charged = asNumber((data as { advancesCharged?: number }).advancesCharged)
      toast.success(`Penalty run complete: ${charged} advance(s) charged`)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const loading = results.some((r) => r.isLoading)
  const error = results.find((r) => r.isError)?.error

  const trial = results[0].data as Record<string, unknown> | undefined
  const floatPosition = results[1].data as Record<string, unknown> | undefined
  const replenishments = pageContent<Record<string, unknown>>(results[2].data)

  const balanced = Boolean(trial?.balanced)
  const emoney = asNumber(floatPosition?.totalEmoneyIssued)
  const settlement = asNumber(floatPosition?.settlementBank)
  const receivable = asNumber(floatPosition?.totalAdvanceReceivable)
  const backing = settlement + receivable
  const gap = emoney - backing

  const narrative = `Trial balance ${balanced ? 'is balanced' : 'is out of balance'} (debit ${money(asNumber(trial?.totalDebit))} vs credit ${money(asNumber(trial?.totalCredit))}). E-money issued ${money(emoney)} is backed by settlement ${money(settlement)} plus advance receivable ${money(receivable)} (combined ${money(backing)}). Residual gap ${money(gap)}. Recent replenishments: ${replenishments.length} movement(s) on this page.`

  return (
    <div>
      <PageHeader
        title="Reconciliation"
        description="Cross-check trial balance, float backing, and recent replenishments before treasury actions."
        actions={
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={penaltiesMutation.isPending}
            onClick={() => penaltiesMutation.mutate()}
          >
            {penaltiesMutation.isPending ? 'Running…' : 'Run penalties'}
          </Button>
        }
      />

      <ErrorAlert error={error} />

      {loading ? (
        <LoadingBlock />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Trial balance"
              value={balanced ? 'Balanced' : 'Mismatch'}
              tone={balanced ? 'success' : 'danger'}
            />
            <MetricCard label="E-money issued" value={emoney} moneyFormat />
            <MetricCard label="Settlement + receivable" value={backing} moneyFormat />
            <MetricCard label="Backing gap" value={gap} moneyFormat tone={Math.abs(gap) < 0.01 ? 'success' : 'warning'} />
          </div>

          <InsightPanel title="Reconciliation narrative" explanation={narrative} footer="Built from trial balance, float position, and replenishment APIs" />

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Trial balance lines</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <LinesTable rows={(trial?.lines as Array<Record<string, unknown>>) ?? []} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Recent replenishments</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {replenishments.length === 0 ? (
                <p className="p-6 text-sm text-ink-muted">No replenishments on this page.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Reference</TableHead>
                      <TableHead>Agent</TableHead>
                      <TableHead>Amount</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {replenishments.map((r) => (
                      <TableRow key={String(r.id ?? r.reference)}>
                        <TableCell className="font-mono text-xs">{String(r.reference ?? '—')}</TableCell>
                        <TableCell>{String(r.agentCode ?? '—')}</TableCell>
                        <TableCell>{money(asNumber(r.amount ?? r.efloatAmount))}</TableCell>
                        <TableCell className="text-ink-muted">{formatInstant(r.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function LinesTable({ rows }: { rows: Array<Record<string, unknown>> }) {
  if (rows.length === 0) {
    return <p className="p-6 text-sm text-ink-muted">No trial balance lines returned.</p>
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Label</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Debit</TableHead>
          <TableHead>Credit</TableHead>
          <TableHead>Accounts</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, i) => (
          <TableRow key={i}>
            <TableCell>{String(row.label ?? row.balanceType ?? '—')}</TableCell>
            <TableCell>
              <Badge variant="secondary">{String(row.category ?? '—')}</Badge>
            </TableCell>
            <TableCell>{money(asNumber(row.debit))}</TableCell>
            <TableCell>{money(asNumber(row.credit))}</TableCell>
            <TableCell className="text-ink-muted">{String(row.accountCount ?? '—')}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
