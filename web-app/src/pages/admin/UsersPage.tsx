import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
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
import { asPageContent, selectClass } from './adminHelpers'
import type { AdminUser, AreaRow, StaffRole, UserStatus } from './adminTypes'
import type { PageResponse, Role } from '@/types/api'

const STAFF_ROLES: StaffRole[] = ['ADMIN', 'RISK_OFFICER', 'FINANCE_OFFICER', 'AREA_MANAGER']
const STATUSES: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'LOCKED']

function statusVariant(status: UserStatus) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'LOCKED') return 'danger' as const
  return 'warning' as const
}

export function UsersPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('Demo@2026!')
  const [role, setRole] = useState<StaffRole>('RISK_OFFICER')
  const [areaId, setAreaId] = useState('')

  const usersQ = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.users({ size: 100 }) as Promise<PageResponse<AdminUser>>,
  })
  const areasQ = useQuery({
    queryKey: ['areas'],
    queryFn: () => sharedApi.areas() as Promise<AreaRow[]>,
  })

  const createM = useMutation({
    mutationFn: () =>
      adminApi.createUser({
        username,
        email,
        fullName,
        password,
        role,
        areaId: areaId || undefined,
      }),
    onSuccess: () => {
      toast.success('User created')
      setOpen(false)
      void qc.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const statusM = useMutation({
    mutationFn: ({ id, status }: { id: string; status: UserStatus }) => adminApi.userStatus(id, status),
    onSuccess: () => {
      toast.success('Status updated')
      void qc.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const users = usersQ.data?.content ?? asPageContent<AdminUser>(usersQ.data)
  const areas = Array.isArray(areasQ.data) ? areasQ.data : []

  if (usersQ.isLoading) return <LoadingBlock label="Loading users…" />

  return (
    <div>
      <PageHeader
        title="Users"
        description="Create staff accounts and manage activation state."
        actions={
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button type="button">Create user</Button>
            </DialogTrigger>
            <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle>New staff user</DialogTitle>
              </DialogHeader>
              <div className="grid gap-3">
                <div className="space-y-1">
                  <Label htmlFor="u-username">Username</Label>
                  <Input id="u-username" value={username} onChange={(e) => setUsername(e.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="u-email">Email</Label>
                  <Input id="u-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="u-name">Full name</Label>
                  <Input id="u-name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="u-role">Role</Label>
                  <select id="u-role" className={selectClass} value={role} onChange={(e) => setRole(e.target.value as StaffRole)}>
                    {STAFF_ROLES.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="u-area">Area (optional)</Label>
                  <select id="u-area" className={selectClass} value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                    <option value="">—</option>
                    {areas.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.code} · {a.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="u-pass">Password</Label>
                  <Input id="u-pass" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
                </div>
              </div>
              <DialogFooter>
                <Button type="button" disabled={createM.isPending} onClick={() => createM.mutate()}>
                  {createM.isPending ? 'Creating…' : 'Create'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />
      <ErrorAlert error={usersQ.error} />
      {users.length === 0 ? (
        <EmptyState title="No users" message="No staff users returned from the API." />
      ) : (
        <div className="rounded-xl border border-line bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Username</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Area</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((u) => (
                <TableRow key={u.id}>
                  <TableCell className="font-medium">{u.username}</TableCell>
                  <TableCell>{u.fullName}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{u.role as Role}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(u.status)}>{u.status}</Badge>
                  </TableCell>
                  <TableCell className="text-ink-muted">{u.areaName ?? '—'}</TableCell>
                  <TableCell>
                    <select
                      className={`${selectClass} max-w-[9rem]`}
                      value={u.status}
                      disabled={statusM.isPending}
                      onChange={(e) => statusM.mutate({ id: u.id, status: e.target.value as UserStatus })}
                    >
                      {STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
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