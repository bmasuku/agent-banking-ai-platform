import { useQueries } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { financeApi } from '@/api/endpoints'
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
import { asNumber, formatInstant, pageContent, pageMeta } from '@/pages/finance/helpers'

export function FinanceDashboardPage() {
  const results = useQueries({
    queries: [
      { queryKey: ['finance', 'float-position'], queryFn: () => financeApi.floatPosition() },
      { queryKey: ['finance', 'advance-book'], queryFn: () => financeApi.advanceBook() },
      { queryKey: ['finance', 'commissions-report'], queryFn: () => financeApi.commissionsReport() },
      { queryKey: ['finance', 'income-statement'], queryFn: () => financeApi.incomeStatement() },
      { queryKey: ['finance', 'advances', 'PENDING'], queryFn: () => financeApi.advances({ status: 'PENDING', size: 1 }) },
      { queryKey: ['finance', 'disbursements', 'recent'], queryFn: () => financeApi.disbursements({ size: 5 }) },
    ],
  })

  const loading = results.some((r) => r.isLoading)
  const error = results.find((r) => r.isError)?.error

  const floatPosition = results[0].data as Record<string, unknown> | undefined
  const advanceBook = results[1].data as Record<string, unknown> | undefined
  const commissionsReport = results[2].data as Record<string, unknown> | undefined
  const incomeStatement = results[3].data as Record<string, unknown> | undefined
  const pendingAdvances = results[4].data
  const recentDisbursements = pageContent<Record<string, unknown>>(results[5].data)

  const pendingCount = pageMeta(pendingAdvances).totalElements

  const chartData =
    incomeStatement != null
      ? [
          {
            name: 'Period',
            fees: asNumber(incomeStatement.feeIncome),
            interest: asNumber(incomeStatement.interestIncome),
            commission: asNumber(incomeStatement.commissionExpense),
            net: asNumber(incomeStatement.netIncome),
          },
        ]
      : []

  const floatNarrative = floatPosition
    ? `Settlement bank ${money(asNumber(floatPosition.settlementBank))}, agent e-float ${money(asNumber(floatPosition.totalAgentEfloat))}, ` +
      `e-money issued ${money(asNumber(floatPosition.totalEmoneyIssued))}, advance receivable ${money(asNumber(floatPosition.totalAdvanceReceivable))}.`
    : 'Float position data is not available yet.'

  const advanceNarrative = advanceBook
    ? `Outstanding principal ${money(asNumber(advanceBook.totalPrincipalOutstanding))} and interest ${money(asNumber(advanceBook.totalInterestOutstanding))} across the advance book.`
    : 'Advance book metrics are not available yet.'

  return (
    <div>
      <PageHeader
        title="Finance overview"
        description="Treasury float, commissions, advances, and ledger-derived income — all sourced from live finance APIs."
        actions={
          <>
            <Button asChild variant="outline" size="sm">
              <Link to="/finance/commissions">Commissions</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/finance/reports">Reports</Link>
            </Button>
          </>
        }
      />
      <ErrorAlert error={error} />
      {loading ? (
        <LoadingBlock label="Loading finance dashboard…" />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Net income (period)"
              value={asNumber(incomeStatement?.netIncome)}
              moneyFormat
              kind="ledger"
              tone={asNumber(incomeStatement?.netIncome) >= 0 ? 'success' : 'danger'}
            />
            <MetricCard
              label="Interest income"
              value={asNumber(incomeStatement?.interestIncome)}
              moneyFormat
              kind="ledger"
              tone="success"
              hint="From income statement"
            />
            <MetricCard
              label="Commission payable (network)"
              value={asNumber(commissionsReport?.totalAccrued)}
              moneyFormat
              kind="ledger"
            />
            <MetricCard
              label="Pending advance decisions"
              value={pendingCount}
              tone={pendingCount > 0 ? 'warning' : 'default'}
              kind="derived"
              hint="Status PENDING"
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <MetricCard
              label="Agent e-float (total)"
              value={asNumber(floatPosition?.totalAgentEfloat)}
              moneyFormat
              kind="ledger"
              hint="Sum of posted agent e-float balances"
            />
            <MetricCard
              label="Advance principal outstanding"
              value={asNumber(advanceBook?.totalPrincipalOutstanding)}
              moneyFormat
              kind="ledger"
              tone={asNumber(advanceBook?.totalPrincipalOutstanding) > 0 ? 'warning' : 'default'}
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <InsightPanel
              title="Float position"
              source="system"
              explanation={floatNarrative}
              footer="Derived from float position report — ledger totals, not AI forecasts."
            />
            <InsightPanel
              title="Advance book"
              source="system"
              explanation={advanceNarrative}
              footer="Derived from advance book report"
            />
            <InsightPanel
              title="Commission recovery"
              source="system"
              explanation={
                commissionsReport
                  ? `Gross disbursed ${money(asNumber(commissionsReport.grossDisbursed))}, principal recovered ${money(asNumber(commissionsReport.principalRecovered))}, interest recovered ${money(asNumber(commissionsReport.interestRecovered))}, net paid ${money(asNumber(commissionsReport.netDisbursed))}.`
                  : 'Commission report is not available.'
              }
              footer="Commission recovery report"
            />
          </div>

          {chartData.length > 0 ? (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Income statement</CardTitle>
              </CardHeader>
              <CardContent className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                    <XAxis dataKey="name" />
                    <YAxis tickFormatter={(v) => money(v).replace('USD ', '')} width={72} />
                    <Tooltip formatter={(v) => money(asNumber(v))} />
                    <Legend />
                    <Bar dataKey="fees" name="Fee income" fill="#2d6a4f" />
                    <Bar dataKey="interest" name="Interest income" fill="#40916c" />
                    <Bar dataKey="commission" name="Commission expense" fill="#d4a373" />
                    <Bar dataKey="net" name="Net income" fill="#1b4332" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          ) : null}

          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Recent commission disbursements</CardTitle>
              <Button asChild variant="ghost" size="sm">
                <Link to="/finance/commissions">View all</Link>
              </Button>
            </CardHeader>
            <CardContent>
              {recentDisbursements.length === 0 ? (
                <p className="text-sm text-ink-muted">No disbursements recorded yet.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Reference</TableHead>
                      <TableHead>Agent</TableHead>
                      <TableHead>Net</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {recentDisbursements.map((row) => (
                      <TableRow key={String(row.id ?? row.reference)}>
                        <TableCell className="font-mono text-xs">{String(row.reference ?? '—')}</TableCell>
                        <TableCell>{String(row.agentCode ?? row.agentName ?? '—')}</TableCell>
                        <TableCell>{money(asNumber(row.netCommission))}</TableCell>
                        <TableCell className="text-ink-muted">{formatInstant(row.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Quick actions</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Button asChild variant="outline" size="sm">
                <Link to="/finance/advances">Review advances</Link>
              </Button>
              <Button asChild variant="outline" size="sm">
                <Link to="/finance/statements">Agent statements</Link>
              </Button>
              <Button asChild variant="outline" size="sm">
                <Link to="/finance/reconciliation">Reconciliation</Link>
              </Button>
              {pendingCount > 0 ? (
                <Badge variant="warning">{pendingCount} pending decision(s)</Badge>
              ) : null}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}