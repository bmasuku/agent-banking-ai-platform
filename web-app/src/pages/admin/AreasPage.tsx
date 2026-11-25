import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { adminApi, sharedApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
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
import type { AreaRow } from './adminTypes'

export function AreasPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [province, setProvince] = useState('')
  const [lat, setLat] = useState('-17.8252')
  const [lng, setLng] = useState('31.0335')

  const areasQ = useQuery({
    queryKey: ['areas'],
    queryFn: () => sharedApi.areas() as Promise<AreaRow[]>,
  })

  const createM = useMutation({
    mutationFn: () =>
      adminApi.createArea({
        code: code.toUpperCase(),
        name,
        province,
        centerLatitude: Number(lat),
        centerLongitude: Number(lng),
      }),
    onSuccess: () => {
      toast.success('Area created')
      setOpen(false)
      void qc.invalidateQueries({ queryKey: ['areas'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const areas = Array.isArray(areasQ.data) ? areasQ.data : []

  if (areasQ.isLoading) return <LoadingBlock label="Loading areas…" />

  return (
    <div>
      <PageHeader
        title="Areas"
        description="Regional groupings and float pools."
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button type="button">Create area</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>New area</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3">
                <div className="space-y-1">
                  <Label htmlFor="a-code">Code</Label>
                  <Input id="a-code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="HRE-METRO" />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="a-name">Name</Label>
                  <Input id="a-name" value={name} onChange={(e) => setName(e.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="a-prov">Province</Label>
                  <Input id="a-prov" value={province} onChange={(e) => setProvince(e.target.value)} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label htmlFor="a-lat">Center latitude</Label>
                    <Input id="a-lat" value={lat} onChange={(e) => setLat(e.target.value)} />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="a-lng">Center longitude</Label>
                    <Input id="a-lng" value={lng} onChange={(e) => setLng(e.target.value)} />
                  </div>
                </div>
              </div>
              <DialogFooter>
                <Button type="button" disabled={createM.isPending} onClick={() => createM.mutate()}>
                  {createM.isPending ? 'Saving…' : 'Create'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />
      <ErrorAlert error={areasQ.error} />
      {areas.length === 0 ? (
        <EmptyState title="No areas" message="Create an area to organize agents." />
      ) : (
        <div className="rounded-xl border border-line bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Province</TableHead>
                <TableHead>Agents</TableHead>
                <TableHead>Manager</TableHead>
                <TableHead>E-float pool</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {areas.map((a) => (
                <TableRow key={a.id}>
                  <TableCell className="font-medium">{a.code}</TableCell>
                  <TableCell>{a.name}</TableCell>
                  <TableCell>{a.province}</TableCell>
                  <TableCell>{a.agentCount}</TableCell>
                  <TableCell className="text-ink-muted">{a.managerName ?? '—'}</TableCell>
                  <TableCell>{a.efloatPoolBalance != null ? money(a.efloatPoolBalance) : '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}