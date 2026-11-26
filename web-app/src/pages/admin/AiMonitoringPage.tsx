import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { adminApi } from '@/api/endpoints'
import { AiBadge } from '@/components/AiBadge'
import { ErrorAlert } from '@/components/ErrorAlert'
import { InsightPanel } from '@/components/InsightPanel'
import { LoadingBlock } from '@/components/LoadingBlock'
import { MetricCard } from '@/components/MetricCard'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { aiStatusExplanation, asPredictions } from './adminHelpers'
import type { AiPredictionItem, AiStatus } from './adminTypes'

type ModelRow = Record<string, unknown>

export function AiMonitoringPage() {
  const statusQ = useQuery({
    queryKey: ['admin', 'ai-status'],
    queryFn: () => adminApi.aiStatus() as Promise<AiStatus>,
  })
  const modelsQ = useQuery({
    queryKey: ['admin', 'ai-models'],
    queryFn: () => adminApi.aiModels() as Promise<ModelRow[]>,
  })
  const predictionsQ = useQuery({
    queryKey: ['admin', 'ai-predictions'],
    queryFn: () => adminApi.aiPredictions({ limit: 50 }),
  })
  const summaryQ = useQuery({
    queryKey: ['admin', 'ai-summary'],
    queryFn: adminApi.aiSummary,
    retry: false,
  })

  const loading = statusQ.isLoading || modelsQ.isLoading || predictionsQ.isLoading
  if (loading) return <LoadingBlock label="Loading AI governance…" />

  const status = statusQ.data
  const models = Array.isArray(modelsQ.data) ? modelsQ.data : []
  const predictions = asPredictions(predictionsQ.data)
  const byKind = aggregateByKind(predictions)

  return (
    <div>
      <PageHeader
        title="AI monitoring"
        description="Service health, registered models, and recent predictions. Values here are model outputs — not ledger balances."
        actions={<AiBadge label="AI governance" />}
      />
      <ErrorAlert error={statusQ.error ?? modelsQ.error ?? predictionsQ.error} />
      {status ? (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard label="Active source" value={status.activeSource} />
            <MetricCard label="Reachable" value={status.reachable ? 'Yes' : 'No'} tone={status.reachable ? 'success' : 'warning'} />
            <MetricCard label="Config enabled" value={status.configEnabled ? 'Yes' : 'No'} />
            <MetricCard label="Setting enabled" value={status.settingEnabled ? 'Yes' : 'No'} />
          </div>
          <div className="mb-6">
            <InsightPanel explanation={aiStatusExplanation(status)} />
          </div>
        </>
      ) : null}
      {byKind.length > 0 ? (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-sm text-ink-muted">Predictions by kind (recent sample)</CardTitle>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={byKind}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="kind" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="count" fill="#00539d" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      ) : null}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-sm text-ink-muted">Model registry</CardTitle>
        </CardHeader>
        <CardContent>
          {models.length === 0 ? (
            <p className="text-sm text-ink-muted">No models returned.</p>
          ) : (
            <div className="overflow-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Model</TableHead>
                    <TableHead>Version</TableHead>
                    <TableHead>Method</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {models.map((m, i) => (
                    <TableRow key={String(m.id ?? m.name ?? i)}>
                      <TableCell>{String(m.name ?? m.kind ?? '—')}</TableCell>
                      <TableCell className="font-mono text-xs">{String(m.version ?? '—')}</TableCell>
                      <TableCell className="text-xs">{String(m.method ?? m.algorithm ?? '—')}</TableCell>
                      <TableCell>
                        <Badge variant="secondary">{String(m.status ?? m.health ?? '—')}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-sm text-ink-muted">Recent predictions</CardTitle>
        </CardHeader>
        <CardContent>
          {predictions.length === 0 ? (
            <p className="text-sm text-ink-muted">No predictions in store.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>Kind</TableHead>
                  <TableHead>Subject</TableHead>
                  <TableHead>Score</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {predictions.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-mono text-xs">{p.id}</TableCell>
                    <TableCell>{p.kind ?? '—'}</TableCell>
                    <TableCell className="text-xs">{p.subject_id ?? '—'}</TableCell>
                    <TableCell>{p.score != null ? p.score.toFixed(3) : '—'}</TableCell>
                    <TableCell className="text-xs text-ink-muted">{p.created_at ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
      {summaryQ.data ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm text-ink-muted">Governance summary</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="max-h-64 overflow-auto rounded-md bg-sand/50 p-3 text-xs">{JSON.stringify(summaryQ.data, null, 2)}</pre>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

function aggregateByKind(items: AiPredictionItem[]) {
  const counts = new Map<string, number>()
  for (const p of items) {
    const kind = p.kind ?? 'UNKNOWN'
    counts.set(kind, (counts.get(kind) ?? 0) + 1)
  }
  return [...counts.entries()].map(([kind, count]) => ({ kind, count }))
}