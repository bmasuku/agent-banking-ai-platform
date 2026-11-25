import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { adminApi, sharedApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { asPageContent, selectClass } from './adminHelpers'
import { SIMULATION_SECTIONS } from './simulationScenarios'
import type {
  AgentSummary,
  AreaRow,
  DemoResetResult,
  RunSimulationRequest,
  SimulationRun,
  SimulationScenarioName,
} from './adminTypes'
import type { PageResponse } from '@/types/api'

function aggregateRuns(runs: SimulationRun[]): SimulationRun {
  if (runs.length === 1) return runs[0]
  const base = runs[runs.length - 1]
  return {
    ...base,
    scenario: runs[0].scenario,
    transactionsAttempted: runs.reduce((n, r) => n + r.transactionsAttempted, 0),
    transactionsCompleted: runs.reduce((n, r) => n + r.transactionsCompleted, 0),
    transactionsBlocked: runs.reduce((n, r) => n + r.transactionsBlocked, 0),
    transactionsFailed: runs.reduce((n, r) => n + r.transactionsFailed, 0),
    summary: {
      batchRuns: runs.length,
      scenarios: runs.map((r) => r.scenario),
      statuses: runs.map((r) => r.status),
    },
  }
}

async function runScenarioTargets(
  body: Omit<RunSimulationRequest, 'agentId'>,
  agentIds: string[],
): Promise<SimulationRun> {
  if (agentIds.length === 0) {
    return (await adminApi.runSimulation(body)) as SimulationRun
  }
  if (agentIds.length === 1) {
    return (await adminApi.runSimulation({ ...body, agentId: agentIds[0] })) as SimulationRun
  }
  const runs: SimulationRun[] = []
  for (const agentId of agentIds) {
    runs.push((await adminApi.runSimulation({ ...body, agentId })) as SimulationRun)
  }
  return aggregateRuns(runs)
}

function agentChipVariant(selected: boolean) {
  return selected ? 'default' : 'secondary'
}

export function SimulationsPage() {
  const qc = useQueryClient()
  const [selectedAgentIds, setSelectedAgentIds] = useState<string[]>([])
  const [customAgentCodes, setCustomAgentCodes] = useState('')
  const [areaId, setAreaId] = useState('')
  const [countOverride, setCountOverride] = useState('')
  const [useAi, setUseAi] = useState(true)
  const [randomSeed, setRandomSeed] = useState('')
  const [activeScenario, setActiveScenario] = useState<SimulationScenarioName | null>(null)
  const [lastRun, setLastRun] = useState<SimulationRun | null>(null)
  const [resetOpen, setResetOpen] = useState(false)
  const [resetConfirm, setResetConfirm] = useState('')
  const [resetResult, setResetResult] = useState<DemoResetResult | null>(null)

  const historyQ = useQuery({
    queryKey: ['admin', 'simulations'],
    queryFn: () => adminApi.simulations({ size: 25 }) as Promise<PageResponse<SimulationRun>>,
  })
  const areasQ = useQuery({
    queryKey: ['areas'],
    queryFn: () => sharedApi.areas() as Promise<AreaRow[]>,
  })
  const agentsQ = useQuery({
    queryKey: ['admin', 'agents'],
    queryFn: () => sharedApi.agents() as Promise<AgentSummary[]>,
  })

  const agents = Array.isArray(agentsQ.data) ? agentsQ.data : []
  const demoAgents = useMemo(
    () =>
      [...agents]
        .filter((a) => /^AGT-\d{3}$/i.test(a.agentCode))
        .sort((a, b) => a.agentCode.localeCompare(b.agentCode)),
    [agents],
  )
  const chipAgents = demoAgents.length > 0 ? demoAgents : agents

  const resolvedAgentIds = useMemo(() => {
    if (selectedAgentIds.length > 0) return selectedAgentIds
    const codes = customAgentCodes
      .split(/[\s,]+/)
      .map((c) => c.trim())
      .filter(Boolean)
    if (codes.length === 0) return []
    return codes
      .map((code) => agents.find((a) => a.agentCode.toLowerCase() === code.toLowerCase())?.id)
      .filter((id): id is string => Boolean(id))
  }, [selectedAgentIds, customAgentCodes, agents])

  const runM = useMutation({
    mutationFn: async (scenario: SimulationScenarioName) => {
      const transactionCount = countOverride.trim() ? Number(countOverride) : undefined
      const body: Omit<RunSimulationRequest, 'agentId'> = {
        scenario,
        areaId: areaId || undefined,
        transactionCount:
          transactionCount !== undefined && !Number.isNaN(transactionCount) ? transactionCount : undefined,
        useAi,
        randomSeed: randomSeed ? Number(randomSeed) : undefined,
      }
      return runScenarioTargets(body, resolvedAgentIds)
    },
    onSuccess: (data, scenario) => {
      setLastRun(data)
      toast.success(`Ran ${scenario.replace(/_/g, ' ').toLowerCase()}`)
      void qc.invalidateQueries({ queryKey: ['admin', 'simulations'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => setActiveScenario(null),
  })

  const resetM = useMutation({
    mutationFn: adminApi.demoReset,
    onSuccess: (data) => {
      setResetResult(data as DemoResetResult)
      setResetOpen(false)
      setResetConfirm('')
      toast.success('Demonstration dataset rebuilt')
      void qc.invalidateQueries()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const toggleAgent = (id: string) => {
    setSelectedAgentIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  const history = historyQ.data?.content ?? asPageContent<SimulationRun>(historyQ.data)
  const areas = Array.isArray(areasQ.data) ? areasQ.data : []
  const isRunning = runM.isPending

  if (historyQ.isLoading) return <LoadingBlock label="Loading simulation control centre…" />

  return (
    <div>
      <PageHeader
        title="Simulation Control Centre"
        description="Presenter console: trigger scripted market activity on the live transaction engine during demos."
      />
      <ErrorAlert error={historyQ.error ?? agentsQ.error} />

      <Card className="mb-6 border-danger/30">
        <CardHeader>
          <CardTitle className="text-danger">Reset entire demonstration</CardTitle>
          <CardDescription>
            Wipes operational data and reseeds the demo ledger (same as Admin → Demo reset). Type RESET to confirm.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" variant="danger" onClick={() => setResetOpen(true)}>
            Reset entire demonstration
          </Button>
        </CardContent>
      </Card>

      <Card className="mb-8">
        <CardHeader>
          <CardTitle className="text-base">Run targets</CardTitle>
          <CardDescription>
            Leave all agents unselected to spread activity across the network. Select one or more demo agents to narrow
            the run (multiple agents execute sequential per-agent runs).
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>Agents</Label>
            {chipAgents.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {chipAgents.map((a) => {
                  const selected = selectedAgentIds.includes(a.id)
                  return (
                    <button
                      key={a.id}
                      type="button"
                      className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      onClick={() => toggleAgent(a.id)}
                      aria-pressed={selected}
                    >
                      <Badge variant={agentChipVariant(selected)} className="cursor-pointer normal-case tracking-normal">
                        {a.agentCode} · {a.businessName}
                      </Badge>
                    </button>
                  )
                })}
                {selectedAgentIds.length > 0 ? (
                  <Button type="button" variant="ghost" size="sm" onClick={() => setSelectedAgentIds([])}>
                    Clear
                  </Button>
                ) : null}
              </div>
            ) : (
              <div className="space-y-1">
                <Label htmlFor="sim-agent-codes">Agent codes (comma-separated)</Label>
                <Input
                  id="sim-agent-codes"
                  placeholder="AGT-001, AGT-002"
                  value={customAgentCodes}
                  onChange={(e) => setCustomAgentCodes(e.target.value)}
                />
              </div>
            )}
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <div className="space-y-1">
              <Label htmlFor="sim-area">Area (optional)</Label>
              <select id="sim-area" className={selectClass} value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">All areas</option>
                {areas.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="sim-count">Transaction count override (1–200)</Label>
              <Input
                id="sim-count"
                placeholder="Server default"
                value={countOverride}
                onChange={(e) => setCountOverride(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="sim-seed">Random seed (optional)</Label>
              <Input id="sim-seed" value={randomSeed} onChange={(e) => setRandomSeed(e.target.value)} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={useAi} onChange={(e) => setUseAi(e.target.checked)} />
            Use AI scoring where enabled
          </label>
        </CardContent>
      </Card>

      <div className="mb-8 space-y-6">
        {SIMULATION_SECTIONS.map((section) => (
          <Card key={section.title}>
            <CardHeader>
              <CardTitle className="text-base">{section.title}</CardTitle>
              <CardDescription>{section.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {section.actions.map((action) => {
                  const loading = isRunning && activeScenario === action.scenario
                  return (
                    <Button
                      key={action.scenario}
                      type="button"
                      variant="secondary"
                      size="sm"
                      disabled={isRunning}
                      title={action.hint}
                      onClick={() => {
                        setActiveScenario(action.scenario)
                        runM.mutate(action.scenario)
                      }}
                    >
                      {loading ? 'Running…' : action.label}
                    </Button>
                  )
                })}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {lastRun ? (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle className="text-base">Last run summary</CardTitle>
            <CardDescription>
              {lastRun.scenario} ·{' '}
              <Badge
                variant={lastRun.status === 'COMPLETED' ? 'success' : lastRun.status === 'FAILED' ? 'danger' : 'secondary'}
                className="ml-1 align-middle"
              >
                {lastRun.status}
              </Badge>
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-4">
            <div className="rounded-lg border border-line bg-sand/30 px-3 py-2">
              <p className="text-xs text-ink-muted">Attempted</p>
              <p className="text-lg font-semibold text-forest">{lastRun.transactionsAttempted}</p>
            </div>
            <div className="rounded-lg border border-line bg-sand/30 px-3 py-2">
              <p className="text-xs text-ink-muted">Completed</p>
              <p className="text-lg font-semibold text-success">{lastRun.transactionsCompleted}</p>
            </div>
            <div className="rounded-lg border border-line bg-sand/30 px-3 py-2">
              <p className="text-xs text-ink-muted">Blocked</p>
              <p className="text-lg font-semibold text-warning">{lastRun.transactionsBlocked}</p>
            </div>
            <div className="rounded-lg border border-line bg-sand/30 px-3 py-2">
              <p className="text-xs text-ink-muted">Failed</p>
              <p className="text-lg font-semibold text-danger">{lastRun.transactionsFailed}</p>
            </div>
            {lastRun.summary && Object.keys(lastRun.summary).length > 0 ? (
              <pre className="sm:col-span-4 max-h-48 overflow-auto rounded-md bg-sand/50 p-3 text-xs">
                {JSON.stringify(lastRun.summary, null, 2)}
              </pre>
            ) : null}
            {lastRun.errorMessage ? (
              <p className="sm:col-span-4 text-sm text-danger">{lastRun.errorMessage}</p>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      {resetResult ? (
        <Card className="mb-8 max-w-2xl">
          <CardHeader>
            <CardTitle className="text-base">Demo reset summary</CardTitle>
          </CardHeader>
          <CardContent>
            {resetResult.message ? <p className="mb-3 text-sm text-ink">{resetResult.message}</p> : null}
            <pre className="max-h-48 overflow-auto rounded-md bg-sand/50 p-3 text-xs">
              {JSON.stringify(resetResult.seed ?? resetResult, null, 2)}
            </pre>
          </CardContent>
        </Card>
      ) : null}

      <h2 className="mb-3 text-sm font-semibold text-ink-muted">Recent runs</h2>
      {history.length === 0 ? (
        <EmptyState title="No runs yet" message="Trigger a scenario above to populate history." />
      ) : (
        <div className="rounded-xl border border-line bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Scenario</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Attempted</TableHead>
                <TableHead>Completed</TableHead>
                <TableHead>Blocked</TableHead>
                <TableHead>Failed</TableHead>
                <TableHead>Started</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {history.map((run) => (
                <TableRow key={run.id}>
                  <TableCell className="font-medium">{run.scenario}</TableCell>
                  <TableCell>
                    <Badge
                      variant={run.status === 'COMPLETED' ? 'success' : run.status === 'FAILED' ? 'danger' : 'secondary'}
                    >
                      {run.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{run.transactionsAttempted}</TableCell>
                  <TableCell>{run.transactionsCompleted}</TableCell>
                  <TableCell>{run.transactionsBlocked}</TableCell>
                  <TableCell>{run.transactionsFailed}</TableCell>
                  <TableCell className="text-xs text-ink-muted">{run.startedAt ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={resetOpen} onOpenChange={setResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm demonstration reset</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-ink-muted">
            This truncates transactions, agents, users (except bootstrap), and settings defaults, then rebuilds the
            seeded demo. Type RESET to confirm.
          </p>
          <div className="space-y-1">
            <Label htmlFor="scc-reset-confirm">Confirmation</Label>
            <Input
              id="scc-reset-confirm"
              value={resetConfirm}
              onChange={(e) => setResetConfirm(e.target.value)}
              autoComplete="off"
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="danger"
              disabled={resetConfirm !== 'RESET' || resetM.isPending}
              onClick={() => resetM.mutate()}
            >
              {resetM.isPending ? 'Resetting…' : 'Execute reset'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}