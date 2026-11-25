import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { sharedApi } from '@/api/endpoints'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadCsv } from '@/lib/utils'
import { asPageContent } from './adminHelpers'
import type { AuditLogRow } from './adminTypes'
import type { PageResponse } from '@/types/api'

export function AuditLogsPage() {
  const [action, setAction] = useState('')
  const [actorId, setActorId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)

  const logsQ = useQuery({
    queryKey: ['audit-logs', action, actorId, from, to, page],
    queryFn: () =>
      sharedApi.auditLogs({
        page,
        size: 50,
        action: action || undefined,
        actorId: actorId || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      }) as Promise<PageResponse<AuditLogRow>>,
  })

  const rows = logsQ.data?.content ?? asPageContent<AuditLogRow>(logsQ.data)
  const totalPages = logsQ.data?.totalPages ?? 1

  const exportCsv = () => {
    downloadCsv(
      'audit-logs.csv',
      rows.map((r) => ({
        id: r.id,
        actor: r.actorUsername,
        role: r.actorRole,
        action: r.action,
        entityType: r.entityType,
        entityId: r.entityId,
        createdAt: r.createdAt,
      })),
    )
  }

  if (logsQ.isLoading) return <LoadingBlock label="Loading audit logs…" />

  return (
    <div>
      <PageHeader
        title="Audit logs"
        description="Immutable trail of privileged actions."
        actions={
          <Button type="button" variant="secondary" disabled={rows.length === 0} onClick={exportCsv}>
            Export CSV
          </Button>
        }
      />
      <ErrorAlert error={logsQ.error} />
      <div className="mb-4 grid gap-3 rounded-xl border border-line bg-white p-4 md:grid-cols-4">
        <div className="space-y-1">
          <Label htmlFor="af-action">Action</Label>
          <Input id="af-action" value={action} onChange={(e) => setAction(e.target.value)} placeholder="USER_STATUS" />
        </div>
        <div className="space-y-1">
          <Label htmlFor="af-actor">Actor ID</Label>
          <Input id="af-actor" value={actorId} onChange={(e) => setActorId(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="af-from">From</Label>
          <Input id="af-from" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="af-to">To</Label>
          <Input id="af-to" type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="No entries" message="Adjust filters or wait for audited activity." />
      ) : (
        <>
          <div className="rounded-xl border border-line bg-white">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Actor</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Entity</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="text-xs text-ink-muted">{r.createdAt}</TableCell>
                    <TableCell>
                      <div className="text-sm font-medium">{r.actorUsername ?? '—'}</div>
                      <div className="text-xs text-ink-muted">{r.actorRole}</div>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{r.action}</TableCell>
                    <TableCell className="text-xs">
                      {r.entityType ?? '—'}
                      {r.entityId ? ` · ${r.entityId}` : ''}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-xs text-ink-muted">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}
    </div>
  )
}