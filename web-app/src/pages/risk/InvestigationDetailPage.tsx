import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { riskApi } from '@/api/endpoints'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { money } from '@/lib/utils'
import { formatInstant, normalizeInvestigationDetail, reviewStatusVariant, riskLevelVariant, selectClass } from './riskHelpers'

const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'ESCALATED'] as const
const OUTCOMES = ['CONFIRMED_FRAUD', 'POLICY_BREACH', 'NO_FRAUD_FOUND', 'INSUFFICIENT_EVIDENCE'] as const
const ACTION_OPTIONS = [
  'WARNING_ISSUED',
  'AGENT_REINSTATED',
  'AGENT_FROZEN',
  'AGENT_SUSPENDED',
  'DAILY_LIMIT_REDUCED',
  'CUSTOMER_BLOCKED',
  'ESCALATED_TO_COMPLIANCE',
] as const

export function InvestigationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const [statusNote, setStatusNote] = useState('')
  const [nextStatus, setNextStatus] = useState<(typeof STATUS_OPTIONS)[number]>('IN_PROGRESS')
  const [outcomeOpen, setOutcomeOpen] = useState(false)
  const [outcome, setOutcome] = useState<(typeof OUTCOMES)[number]>('NO_FRAUD_FOUND')
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [actions, setActions] = useState<string[]>([])

  const q = useQuery({
    queryKey: ['risk', 'investigation', id],
    queryFn: async () => normalizeInvestigationDetail(await riskApi.investigation(id!)),
    enabled: Boolean(id),
  })

  const noteMut = useMutation({
    mutationFn: () => riskApi.addNote(id!, note.trim()),
    onSuccess: () => {
      toast.success('Note added')
      setNote('')
      void qc.invalidateQueries({ queryKey: ['risk', 'investigation', id] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const statusMut = useMutation({
    mutationFn: () => riskApi.updateStatus(id!, nextStatus, statusNote.trim() || undefined),
    onSuccess: () => {
      toast.success('Status updated')
      setStatusNote('')
      void qc.invalidateQueries({ queryKey: ['risk', 'investigation', id] })
      void qc.invalidateQueries({ queryKey: ['risk', 'investigations'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const outcomeMut = useMutation({
    mutationFn: () =>
      riskApi.outcome(id!, {
        outcome,
        resolutionNotes: resolutionNotes.trim(),
        actions,
      }),
    onSuccess: () => {
      toast.success('Investigation closed')
      setOutcomeOpen(false)
      setResolutionNotes('')
      setActions([])
      void qc.invalidateQueries({ queryKey: ['risk', 'investigation', id] })
      void qc.invalidateQueries({ queryKey: ['risk', 'investigations'] })
      void qc.invalidateQueries({ queryKey: ['risk', 'dashboard'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (!id) return <ErrorAlert error={new Error('Missing investigation id')} />
  if (q.isLoading) return <LoadingBlock label="Loading investigation…" />
  if (q.isError) return <ErrorAlert error={q.error} />
  if (!q.data) return null

  const inv = q.data
  const closed = inv.status === 'CLOSED'

  const toggleAction = (action: string) => {
    setActions((prev) => (prev.includes(action) ? prev.filter((a) => a !== action) : [...prev, action]))
  }

  return (
    <div>
      <PageHeader
        title={`${inv.caseNumber} · ${inv.title}`}
        description={inv.description ?? 'Investigation case file'}
        actions={
          <Button type="button" variant="outline" onClick={() => navigate('/risk/investigations')}>
            Back to list
          </Button>
        }
      />

      <div className="mb-6 flex flex-wrap gap-2">
        <Badge variant={riskLevelVariant(inv.severity)}>{inv.severity}</Badge>
        <Badge variant="secondary">{inv.status}</Badge>
        {inv.outcome ? <Badge variant="info">{inv.outcome}</Badge> : null}
      </div>

      <div className="mb-6 grid gap-4 sm:grid-cols-3 text-sm">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-xs text-ink-muted">Agent</CardTitle>
          </CardHeader>
          <CardContent>{inv.agentCode}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-xs text-ink-muted">Assignee</CardTitle>
          </CardHeader>
          <CardContent>{inv.assigneeName ?? 'Unassigned'}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-xs text-ink-muted">Opened</CardTitle>
          </CardHeader>
          <CardContent>{formatInstant(inv.openedAt)}</CardContent>
        </Card>
      </div>

      {inv.linkedFraudChecks && inv.linkedFraudChecks.length > 0 ? (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">Linked fraud alerts</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reference</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Review</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {inv.linkedFraudChecks.map((fc) => (
                  <TableRow key={fc.fraudCheckId}>
                    <TableCell>
                      <Link to={`/risk/fraud/${fc.fraudCheckId}`} className="font-mono text-xs text-forest hover:underline">
                        {fc.reference}
                      </Link>
                    </TableCell>
                    <TableCell>{money(fc.amount, fc.currency)}</TableCell>
                    <TableCell>
                      <Badge variant={reviewStatusVariant(fc.reviewStatus)}>{fc.reviewStatus}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Notes</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {inv.notes.length === 0 ? <p className="text-sm text-ink-muted">No notes yet.</p> : null}
            <ul className="space-y-3">
              {inv.notes.map((n) => (
                <li key={n.id} className="rounded-lg border border-line bg-sand/30 p-3">
                  <p className="text-sm">{n.note}</p>
                  <p className="mt-1 text-xs text-ink-muted">
                    {n.authorName ?? 'Staff'} · {formatInstant(n.createdAt)}
                  </p>
                </li>
              ))}
            </ul>
            {!closed ? (
              <div className="space-y-2">
                <Label htmlFor="note">Add note</Label>
                <Textarea id="note" rows={3} value={note} onChange={(e) => setNote(e.target.value)} />
                <Button type="button" disabled={note.trim().length === 0 || noteMut.isPending} onClick={() => noteMut.mutate()}>
                  {noteMut.isPending ? 'Saving…' : 'Add note'}
                </Button>
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Case actions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {!closed ? (
              <div className="space-y-3">
                <p className="text-sm font-medium">Update status</p>
                <select
                  className={selectClass}
                  value={nextStatus}
                  onChange={(e) => setNextStatus(e.target.value as (typeof STATUS_OPTIONS)[number])}
                >
                  {STATUS_OPTIONS.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
                <Textarea
                  rows={2}
                  placeholder="Optional status note"
                  value={statusNote}
                  onChange={(e) => setStatusNote(e.target.value)}
                />
                <Button type="button" variant="secondary" disabled={statusMut.isPending} onClick={() => statusMut.mutate()}>
                  {statusMut.isPending ? 'Updating…' : 'Update status'}
                </Button>
              </div>
            ) : (
              <p className="text-sm text-ink-muted">This case is closed.</p>
            )}

            {!closed ? (
              <Dialog open={outcomeOpen} onOpenChange={setOutcomeOpen}>
                <DialogTrigger asChild>
                  <Button type="button" variant="default">
                    Record outcome
                  </Button>
                </DialogTrigger>
                <DialogContent className="max-w-lg">
                  <DialogHeader>
                    <DialogTitle>Close investigation</DialogTitle>
                    <DialogDescription>Select outcome, resolution notes, and optional remedial actions.</DialogDescription>
                  </DialogHeader>
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="outcome">Outcome</Label>
                      <select
                        id="outcome"
                        className={selectClass}
                        value={outcome}
                        onChange={(e) => setOutcome(e.target.value as (typeof OUTCOMES)[number])}
                      >
                        {OUTCOMES.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="resolutionNotes">Resolution notes</Label>
                      <Textarea
                        id="resolutionNotes"
                        rows={4}
                        value={resolutionNotes}
                        onChange={(e) => setResolutionNotes(e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Optional actions</Label>
                      <div className="flex flex-wrap gap-2">
                        {ACTION_OPTIONS.map((a) => (
                          <Button
                            key={a}
                            type="button"
                            size="sm"
                            variant={actions.includes(a) ? 'default' : 'outline'}
                            onClick={() => toggleAction(a)}
                          >
                            {a.replaceAll('_', ' ')}
                          </Button>
                        ))}
                      </div>
                    </div>
                    <Button
                      type="button"
                      disabled={resolutionNotes.trim().length === 0 || outcomeMut.isPending}
                      onClick={() => outcomeMut.mutate()}
                    >
                      {outcomeMut.isPending ? 'Closing…' : 'Close case'}
                    </Button>
                  </div>
                </DialogContent>
              </Dialog>
            ) : null}

            {inv.resolutionNotes ? (
              <div className="rounded-lg border border-line p-3 text-sm">
                <p className="text-xs font-semibold uppercase text-ink-muted">Resolution</p>
                <p className="mt-1">{inv.resolutionNotes}</p>
              </div>
            ) : null}
            {inv.actions && inv.actions.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {inv.actions.map((a) => (
                  <Badge key={a} variant="secondary">
                    {a}
                  </Badge>
                ))}
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}