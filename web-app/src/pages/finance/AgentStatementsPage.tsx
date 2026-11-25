import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { sharedApi } from '@/api/endpoints'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { MetricCard } from '@/components/MetricCard'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadCsv, money } from '@/lib/utils'
import { asNumber, formatInstant, pageContent, pageMeta } from '@/pages/finance/helpers'

type AgentOption = { id: string; agentCode: string; businessName?: string; fullName?: string }

const BALANCE_TYPES = ['EFLOAT', 'CASH', 'COMMISSION_PAYABLE'] as const

export function AgentStatementsPage() {
  const [agentId, setAgentId] = useState('')
  const [balanceType, setBalanceType] = useState<(typeof BALANCE_TYPES)[number]>('EFLOAT')
  const [page, setPage] = useState(0)

  const agentsQuery = useQuery({
    queryKey: ['agents'],
    queryFn: () => sharedApi.agents(),
  })

  const balancesQuery = useQuery({
    queryKey: ['agent-balances', agentId],
    queryFn: () => sharedApi.agentBalances(agentId),
    enabled: Boolean(agentId),
  })

  const statementQuery = useQuery({
    queryKey: ['agent-statement', agentId, balanceType, page],
    queryFn: () => sharedApi.agentStatement(agentId, { type: balanceType, page, size: 25 }),
    enabled: Boolean(agentId),
  })

  const agents = (Array.isArray(agentsQuery.data) ? agentsQuery.data : []) as AgentOption[]
  const statementRows = pageContent<Record<string, unknown>>(statementQuery.data)
  const statementMeta = pageMeta(statementQuery.data)

  const balances = balancesQuery.data as Record<string, unknown> | undefined
  const snapshot = (balances?.balances ?? balances) as Record<string, unknown> | undefined

  const exportStatement = () => {
    downloadCsv(`agent-statement-${agentId}-${balanceType}.csv`, statementRows)
  }

  return (
    <div>
      <PageHeader
        title="Agent statements"
        description="Ledger balances and paged statement lines for any agent in the network."
        actions={
          <Button type="button" variant="outline" size="sm" disabled={statementRows.length === 0} onClick={exportStatement}>
            Export CSV
          </Button>
        }
      />

      <ErrorAlert error={agentsQuery.error ?? balancesQuery.error ?? statementQuery.error} />

      <div className="mb-6 grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="stmt-agent">Agent</Label>
          <select
            id="stmt-agent"
            className="flex h-10 w-full rounded-md border border-line bg-card px-3 text-sm"
            value={agentId}
            onChange={(e) => {
              setAgentId(e.target.value)
              setPage(0)
            }}
          >
            <option value="">Select agent…</option>
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.agentCode} · {a.businessName ?? a.fullName ?? a.agentCode}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="stmt-type">Balance type</Label>
          <select
            id="stmt-type"
            className="flex h-10 w-full rounded-md border border-line bg-card px-3 text-sm"
            value={balanceType}
            onChange={(e) => {
              setBalanceType(e.target.value as (typeof BALANCE_TYPES)[number])
              setPage(0)
            }}
          >
            {BALANCE_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
      </div>

      {!agentId ? (
        <EmptyState title="Select an agent" message="Choose an agent to load balances and statement lines." />
      ) : balancesQuery.isLoading ? (
        <LoadingBlock label="Loading balances…" />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard label="E-float" value={asNumber(snapshot?.efloat)} moneyFormat />
            <MetricCard label="Cash" value={asNumber(snapshot?.cash)} moneyFormat />
            <MetricCard label="Commission payable" value={asNumber(snapshot?.commissionPayable)} moneyFormat />
            <MetricCard
              label="Advance outstanding"
              value={asNumber(snapshot?.advancePrincipalOutstanding) + asNumber(snapshot?.advanceInterestOutstanding)}
              moneyFormat
              tone="warning"
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                Statement · {balanceType}
                {balances?.agentCode ? ` · ${String(balances.agentCode)}` : ''}
              </CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {statementQuery.isLoading ? (
                <LoadingBlock />
              ) : statementRows.length === 0 ? (
                <p className="p-6 text-sm text-ink-muted">No statement lines for this balance type.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Reference</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Side</TableHead>
                      <TableHead>Amount</TableHead>
                      <TableHead>Signed</TableHead>
                      <TableHead>Balance after</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {statementRows.map((row) => (
                      <TableRow key={String(row.entryId ?? row.id)}>
                        <TableCell className="font-mono text-xs">{String(row.reference ?? '—')}</TableCell>
                        <TableCell>{String(row.transactionType ?? '—')}</TableCell>
                        <TableCell>{String(row.side ?? '—')}</TableCell>
                        <TableCell>{money(asNumber(row.amount))}</TableCell>
                        <TableCell>{money(asNumber(row.signedAmount))}</TableCell>
                        <TableCell className="font-semibold">{money(asNumber(row.balanceAfter))}</TableCell>
                        <TableCell className="text-ink-muted">{formatInstant(row.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          {statementMeta.totalPages > 1 ? (
            <div className="flex items-center justify-between text-sm text-ink-muted">
              <span>
                Page {statementMeta.page + 1} of {statementMeta.totalPages}
              </span>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={page + 1 >= statementMeta.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}
