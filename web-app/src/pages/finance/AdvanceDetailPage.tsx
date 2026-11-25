import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { errorMessage } from '@/api/client'
import { financeApi, sharedApi } from '@/api/endpoints'
import { ErrorAlert } from '@/components/ErrorAlert'
import { InsightPanel } from '@/components/InsightPanel'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { money, newIdempotencyKey } from '@/lib/utils'
import { advanceStatusVariant, formatInstant, unwrapAdvanceDetail } from '@/pages/finance/helpers'
import type { AdvanceDetail, FeatureContribution } from '@/types/api'
import { toast } from 'sonner'

export function AdvanceDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [repayOpen, setRepayOpen] = useState(false)

  const [approveNote, setApproveNote] = useState('')
  const [approvedAmount, setApprovedAmount] = useState('')
  const [dailyRate, setDailyRate] = useState('')
  const [termDays, setTermDays] = useState('')
  const [rejectNote, setRejectNote] = useState('')
  const [repayAmount, setRepayAmount] = useState('')

  const advanceQuery = useQuery({
    queryKey: ['finance', 'advance', id],
    queryFn: () => financeApi.advance(id),
    enabled: Boolean(id),
  })

  const unwrapped = advanceQuery.data
    ? unwrapAdvanceDetail(advanceQuery.data as AdvanceDetail)
    : { advance: null as null, repayments: [] as Array<Record<string, unknown>> }
  const advance = unwrapped.advance
  const repayments = unwrapped.repayments

  const creditQuery = useQuery({
    queryKey: ['credit-score', advance?.agentId],
    queryFn: () => sharedApi.creditScore(advance!.agentId),
    enabled: Boolean(advance?.agentId),
  })

  const advanceRecord = (advance ?? {}) as Record<string, unknown>
  const rationaleRaw = advanceRecord.decisionRationale
  const rationale =
    rationaleRaw && typeof rationaleRaw === 'object' ? (rationaleRaw as Record<string, unknown>) : null
  const creditExplanation: string | undefined =
    typeof advanceRecord.creditExplanation === 'string'
      ? advanceRecord.creditExplanation
      : typeof rationale?.modelExplanation === 'string'
        ? rationale.modelExplanation
        : undefined

  const creditFactorsFromAdvance = advanceRecord.creditFactors
  const creditFactorsFromQuery = (creditQuery.data as { factors?: FeatureContribution[] } | undefined)?.factors
  const creditFactors: FeatureContribution[] = Array.isArray(creditFactorsFromAdvance)
    ? (creditFactorsFromAdvance as FeatureContribution[])
    : Array.isArray(creditFactorsFromQuery)
      ? creditFactorsFromQuery
      : []

  const latestCredit = creditQuery.data as
    | { score?: number; explanation?: string; riskBand?: string }
    | undefined
  const scoreAtRequest: string | number | undefined =
    typeof advance?.creditScoreAtRequest === 'number'
      ? advance.creditScoreAtRequest
      : typeof rationale?.creditScore === 'number' || typeof rationale?.creditScore === 'string'
        ? (rationale.creditScore as string | number)
        : undefined

  const approveMutation = useMutation({
    mutationFn: () => {
      const body: Record<string, unknown> = { note: approveNote.trim() }
      if (approvedAmount.trim()) body.approvedAmount = Number(approvedAmount)
      if (dailyRate.trim()) body.dailyInterestRate = Number(dailyRate)
      if (termDays.trim()) body.termDays = Number(termDays)
      return financeApi.approveAdvance(id, body)
    },
    onSuccess: () => {
      toast.success('Advance approved')
      setApproveOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['finance', 'advance', id] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const rejectMutation = useMutation({
    mutationFn: () => financeApi.rejectAdvance(id, rejectNote.trim()),
    onSuccess: () => {
      toast.success('Advance rejected')
      setRejectOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['finance', 'advance', id] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const repayMutation = useMutation({
    mutationFn: () => financeApi.repayAdvance(id, Number(repayAmount), newIdempotencyKey('REPAY')),
    onSuccess: () => {
      toast.success('Repayment recorded')
      setRepayOpen(false)
      setRepayAmount('')
      void queryClient.invalidateQueries({ queryKey: ['finance', 'advance', id] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (!id) {
    return <p className="text-sm text-danger">Missing advance id.</p>
  }

  if (advanceQuery.isLoading || !advance) {
    return <LoadingBlock label="Loading advance…" />
  }

  const canDecide = advance.status === 'PENDING'
  const canRepay = advance.status === 'ACTIVE' || advance.status === 'OVERDUE'

  return (
    <div>
      <PageHeader
        title={`Advance ${String(advance.reference ?? id)}`}
        description={`Agent ${String(advance.agentCode ?? '—')}${advance.agentName ? ` · ${advance.agentName}` : ''}`}
        actions={
          <Button type="button" variant="outline" size="sm" onClick={() => navigate('/finance/advances')}>
            Back to list
          </Button>
        }
      />

      <ErrorAlert error={advanceQuery.error} />

      {advanceQuery.isLoading ? (
        <LoadingBlock />
      ) : (
        <div className="space-y-6">
          <div>
            <Badge variant={advanceStatusVariant(advance.status)}>{String(advance.status ?? '—')}</Badge>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Stat label="Requested" value={money(advance.requestedAmount)} />
            <Stat label="Approved" value={advance.approvedAmount != null ? money(advance.approvedAmount) : '—'} />
            <Stat label="Principal outstanding" value={money(advance.principalOutstanding)} />
            <Stat label="Total outstanding" value={money(advance.totalOutstanding)} highlight />
          </div>

          <div className="flex flex-wrap gap-2">
            {canDecide ? (
              <>
                <Button type="button" size="sm" onClick={() => setApproveOpen(true)}>
                  Approve
                </Button>
                <Button type="button" size="sm" variant="danger" onClick={() => setRejectOpen(true)}>
                  Reject
                </Button>
              </>
            ) : null}
            {canRepay ? (
              <Button type="button" size="sm" variant="secondary" onClick={() => setRepayOpen(true)}>
                Record repayment
              </Button>
            ) : null}
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Credit at request</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <p>
                  Score at request:{' '}
                  <span className="font-semibold">{scoreAtRequest ?? '—'}</span>
                </p>
                {latestCredit?.score != null ? (
                  <p className="text-ink-muted">
                    Latest score: {latestCredit.score}
                    {latestCredit.riskBand ? ` (${latestCredit.riskBand})` : ''}
                  </p>
                ) : null}
                {creditExplanation ? (
                  <InsightPanel
                    title="Credit explanation"
                    source="ai"
                    explanation={creditExplanation}
                    footer="Explainable credit model output — not a ledger balance."
                  />
                ) : latestCredit?.explanation ? (
                  <InsightPanel
                    title="Latest credit explanation"
                    source="ai"
                    explanation={latestCredit.explanation}
                    footer="Explainable credit model output — not a ledger balance."
                  />
                ) : null}
                {creditFactors.length > 0 ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Factor</TableHead>
                        <TableHead>Value</TableHead>
                        <TableHead>Impact</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {creditFactors.map((f) => (
                        <TableRow key={f.feature}>
                          <TableCell>{f.feature}</TableCell>
                          <TableCell className="text-ink-muted">{f.value}</TableCell>
                          <TableCell>
                            {f.direction} ({f.contribution.toFixed(3)})
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : null}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Terms &amp; notes</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm text-ink-muted">
                <p>Daily rate: {advance.dailyInterestRate}</p>
                <p>Term: {advance.termDays} days</p>
                {advance.dueDate ? <p>Due: {String(advance.dueDate)}</p> : null}
                {advance.purpose ? <p>Purpose: {advance.purpose}</p> : null}
                {advance.decisionNote ? <p className="text-ink">Decision note: {advance.decisionNote}</p> : null}
                <p>Requested: {formatInstant(advance.requestedAt)}</p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Repayment history</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              {repayments.length === 0 ? (
                <p className="p-6 text-sm text-ink-muted">No repayments recorded.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Source</TableHead>
                      <TableHead>Principal</TableHead>
                      <TableHead>Interest</TableHead>
                      <TableHead>Total</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {repayments.map((r) => (
                      <TableRow key={String(r.id)}>
                        <TableCell>{String(r.source ?? '—')}</TableCell>
                        <TableCell>{money(Number(r.principalAmount ?? r.amount ?? 0))}</TableCell>
                        <TableCell>{money(Number(r.interestAmount ?? 0))}</TableCell>
                        <TableCell>{money(Number(r.totalAmount ?? r.amount ?? 0))}</TableCell>
                        <TableCell className="text-ink-muted">{formatInstant(r.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          <p className="text-xs text-ink-faint">
            Agent profile:{' '}
            <Link className="text-forest underline" to="/finance/statements">
              Open statements
            </Link>
          </p>
        </div>
      )}

      <Dialog open={approveOpen} onOpenChange={setApproveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve advance</DialogTitle>
            <DialogDescription>
              Disburse immediately after approval. Optional fields default to requested terms and model pricing.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Field label="Note (required)" required>
              <Textarea value={approveNote} onChange={(e) => setApproveNote(e.target.value)} rows={3} />
            </Field>
            <Field label="Approved amount (optional)">
              <Input type="number" min="0" step="0.01" value={approvedAmount} onChange={(e) => setApprovedAmount(e.target.value)} />
            </Field>
            <Field label="Daily interest rate (optional)">
              <Input type="number" min="0" step="0.000001" value={dailyRate} onChange={(e) => setDailyRate(e.target.value)} />
            </Field>
            <Field label="Term days (optional)">
              <Input type="number" min="1" max="90" value={termDays} onChange={(e) => setTermDays(e.target.value)} />
            </Field>
            <div className="flex justify-end gap-2">
              <DialogClose asChild>
                <Button type="button" variant="outline">
                  Cancel
                </Button>
              </DialogClose>
              <Button
                type="button"
                disabled={!approveNote.trim() || approveMutation.isPending}
                onClick={() => approveMutation.mutate()}
              >
                {approveMutation.isPending ? 'Approving…' : 'Confirm approval'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject advance</DialogTitle>
            <DialogDescription>Provide a decision note for the agent and audit trail.</DialogDescription>
          </DialogHeader>
          <Field label="Note (required)" required>
            <Textarea value={rejectNote} onChange={(e) => setRejectNote(e.target.value)} rows={3} />
          </Field>
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="outline">
                Cancel
              </Button>
            </DialogClose>
            <Button
              type="button"
              variant="danger"
              disabled={!rejectNote.trim() || rejectMutation.isPending}
              onClick={() => rejectMutation.mutate()}
            >
              {rejectMutation.isPending ? 'Rejecting…' : 'Confirm rejection'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={repayOpen} onOpenChange={setRepayOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Record repayment</DialogTitle>
            <DialogDescription>Off-platform repayment received into settlement bank.</DialogDescription>
          </DialogHeader>
          <Field label="Amount" required>
            <Input type="number" min="0.01" step="0.01" value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} />
          </Field>
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="outline">
                Cancel
              </Button>
            </DialogClose>
            <Button
              type="button"
              disabled={!repayAmount || Number(repayAmount) <= 0 || repayMutation.isPending}
              onClick={() => repayMutation.mutate()}
            >
              {repayMutation.isPending ? 'Saving…' : 'Record repayment'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Stat({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs text-ink-muted">{label}</p>
        <p className={highlight ? 'font-display text-xl font-semibold text-forest' : 'font-display text-xl font-semibold'}>
          {value}
        </p>
      </CardContent>
    </Card>
  )
}

function Field({
  label,
  children,
  required,
}: {
  label: string
  children: React.ReactNode
  required?: boolean
}) {
  return (
    <div className="space-y-2">
      <Label>
        {label}
        {required ? ' *' : ''}
      </Label>
      {children}
    </div>
  )
}
