import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ChangeEvent, type Dispatch, type SetStateAction } from 'react'
import { toast } from 'sonner'
import { adminApi, sharedApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { money } from '@/lib/utils'
import { selectClass } from './adminHelpers'
import type { AgentSummary, AreaRow } from './adminTypes'

export function AgentsAdminPage() {
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<string | null>(null)
  const [form, setForm] = useState({
    username: '',
    email: '',
    fullName: '',
    phoneNumber: '',
    password: 'Demo@2026!',
    businessName: '',
    areaId: '',
    address: '',
    latitude: '-17.8252',
    longitude: '31.0335',
    minEfloatThreshold: '500',
    minCashThreshold: '200',
    dailyTransactionLimit: '10000',
  })
  const [editForm, setEditForm] = useState({
    businessName: '',
    address: '',
    latitude: '',
    longitude: '',
    minEfloatThreshold: '',
    minCashThreshold: '',
    dailyTransactionLimit: '',
  })

  const agentsQ = useQuery({
    queryKey: ['admin', 'agents'],
    queryFn: () => sharedApi.agents() as Promise<AgentSummary[]>,
  })
  const areasQ = useQuery({
    queryKey: ['areas'],
    queryFn: () => sharedApi.areas() as Promise<AreaRow[]>,
  })

  const createM = useMutation({
    mutationFn: () =>
      adminApi.createAgent({
        username: form.username,
        email: form.email,
        fullName: form.fullName,
        phoneNumber: form.phoneNumber || undefined,
        password: form.password,
        businessName: form.businessName,
        areaId: form.areaId,
        address: form.address,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        minEfloatThreshold: Number(form.minEfloatThreshold),
        minCashThreshold: Number(form.minCashThreshold),
        dailyTransactionLimit: Number(form.dailyTransactionLimit),
      }),
    onSuccess: () => {
      toast.success('Agent onboarded')
      setCreateOpen(false)
      void qc.invalidateQueries({ queryKey: ['admin', 'agents'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const updateM = useMutation({
    mutationFn: () => {
      if (!editId) throw new Error('No agent selected')
      const body: Record<string, unknown> = {}
      if (editForm.businessName) body.businessName = editForm.businessName
      if (editForm.address) body.address = editForm.address
      if (editForm.latitude) body.latitude = Number(editForm.latitude)
      if (editForm.longitude) body.longitude = Number(editForm.longitude)
      if (editForm.minEfloatThreshold) body.minEfloatThreshold = Number(editForm.minEfloatThreshold)
      if (editForm.minCashThreshold) body.minCashThreshold = Number(editForm.minCashThreshold)
      if (editForm.dailyTransactionLimit) body.dailyTransactionLimit = Number(editForm.dailyTransactionLimit)
      return adminApi.updateAgent(editId, body)
    },
    onSuccess: () => {
      toast.success('Agent updated')
      setEditId(null)
      void qc.invalidateQueries({ queryKey: ['admin', 'agents'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const agents = Array.isArray(agentsQ.data) ? agentsQ.data : []
  const areas = Array.isArray(areasQ.data) ? areasQ.data : []

  const openEdit = (agent: AgentSummary) => {
    setEditId(agent.id)
    setEditForm({
      businessName: agent.businessName,
      address: '',
      latitude: '',
      longitude: '',
      minEfloatThreshold: '',
      minCashThreshold: '',
      dailyTransactionLimit: '',
    })
  }

  if (agentsQ.isLoading) return <LoadingBlock label="Loading agents…" />

  return (
    <div>
      <PageHeader
        title="Agents"
        description="Onboard agents and adjust liquidity thresholds and limits."
        actions={
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button type="button">Onboard agent</Button>
            </DialogTrigger>
            <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
              <DialogHeader>
                <DialogTitle>New agent</DialogTitle>
              </DialogHeader>
              <CreateAgentForm
                form={form}
                setForm={setForm}
                areas={areas}
                onSubmit={() => createM.mutate()}
                pending={createM.isPending}
              />
            </DialogContent>
          </Dialog>
        }
      />
      <ErrorAlert error={agentsQ.error} />
      <Dialog open={editId != null} onOpenChange={(o) => !o && setEditId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Update agent limits</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3">
            <EditAgentFields form={editForm} setForm={setEditForm} />
          </div>
          <DialogFooter>
            <Button type="button" disabled={updateM.isPending} onClick={() => updateM.mutate()}>
              {updateM.isPending ? 'Saving…' : 'Save changes'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {agents.length === 0 ? (
        <EmptyState title="No agents" message="Onboard an agent to appear in the directory." />
      ) : (
        <div className="rounded-xl border border-line bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Business</TableHead>
                <TableHead>Area</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Risk</TableHead>
                <TableHead>E-float</TableHead>
                <TableHead>Cash</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {agents.map((a) => (
                <TableRow key={a.id}>
                  <TableCell className="font-medium">{a.agentCode}</TableCell>
                  <TableCell>{a.businessName}</TableCell>
                  <TableCell>{a.areaCode ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{a.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={a.riskLevel === 'HIGH' || a.riskLevel === 'CRITICAL' ? 'danger' : 'secondary'}>
                      {a.riskLevel}
                    </Badge>
                  </TableCell>
                  <TableCell>{a.efloat != null ? money(a.efloat) : '—'}</TableCell>
                  <TableCell>{a.cash != null ? money(a.cash) : '—'}</TableCell>
                  <TableCell>
                    <Button type="button" variant="ghost" size="sm" onClick={() => openEdit(a)}>
                      Edit
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

function CreateAgentForm({
  form,
  setForm,
  areas,
  onSubmit,
  pending,
}: {
  form: {
    username: string
    email: string
    fullName: string
    phoneNumber: string
    password: string
    businessName: string
    areaId: string
    address: string
    latitude: string
    longitude: string
    minEfloatThreshold: string
    minCashThreshold: string
    dailyTransactionLimit: string
  }
  setForm: Dispatch<SetStateAction<typeof form>>
  areas: AreaRow[]
  onSubmit: () => void
  pending: boolean
}) {
  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))
  return (
  <>
    <div className="grid gap-3">
      <Field label="Username" id="ag-user" value={form.username} onChange={set('username')} />
      <Field label="Email" id="ag-email" value={form.email} onChange={set('email')} />
      <Field label="Full name" id="ag-name" value={form.fullName} onChange={set('fullName')} />
      <Field label="Phone" id="ag-phone" value={form.phoneNumber} onChange={set('phoneNumber')} />
      <Field label="Password" id="ag-pass" type="password" value={form.password} onChange={set('password')} />
      <Field label="Business name" id="ag-biz" value={form.businessName} onChange={set('businessName')} />
      <div className="space-y-1">
        <Label htmlFor="ag-area">Area</Label>
        <select id="ag-area" className={selectClass} value={form.areaId} onChange={set('areaId')} required>
          <option value="">Select area</option>
          {areas.map((a) => (
            <option key={a.id} value={a.id}>
              {a.code}
            </option>
          ))}
        </select>
      </div>
      <Field label="Address" id="ag-addr" value={form.address} onChange={set('address')} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Latitude" id="ag-lat" value={form.latitude} onChange={set('latitude')} />
        <Field label="Longitude" id="ag-lng" value={form.longitude} onChange={set('longitude')} />
      </div>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Min e-float" id="ag-mef" value={form.minEfloatThreshold} onChange={set('minEfloatThreshold')} />
        <Field label="Min cash" id="ag-mca" value={form.minCashThreshold} onChange={set('minCashThreshold')} />
        <Field label="Daily limit" id="ag-dtl" value={form.dailyTransactionLimit} onChange={set('dailyTransactionLimit')} />
      </div>
    </div>
    <DialogFooter>
      <Button type="button" disabled={pending || !form.areaId} onClick={onSubmit}>
        {pending ? 'Onboarding…' : 'Create agent'}
      </Button>
    </DialogFooter>
  </>
  )
}

function EditAgentFields({
  form,
  setForm,
}: {
  form: {
    businessName: string
    address: string
    latitude: string
    longitude: string
    minEfloatThreshold: string
    minCashThreshold: string
    dailyTransactionLimit: string
  }
  setForm: Dispatch<SetStateAction<typeof form>>
}) {
  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))
  return (
    <>
      <Field label="Business name" id="ed-biz" value={form.businessName} onChange={set('businessName')} />
      <Field label="Address" id="ed-addr" value={form.address} onChange={set('address')} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Latitude" id="ed-lat" value={form.latitude} onChange={set('latitude')} />
        <Field label="Longitude" id="ed-lng" value={form.longitude} onChange={set('longitude')} />
      </div>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Min e-float" id="ed-mef" value={form.minEfloatThreshold} onChange={set('minEfloatThreshold')} />
        <Field label="Min cash" id="ed-mca" value={form.minCashThreshold} onChange={set('minCashThreshold')} />
        <Field label="Daily limit" id="ed-dtl" value={form.dailyTransactionLimit} onChange={set('dailyTransactionLimit')} />
      </div>
    </>
  )
}

function Field({
  label,
  id,
  value,
  onChange,
  type = 'text',
}: {
  label: string
  id: string
  value: string
  onChange: (e: ChangeEvent<HTMLInputElement>) => void
  type?: string
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={type} value={value} onChange={onChange} />
    </div>
  )
}
