import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { financeApi } from '@/api/endpoints'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadCsv, money } from '@/lib/utils'
import { asNumber, recordRows } from '@/pages/finance/helpers'

const TABS = [
  { id: 'trial', label: 'Trial balance', fetch: () => financeApi.trialBalance() },
  { id: 'income', label: 'Income statement', fetch: () => financeApi.incomeStatement() },
  { id: 'float', label: 'Float position', fetch: () => financeApi.floatPosition() },
  { id: 'advances', label: 'Advance book', fetch: () => financeApi.advanceBook() },
  { id: 'commissions', label: 'Commissions', fetch: () => financeApi.commissionsReport() },
  { id: 'transactions', label: 'Transactions', fetch: () => financeApi.transactionsReport() },
] as const

type TabId = (typeof TABS)[number]['id']

export function ReportsPage() {
  const [tab, setTab] = useState<TabId>('trial')

  const active = TABS.find((t) => t.id === tab) ?? TABS[0]

  const query = useQuery({
    queryKey: ['finance', 'reports', tab],
    queryFn: active.fetch,
  })

  const data = query.data as Record<string, unknown> | undefined
  const rows = useMemo(() => recordRows(query.data), [query.data])

  const exportRows = () => {
    if (rows.length > 0) {
      downloadCsv(`finance-${tab}.csv`, rows)
      return
    }
    if (data) {
      downloadCsv(`finance-${tab}.csv`, [data])
    }
  }

  return (
    <div>
      <PageHeader
        title="Finance reports"
        description="Ledger-derived trial balance, P&amp;L, float, advance book, commissions, and transaction summaries."
        actions={
          <Button type="button" variant="outline" size="sm" disabled={!data} onClick={exportRows}>
            Export CSV
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Button key={t.id} type="button" size="sm" variant={tab === t.id ? 'default' : 'outline'} onClick={() => setTab(t.id)}>
            {t.label}
          </Button>
        ))}
      </div>

      <ErrorAlert error={query.error} />

      {query.isLoading ? (
        <LoadingBlock />
      ) : (
        <div className="space-y-4">
          {tab === 'income' && data ? <IncomeSummary data={data} /> : null}
          {tab === 'trial' && data ? <TrialSummary data={data} /> : null}
          {tab === 'float' && data ? <FloatSummary data={data} /> : null}

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{active.label}</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {rows.length === 0 ? (
                <div className="p-6 text-sm text-ink-muted">
                  {data ? <ObjectPreview data={data} /> : 'No rows in this report.'}
                </div>
              ) : (
                <ReportTable rows={rows} highlightKey={tab === 'income' ? 'interest' : undefined} />
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function IncomeSummary({ data }: { data: Record<string, unknown> }) {
  const interest = asNumber(data.interestIncome)
  return (
    <Card className="border-forest/30 bg-mint/40">
      <CardContent className="flex flex-wrap items-center justify-between gap-4 pt-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-forest">Interest income (highlighted)</p>
          <p className="font-display text-2xl font-semibold text-forest">{money(interest)}</p>
        </div>
        <div className="text-sm text-ink-muted">
          <p>Fee income: {money(asNumber(data.feeIncome))}</p>
          <p>Commission expense: {money(asNumber(data.commissionExpense))}</p>
          <p>Net income: {money(asNumber(data.netIncome))}</p>
        </div>
      </CardContent>
    </Card>
  )
}

function TrialSummary({ data }: { data: Record<string, unknown> }) {
  return (
    <p className="text-sm text-ink-muted">
      Balanced: <span className="font-semibold text-ink">{String(data.balanced ?? '—')}</span> · Total debit{' '}
      {money(asNumber(data.totalDebit))} · Total credit {money(asNumber(data.totalCredit))}
    </p>
  )
}

function FloatSummary({ data }: { data: Record<string, unknown> }) {
  return (
    <p className="text-sm text-ink-muted">
      E-money issued {money(asNumber(data.totalEmoneyIssued))} · Settlement bank {money(asNumber(data.settlementBank))} ·
      Advance receivable {money(asNumber(data.totalAdvanceReceivable))}
    </p>
  )
}

function ReportTable({
  rows,
  highlightKey,
}: {
  rows: Array<Record<string, unknown>>
  highlightKey?: string
}) {
  const keys = Object.keys(rows[0] ?? {})
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {keys.map((k) => (
            <TableHead key={k}>{k}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, i) => (
          <TableRow key={i}>
            {keys.map((k) => {
              const raw = row[k]
              const text =
                typeof raw === 'number' ? money(raw) : raw == null ? '—' : typeof raw === 'object' ? JSON.stringify(raw) : String(raw)
              const hl =
                highlightKey &&
                (k.toLowerCase().includes('interest') || String(row.label ?? row.type ?? '').toLowerCase().includes('interest'))
              return (
                <TableCell key={k} className={hl ? 'bg-mint/50 font-semibold text-forest' : undefined}>
                  {text}
                </TableCell>
              )
            })}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function ObjectPreview({ data }: { data: Record<string, unknown> }) {
  const skip = new Set(['lines', 'agents', 'byStatus', 'repaymentsInPeriod', 'areas'])
  const entries = Object.entries(data).filter(([k]) => !skip.has(k))
  return (
    <dl className="grid gap-2 sm:grid-cols-2">
      {entries.map(([k, v]) => (
        <div key={k}>
          <dt className="text-xs uppercase text-ink-muted">{k}</dt>
          <dd className="text-ink">
            {typeof v === 'number' ? money(v) : v == null ? '—' : typeof v === 'object' ? JSON.stringify(v) : String(v)}
          </dd>
        </div>
      ))}
    </dl>
  )
}
