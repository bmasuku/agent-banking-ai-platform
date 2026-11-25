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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
                    <TableCell>{a.flaggedTransactions ?? 0}</TableCell>
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
              Recent-window anomaly detection from the risk API. Model scores — not ledger balances.
            </DialogDescription>
          </DialogHeader>
          {anomaliesQ.isLoading ? <LoadingBlock label="Running anomaly detection…" /> : null}
          {anomaliesQ.isError ? <ErrorAlert error={anomaliesQ.error} /> : null}
          {anomaliesQ.data ? (
            <div className="space-y-4">
              {anomalySummary.highlights.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {anomalySummary.highlights.map((h) => (
                    <Badge key={h} variant="warning">
                      {h}
                    </Badge>
                  ))}
                </div>
              ) : null}
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">Raw response</CardTitle>
                </CardHeader>
                <CardContent>
                  <pre className="max-h-80 overflow-auto rounded-md bg-sand/50 p-3 text-xs">{anomalySummary.pretty}</pre>
                </CardContent>
              </Card>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function formatAnomalyPayload(data: unknown): { pretty: string; highlights: string[] } {
  if (data == null || typeof data !== 'object') {
    return { pretty: String(data ?? ''), highlights: [] }
  }
  const obj = data as Record<string, unknown>
  const highlights: string[] = []
  const score = obj.anomalyScore ?? obj.score
  const flagged = obj.flagged
  const summary = obj.summary ?? obj.explanation
  if (typeof score === 'number') highlights.push(`Score ${score}`)
  if (typeof flagged === 'boolean') highlights.push(flagged ? 'Flagged' : 'Not flagged')
  if (typeof summary === 'string' && summary.trim()) highlights.push(summary.slice(0, 120))
  const reasons = obj.reasons ?? obj.anomalies
  if (Array.isArray(reasons)) {
    for (const r of reasons.slice(0, 3)) {
      if (typeof r === 'string') highlights.push(r)
      else if (r && typeof r === 'object' && 'label' in r) highlights.push(String((r as { label: unknown }).label))
    }
  }
  return { pretty: JSON.stringify(data, null, 2), highlights }
}