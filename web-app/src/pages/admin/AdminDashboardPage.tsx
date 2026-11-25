import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi, sharedApi } from '@/api/endpoints'
import { ErrorAlert } from '@/components/ErrorAlert'
import { InsightPanel } from '@/components/InsightPanel'
import { LoadingBlock } from '@/components/LoadingBlock'
import { MetricCard } from '@/components/MetricCard'
import { PageHeader } from '@/components/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { aiStatusExplanation, asPageContent } from './adminHelpers'
import type { AdminUser, AgentSummary, AiStatus, SimulationRun } from './adminTypes'
import type { PageResponse } from '@/types/api'

const SHORTCUTS = [
  { to: '/admin/users', title: 'Users', description: 'Staff accounts and access' },
  { to: '/admin/agents', title: 'Agents', description: 'Onboard and tune limits' },
  { to: '/admin/areas', title: 'Areas', description: 'Regional structure' },
  { to: '/admin/config', title: 'Settings & tariffs', description: 'Policy and fee book' },
  { to: '/admin/simulations', title: 'Simulations', description: 'Synthetic market runs' },
  { to: '/admin/audit', title: 'Audit logs', description: 'Immutable activity trail' },
  { to: '/admin/demo', title: 'Demo reset', description: 'Rebuild demonstration data' },
  { to: '/admin/ai', title: 'AI monitoring', description: 'Models and predictions' },
] as const

export function AdminDashboardPage() {
  const usersQ = useQuery({
    queryKey: ['admin', 'users', 'dash'],
    queryFn: () => adminApi.users({ size: 100 }) as Promise<PageResponse<AdminUser>>,
  })
  const agentsQ = useQuery({
    queryKey: ['admin', 'agents', 'dash'],
    queryFn: () => sharedApi.agents() as Promise<AgentSummary[]>,
  })
  const settingsQ = useQuery({ queryKey: ['admin', 'settings'], queryFn: adminApi.settings })
  const aiQ = useQuery({
    queryKey: ['admin', 'ai-status'],
    queryFn: () => adminApi.aiStatus() as Promise<AiStatus>,
  })
  const aiSummaryQ = useQuery({
    queryKey: ['admin', 'ai-summary'],
    queryFn: adminApi.aiSummary,
    retry: false,
  })
  const simsQ = useQuery({
    queryKey: ['admin', 'simulations', 'recent'],
    queryFn: () => adminApi.simulations({ size: 5 }) as Promise<PageResponse<SimulationRun>>,
  })

  const loading = usersQ.isLoading || agentsQ.isLoading || settingsQ.isLoading || aiQ.isLoading
  const error = usersQ.error ?? agentsQ.error ?? settingsQ.error ?? aiQ.error

  if (loading) return <LoadingBlock label="Loading admin dashboard…" />

  const users = usersQ.data?.content ?? asPageContent<AdminUser>(usersQ.data)
  const agents = Array.isArray(agentsQ.data) ? agentsQ.data : []
  const settings = settingsQ.data ?? []
  const ai = aiQ.data
  const recentSims = simsQ.data?.content ?? asPageContent<SimulationRun>(simsQ.data)

  return (
    <div>
      <PageHeader title="Administration" description="Platform health, policy shortcuts, and recent simulation activity." />
      <ErrorAlert error={error} />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Staff users"
          value={users.length}
          kind="derived"
          hint={`${usersQ.data?.totalElements ?? users.length} total`}
        />
        <MetricCard label="Agents" value={agents.length} kind="derived" />
        <MetricCard
          label="AI source"
          value={ai?.activeSource ?? '—'}
          tone={ai?.reachable ? 'success' : 'warning'}
          kind="derived"
          hint={ai?.reachable ? 'Service reachable' : 'Using fallbacks'}
        />
        <MetricCard label="Runtime settings" value={settings.length} kind="derived" />
      </div>
      {ai ? (
        <div className="mb-6">
          <InsightPanel
            title="AI platform status"
            source="ai"
            explanation={aiStatusExplanation(ai)}
            footer="Reachability and active model source from the AI service health API."
          />
        </div>
      ) : null}
      {aiSummaryQ.data && typeof aiSummaryQ.data === 'object' ? (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-sm text-ink-muted">Governance summary</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="max-h-48 overflow-auto rounded-md bg-sand/50 p-3 text-xs text-ink">
              {JSON.stringify(aiSummaryQ.data, null, 2)}
            </pre>
          </CardContent>
        </Card>
      ) : null}
      <div className="mb-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {SHORTCUTS.map((s) => (
          <Link key={s.to} to={s.to} className="block">
            <Card className="h-full transition-shadow hover:shadow-md">
              <CardHeader className="pb-2">
                <CardTitle className="text-base">{s.title}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-ink-muted">{s.description}</p>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
      {recentSims.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm text-ink-muted">Recent simulations</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2 text-sm">
              {recentSims.map((run) => (
                <li key={run.id} className="flex flex-wrap justify-between gap-2 border-b border-line pb-2 last:border-0">
                  <span className="font-medium">{run.scenario}</span>
                  <span className="text-ink-muted">
                    {run.status} · {run.transactionsCompleted}/{run.transactionsAttempted} completed
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}