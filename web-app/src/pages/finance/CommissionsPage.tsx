import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '@/api/client'
import { financeApi, sharedApi } from '@/api/endpoints'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadCsv, money, newIdempotencyKey } from '@/lib/utils'
import { asNumber, formatInstant, pageContent, pageMeta } from '@/pages/finance/helpers'
import type { DisbursementPreview } from '@/types/api'
import { toast } from 'sonner'

type AgentOption = { id?: string; agentId?: string; agentCode: string; businessName?: string; fullName?: string }

export function CommissionsPage() {
  const queryClient = useQueryClient()
  const [commissionPage, setCommissionPage] = useState(0)
  const [disbursementPage, setDisbursementPage] = useState(0)
  const [agentId, setAgentId] = useState('')

  const agentsQuery = useQuery({
    queryKey: ['agents'],
    queryFn: () => sharedApi.agents(),
  })

  const commissionsQuery = useQuery({
    queryKey: ['finance', 'commissions', commissionPage],
    queryFn: () => financeApi.commissions({ page: commissionPage, size: 20 }),
  })

  const disbursementsQuery = useQuery({
    queryKey: ['finance', 'disbursements', disbursementPage],
    queryFn: () => financeApi.disbursements({ page: disbursementPage, size: 20 }),
  })

  const previewQuery = useQuery({
    queryKey: ['finance', 'disbursement-preview', agentId],
    queryFn: () => financeApi.preview(agentId),
    enabled: Boolean(agentId),
  })

  const agents = (Array.isArray(agentsQuery.data) ? agentsQuery.data : []) as AgentOption[]

  const disburseMutation = useMutation({
    mutationFn: (id: string) => financeApi.disburse(id, newIdempotencyKey('DISB')),
    onSuccess: () => {
      toast.success('Commission disbursed')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const batchMutation = useMutation({
    mutationFn: () => financeApi.disburseBatch(newIdempotencyKey('BATCH')),
    onSuccess: (data) => {
      const r = data as { processed?: number; totalNet?: number }
      toast.success(`Batch complete: ${r.processed ?? 0} agent(s), net ${money(asNumber(r.totalNet))}`)
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const commissionRows = pageContent<Record<string, unknown>>(commissionsQuery.data)
  const commissionMeta = pageMeta(commissionsQuery.data)
  const disbursementRows = pageContent<Record<string, unknown>>(disbursementsQuery.data)
  const disbursementMeta = pageMeta(disbursementsQuery.data)
  const preview = previewQuery.data as DisbursementPreview | undefined

  const exportCommissions = () => {
    downloadCsv('commissions.csv', commissionRows)
  }

  const exportDisbursements = () => {
    downloadCsv('disbursements.csv', disbursementRows)
  }

  return (
    <div>
      <PageHeader
        title="Commissions"
        description="Accrued commission lines, disbursement preview with advance recovery, and payout history."
        actions={
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={batchMutation.isPending}
            onClick={() => batchMutation.mutate()}
          >
            {batchMutation.isPending ? 'Running batch…' : 'Disburse batch'}
          </Button>
        }
      />

      <ErrorAlert error={agentsQuery.error ?? commissionsQuery.error ?? disbursementsQuery.error} />

      <div className="mb-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Disburse to agent</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="agent-select">Agent</Label>
              <select
                id="agent-select"
                className="flex h-10 w-full rounded-md border border-line bg-card px-3 text-sm"
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
              >
                <option value="">Select agent…</option>
                {agents.map((a) => {
                  const id = a.id ?? a.agentId ?? ''
                  return (
                    <option key={id} value={id}>
                      {a.agentCode} · {a.businessName ?? a.agentCode}
                    </option>
                  )
                })}
              </select>
            </div>

            {agentId && previewQuery.isLoading ? <LoadingBlock label="Loading preview…" /> : null}
            {agentId && preview ? (
              <div className="space-y-3 rounded-lg border border-line bg-sand/40 p-4 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-ink">{preview.agentCode}</span>
                  <Badge variant={preview.eligible ? 'success' : 'warning'}>
                    {preview.eligible ? 'Eligible' : 'Not eligible'}
                  </Badge>
                </div>
                <dl className="grid gap-2 sm:grid-cols-2">
                  <Token label="Gross commission" value={money(preview.grossCommission)} />
                  <Token label="Interest recovered" value={money(preview.interestRecovered)} />
                  <Token label="Principal recovered" value={money(preview.principalRecovered)} />
                  <Token label="Net commission" value={money(preview.netCommission)} highlight />
                  <Token label="Outstanding before" value={money(preview.outstandingAdvanceBefore)} />
                  <Token label="Outstanding after" value={money(preview.outstandingAdvanceAfter)} />
                </dl>
                {preview.reason ? <p className="text-xs text-ink-muted">Reason: {preview.reason}</p> : null}
                <Button
                  type="button"
                  className="w-full"
                  disabled={!preview.eligible || disburseMutation.isPending}
                  onClick={() => disburseMutation.mutate(agentId)}
                >
                  {disburseMutation.isPending ? 'Disbursing…' : 'Confirm disbursement'}
                </Button>
              </div>
            ) : null}
            {agentId && !previewQuery.isLoading && previewQuery.isError ? (
              <p className="text-sm text-danger">{errorMessage(previewQuery.error)}</p>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base">Accrued commissions</CardTitle>
            <Button type="button" variant="outline" size="sm" disabled={commissionRows.length === 0} onClick={exportCommissions}>
              Export CSV
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            {commissionsQuery.isLoading ? (
              <LoadingBlock />
            ) : commissionRows.length === 0 ? (
              <div className="p-6">
                <EmptyState message="No accrued commission lines for this page." />
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Agent</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Accrued</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {commissionRows.map((row) => (
                    <TableRow key={String(row.id)}>
                      <TableCell>{String(row.agentCode ?? '—')}</TableCell>
                      <TableCell className="text-ink-muted">{String(row.transactionType ?? '—')}</TableCell>
                      <TableCell>{money(asNumber(row.amount))}</TableCell>
                      <TableCell>
                        <Badge variant="secondary">{String(row.status ?? '—')}</Badge>
                      </TableCell>
                      <TableCell className="text-ink-muted">{formatInstant(row.accruedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      {commissionMeta.totalPages > 1 ? (
        <Pager page={commissionPage} meta={commissionMeta} onPrev={() => setCommissionPage((p) => p - 1)} onNext={() => setCommissionPage((p) => p + 1)} />
      ) : null}

      <Card className="mt-6">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">Disbursement history</CardTitle>
          <Button type="button" variant="outline" size="sm" disabled={disbursementRows.length === 0} onClick={exportDisbursements}>
            Export CSV
          </Button>
        </CardHeader>
        <CardContent className="p-0">
          {disbursementsQuery.isLoading ? (
            <LoadingBlock />
          ) : disbursementRows.length === 0 ? (
            <div className="p-6">
              <EmptyState message="No disbursements recorded yet." />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reference</TableHead>
                  <TableHead>Agent</TableHead>
                  <TableHead>Gross</TableHead>
                  <TableHead>Recovered</TableHead>
                  <TableHead>Net</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {disbursementRows.map((row) => (
                  <TableRow key={String(row.id)}>
                    <TableCell className="font-mono text-xs">{String(row.reference ?? '—')}</TableCell>
                    <TableCell>{String(row.agentCode ?? '—')}</TableCell>
                    <TableCell>{money(asNumber(row.grossCommission))}</TableCell>
                    <TableCell>
                      {money(asNumber(row.interestRecovered) + asNumber(row.principalRecovered))}
                    </TableCell>
                    <TableCell className="font-semibold">{money(asNumber(row.netCommission))}</TableCell>
                    <TableCell className="text-ink-muted">{formatInstant(row.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {disbursementMeta.totalPages > 1 ? (
        <Pager
          page={disbursementPage}
          meta={disbursementMeta}
          onPrev={() => setDisbursementPage((p) => p - 1)}
          onNext={() => setDisbursementPage((p) => p + 1)}
        />
      ) : null}
    </div>
  )
}

function Token({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-ink-muted">{label}</dt>
      <dd className={highlight ? 'font-semibold text-forest' : 'text-ink'}>{value}</dd>
    </div>
  )
}

function Pager({
  page,
  meta,
  onPrev,
  onNext,
}: {
  page: number
  meta: ReturnType<typeof pageMeta>
  onPrev: () => void
  onNext: () => void
}) {
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-ink-muted">
      <span>
        Page {meta.page + 1} of {meta.totalPages}
      </span>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="outline" disabled={page <= 0} onClick={onPrev}>
          Previous
        </Button>
        <Button type="button" size="sm" variant="outline" disabled={page + 1 >= meta.totalPages} onClick={onNext}>
          Next
        </Button>
      </div>
    </div>
  )
}