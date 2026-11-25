import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts'
import { riskApi } from '@/api/endpoints'
import { ErrorAlert } from '@/components/ErrorAlert'
import { InsightPanel } from '@/components/InsightPanel'
import { LoadingBlock } from '@/components/LoadingBlock'
import { MetricCard } from '@/components/MetricCard'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { riskLevelVariant } from './riskHelpers'

const SEVERITY_COLORS = ['#b91c1c', '#c2410c', '#ca8a04', '#166534']

export function RiskDashboardPage() {
  const q = useQuery({ queryKey: ['risk', 'dashboard'], queryFn: () => riskApi.dashboard() })

  if (q.isLoading) return <LoadingBlock label="Loading risk dashboard…" />
  if (q.isError) return <ErrorAlert error={q.error} />
  if (!q.data) return null

  const data = q.data
  const severityData = Object.entries(data.investigationsBySeverity).map(([name, value]) => ({ name, value }))
  const topAgents = data.highestRiskAgents.slice(0, 5)
  const insightParts: string[] = []
  if (topAgents.length > 0) {
    const lead = topAgents[0]
    insightParts.push(
      `${lead.businessName} (${lead.agentCode}) is currently the highest-ranked risk agent at ${lead.riskLevel} level.`,
    )
  }
  insightParts.push(
    `${data.flaggedLast24h} transactions were flagged and ${data.blockedLast24h} were blocked in the last 24 hours.`,
  )
  if (data.pendingReviews > 0) {
    insightParts.push(`${data.pendingReviews} fraud alert(s) still need a review decision.`)
  }

  return (
    <div>
      <PageHeader
        title="Risk dashboard"
        description="Live fraud queue, open investigations, and agent risk concentration from the risk service."
        actions={
          <Link to="/risk/flagged" className="text-sm font-semibold text-forest hover:underline">
            Review flagged transactions →
          </Link>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Pending reviews"
          value={data.pendingReviews}
          tone={data.pendingReviews > 0 ? 'warning' : 'default'}
          kind="derived"
        />
        <MetricCard label="Open investigations" value={data.openInvestigations} kind="derived" />
        <MetricCard
          label="Flagged (24h)"
          value={data.flaggedLast24h}
          tone={data.flaggedLast24h > 0 ? 'warning' : 'default'}
          kind="derived"
        />
        <MetricCard
          label="Blocked (24h)"
          value={data.blockedLast24h}
          tone={data.blockedLast24h > 0 ? 'danger' : 'default'}
          kind="derived"
        />
      </div>

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Investigations by severity</CardTitle>
          </CardHeader>
          <CardContent className="h-72">
            {severityData.length === 0 ? (
              <p className="text-sm text-ink-muted">No open investigations by severity.</p>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={severityData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} label>
                    {severityData.map((_, i) => (
                      <Cell key={i} fill={SEVERITY_COLORS[i % SEVERITY_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Investigation counts (bar)</CardTitle>
          </CardHeader>
          <CardContent className="h-72">
            {severityData.length === 0 ? (
              <p className="text-sm text-ink-muted">No severity breakdown available.</p>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={severityData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e7e2d8" />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="value" fill="#1f4d3c" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="mb-6 grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Highest-risk agents</CardTitle>
              <Link to="/risk/agents" className="text-xs font-semibold text-forest hover:underline">
                View all agents
              </Link>
            </CardHeader>
            <CardContent>
              {data.highestRiskAgents.length === 0 ? (
                <p className="text-sm text-ink-muted">No agents ranked yet.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Agent</TableHead>
                      <TableHead>Area</TableHead>
                      <TableHead>Risk</TableHead>
                      <TableHead>Flagged</TableHead>
                      <TableHead>Open cases</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.highestRiskAgents.map((a) => (
                      <TableRow key={a.agentId}>
                        <TableCell>
                          <div className="font-medium">{a.businessName}</div>
                          <div className="text-xs text-ink-muted">{a.agentCode}</div>
                        </TableCell>
                        <TableCell>{a.areaCode ?? '—'}</TableCell>
                        <TableCell>
                          <Badge variant={riskLevelVariant(a.riskLevel)}>{a.riskLevel}</Badge>
                        </TableCell>
                        <TableCell>{a.flaggedTransactions ?? 0}</TableCell>
                        <TableCell>{a.openInvestigations ?? 0}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
        <InsightPanel
          title="Risk summary"
          source="system"
          explanation={insightParts.join(' ')}
          footer="Narrative derived from live risk dashboard APIs — not an AI model output."
        />
      </div>
    </div>
  )
}