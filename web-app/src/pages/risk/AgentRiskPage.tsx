import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { riskApi } from '@/api/endpoints'
import { AiBadge } from '@/components/AiBadge'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { riskLevelVariant, selectClass } from './riskHelpers'

const AGENT_STATUSES = ['ACTIVE', 'UNDER_REVIEW', 'SUSPENDED', 'FROZEN'] as const

export function AgentRiskPage() {
  const qc = useQueryClient()
  const [statusAgentId, setStatusAgentId] = useState<string | null>(null)
  const [newStatus, setNewStatus] = useState<(typeof AGENT_STATUSES)[number]>('UNDER_REVIEW')
  const [reason, setReason] = useState('')
  const [anomalyAgentId, setAnomalyAgentId] = useState<string | null>(null)
  const [anomalyAgentCode, setAnomalyAgentCode] = useState('')

  const listQ = useQuery({
    queryKey: ['risk', 'agents'],
    queryFn: () => riskApi.agents(),
  })

  const anomaliesQ = useQuery({
    queryKey: ['risk', 'anomalies', anomalyAgentId],
    queryFn: () => riskApi.anomalies(anomalyAgentId!),
    enabled: Boolean(anomalyAgentId),
  })

  const statusMut = useMutation({
    mutationFn: () => riskApi.setAgentStatus(statusAgentId!, newStatus, reason.trim()),
    onSuccess: () => {
      toast.success('Agent status updated')
      setStatusAgentId(null)
      setReason('')
      void qc.invalidateQueries({ queryKey: ['risk', 'agents'] })
      void qc.invalidateQueries({ queryKey: ['risk', 'dashboard'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const openAnomalies = (agentId: string, agentCode: string) => {
    setAnomalyAgentId(agentId)
    setAnomalyAgentCode(agentCode)
  }

  const anomalySummary = formatAnomalyPayload(anomaliesQ.data)

  return (
    <div>
      <PageHeader title="Agent risk" description="Ranked agent exposure, controls, and AI anomaly signals." />

      {listQ.isLoading ? <LoadingBlock /> : null}
      {listQ.isError ? <ErrorAlert error={listQ.error} /> : null}

      {listQ.data && listQ.data.length === 0 ? (
        <EmptyState title="No agents" message="No agents returned from the risk overview." />
      ) : null}

      {listQ.data && listQ.data.length > 0 ? (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Agent</TableHead>
                  <TableHead>Area</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Risk</TableHead>
                  <TableHead>Credit</TableHead>
                  <TableHead>Flagged</TableHead>
                  <TableHead>Cases</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {listQ.data.map((a) => (
                  <TableRow key={a.agentId}>
                    <TableCell>
                      <div className="font-medium">{a.businessName}</div>
                      <div className="text-xs text-ink-muted">{a.agentCode}</div>
                    </TableCell>
                    <TableCell>{a.areaCode ?? '—'}</TableCell>
                    <TableCell>{a.status}</TableCell>
                    <TableCell>
                      <Badge variant={riskLevelVariant(a.riskLevel)}>{a.riskLevel}</Badge>
                    </TableCell>
                    <TableCell>{a.creditScore ?? '—'}</TableCell>
                    <TableCell>{a.flaggedTransactions30d ?? a.flaggedTransactions ?? 0}</TableCell>
                    <TableCell>{a.openInvestigations ?? 0}</TableCell>
                    <TableCell className="space-x-2 text-right">
                      <Button type="button" size="sm" variant="outline" onClick={() => setStatusAgentId(a.agentId)}>
                        Set status
                      </Button>
                      <Button type="button" size="sm" variant="secondary" onClick={() => openAnomalies(a.agentId, a.agentCode)}>
                        Anomalies
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={statusAgentId != null} onOpenChange={(open) => !open && setStatusAgentId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Update agent status</DialogTitle>
            <DialogDescription>Apply ACTIVE, UNDER_REVIEW, SUSPENDED, or FROZEN with a documented reason.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="agentStatus">Status</Label>
              <select
                id="agentStatus"
                className={selectClass}
                value={newStatus}
                onChange={(e) => setNewStatus(e.target.value as (typeof AGENT_STATUSES)[number])}
              >
                {AGENT_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="statusReason">Reason</Label>
              <Textarea id="statusReason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
            </div>
            <Button
              type="button"
              disabled={!statusAgentId || reason.trim().length === 0 || statusMut.isPending}
              onClick={() => statusMut.mutate()}
            >
              {statusMut.isPending ? 'Saving…' : 'Apply status'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={anomalyAgentId != null} onOpenChange={(open) => !open && setAnomalyAgentId(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <div className="flex items-center gap-2 pr-6">
              <DialogTitle>AI anomalies · {anomalyAgentCode}</DialogTitle>
              <AiBadge />
            </div>
            <DialogDescription>
              Behavioural anomaly detection plus open fraud alerts for this agent. Model scores — not ledger balances.
            </DialogDescription>
          </DialogHeader>
          {anomaliesQ.isLoading ? <LoadingBlock label="Running anomaly detection…" /> : null}
          {anomaliesQ.isError ? <ErrorAlert error={anomaliesQ.error} /> : null}
          {anomaliesQ.data ? (
            <div className="space-y-4">
              <div className="flex flex-wrap gap-2">
                {anomalySummary.highlights.map((h) => (
                  <Badge key={h} variant="warning">
                    {h}
                  </Badge>
                ))}
              </div>
              {anomalySummary.explanation ? (
                <p className="text-sm leading-relaxed text-ink">{anomalySummary.explanation}</p>
              ) : null}
              {anomalySummary.signals.length === 0 ? (
                <EmptyState
                  title="No anomaly signals"
                  message="No behavioural anomalies or fraud alerts were returned for the recent window."
                />
              ) : (
                <div className="max-h-80 space-y-3 overflow-y-auto pr-1">
                  {anomalySummary.signals.map((s, idx) => (
                    <Card key={`${s.type}-${idx}`}>
                      <CardContent className="space-y-2 pt-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant={riskLevelVariant((s.severity as 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL') || 'MEDIUM')}>
                            {s.severity || 'SIGNAL'}
                          </Badge>
                          <span className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{s.type}</span>
                          {s.score != null ? (
                            <span className="text-xs text-ink-muted">Score {Number(s.score).toFixed(2)}</span>
                          ) : null}
                        </div>
                        <p className="text-sm leading-relaxed text-ink">{s.description}</p>
                        {s.reference ? <p className="text-xs text-ink-muted">Txn {s.reference}</p> : null}
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
              <details className="rounded-md border border-line bg-sand/40 p-3">
                <summary className="cursor-pointer text-sm font-medium text-ink-muted">Raw response</summary>
                <pre className="mt-2 max-h-48 overflow-auto text-xs">{anomalySummary.pretty}</pre>
              </details>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

type AnomalySignal = {
  type: string
  severity?: string
  score?: number
  description: string
  reference?: string
}

function formatAnomalyPayload(data: unknown): {
  pretty: string
  highlights: string[]
  explanation: string
  signals: AnomalySignal[]
} {
  if (data == null || typeof data !== 'object') {
    return { pretty: String(data ?? ''), highlights: [], explanation: '', signals: [] }
  }
  const obj = data as Record<string, unknown>
  const highlights: string[] = []
  const score = obj.anomaly_score ?? obj.anomalyScore ?? obj.score
  const risk = obj.risk_level ?? obj.riskLevel
  const explanation = typeof obj.explanation === 'string' ? obj.explanation : ''
  if (typeof score === 'number') highlights.push(`Score ${score}`)
  if (typeof risk === 'string' && risk.trim()) highlights.push(risk)

  const signals: AnomalySignal[] = []
  const pushSignal = (raw: unknown) => {
    if (!raw || typeof raw !== 'object') return
    const r = raw as Record<string, unknown>
    const description =
      (typeof r.description === 'string' && r.description) ||
      (typeof r.label === 'string' && r.label) ||
      (typeof r.explanation === 'string' && r.explanation) ||
      ''
    if (!description) return
    const evidence = r.evidence && typeof r.evidence === 'object' ? (r.evidence as Record<string, unknown>) : {}
    signals.push({
      type: String(r.type ?? 'SIGNAL'),
      severity: r.severity != null ? String(r.severity) : undefined,
      score: typeof r.score === 'number' ? r.score : undefined,
      description,
      reference:
        (typeof evidence.reference === 'string' && evidence.reference) ||
        (typeof r.reference === 'string' && r.reference) ||
        undefined,
    })
  }

  if (Array.isArray(obj.anomalies)) obj.anomalies.forEach(pushSignal)
  if (Array.isArray(obj.fraudAlerts)) {
    for (const alert of obj.fraudAlerts) {
      // Already merged into anomalies by backend; keep as fallback if only fraudAlerts present.
      if (!Array.isArray(obj.anomalies) || obj.anomalies.length === 0) pushSignal(alert)
    }
  }

  if (signals.length > 0) highlights.push(`${signals.length} signal(s)`)
  else if (explanation) highlights.push(explanation.slice(0, 100))

  return { pretty: JSON.stringify(data, null, 2), highlights, explanation, signals }
}