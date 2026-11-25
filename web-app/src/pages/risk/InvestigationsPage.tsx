import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { riskApi } from '@/api/endpoints'
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
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import type { RiskLevel } from '@/types/api'
import { formatInstant, riskLevelVariant, selectClass } from './riskHelpers'

const STATUSES = ['', 'OPEN', 'IN_PROGRESS', 'ESCALATED', 'CLOSED'] as const
const SEVERITIES: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

type AgentOption = { id: string; agentCode: string; businessName?: string }

export function InvestigationsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [status, setStatus] = useState<(typeof STATUSES)[number]>('')
  const [createOpen, setCreateOpen] = useState(false)
  const [agentId, setAgentId] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [severity, setSeverity] = useState<RiskLevel>('MEDIUM')

  const params: Record<string, unknown> = { page: 0, size: 50 }
  if (status) params.status = status

  const listQ = useQuery({
    queryKey: ['risk', 'investigations', params],
    queryFn: () => riskApi.investigations(params),
  })

  const agentsQ = useQuery({
    queryKey: ['agents', 'pick-list'],
    queryFn: async () => {
      const data = await riskApi.agents()
      return data.map((a) => ({
        id: a.agentId,
        agentCode: a.agentCode,
        businessName: a.businessName,
      })) satisfies AgentOption[]
    },
    enabled: createOpen,
  })

  const createMut = useMutation({
    mutationFn: () =>
      riskApi.createInvestigation({
        agentId,
        title: title.trim(),
        description: description.trim(),
        severity,
      }),
    onSuccess: (inv) => {
      toast.success('Investigation created')
      setCreateOpen(false)
      setTitle('')
      setDescription('')
      setAgentId('')
      void qc.invalidateQueries({ queryKey: ['risk', 'investigations'] })
      navigate(`/risk/investigations/${inv.id}`)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <div>
      <PageHeader
        title="Investigations"
        description="Open cases, escalations, and closed outcomes."
        actions={
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button type="button">New investigation</Button>
            </DialogTrigger>
            <DialogContent className="max-w-lg">
              <DialogHeader>
                <DialogTitle>Create investigation</DialogTitle>
                <DialogDescription>Open a manual case against an agent.</DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="agentId">Agent</Label>
                  <select
                    id="agentId"
                    className={selectClass}
                    value={agentId}
                    onChange={(e) => setAgentId(e.target.value)}
                  >
                    <option value="">Select agent…</option>
                    {(agentsQ.data ?? []).map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.agentCode} · {a.businessName ?? 'Agent'}
                      </option>
                    ))}
                  </select>
                  {agentsQ.isError ? (
                    <p className="text-xs text-danger">Could not load agents; try risk agent list or shared directory.</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Title</Label>
                  <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="description">Description</Label>
                  <Textarea
                    id="description"
                    rows={4}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="severity">Severity</Label>
                  <select
                    id="severity"
                    className={selectClass}
                    value={severity}
                    onChange={(e) => setSeverity(e.target.value as RiskLevel)}
                  >
                    {SEVERITIES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </div>
                <Button
                  type="button"
                  disabled={
                    !agentId || title.trim().length === 0 || description.trim().length === 0 || createMut.isPending
                  }
                  onClick={() => createMut.mutate()}
                >
                  {createMut.isPending ? 'Creating…' : 'Create case'}
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        }
      />

      <Card className="mb-4">
        <CardContent className="grid gap-4 pt-6 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="status">Status</Label>
            <select
              id="status"
              className={selectClass}
              value={status}
              onChange={(e) => setStatus(e.target.value as (typeof STATUSES)[number])}
            >
              {STATUSES.map((s) => (
                <option key={s || 'all'} value={s}>
                  {s || 'All statuses'}
                </option>
              ))}
            </select>
          </div>
        </CardContent>
      </Card>

      {listQ.isLoading ? <LoadingBlock /> : null}
      {listQ.isError ? <ErrorAlert error={listQ.error} /> : null}

      {listQ.data && listQ.data.content.length === 0 ? (
        <EmptyState title="No investigations" message="No cases match the selected status filter." />
      ) : null}

      {listQ.data && listQ.data.content.length > 0 ? (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Case</TableHead>
                  <TableHead>Title</TableHead>
                  <TableHead>Agent</TableHead>
                  <TableHead>Severity</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Opened</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {listQ.data.content.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    onClick={() => navigate(`/risk/investigations/${row.id}`)}
                  >
                    <TableCell className="font-mono text-xs">{row.caseNumber}</TableCell>
                    <TableCell>{row.title}</TableCell>
                    <TableCell>{row.agentCode}</TableCell>
                    <TableCell>
                      <Badge variant={riskLevelVariant(row.severity)}>{row.severity}</Badge>
                    </TableCell>
                    <TableCell>{row.status}</TableCell>
                    <TableCell className="text-xs text-ink-muted">{formatInstant(row.openedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}