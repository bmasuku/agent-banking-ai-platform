import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { financeApi } from '@/api/endpoints'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { money } from '@/lib/utils'
import { advanceStatusVariant, formatInstant, pageContent, pageMeta } from '@/pages/finance/helpers'
import type { AdvanceResponse } from '@/types/api'

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'REPAID', label: 'Repaid' },
  { value: 'REJECTED', label: 'Rejected' },
]

export function AdvancesPage() {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)

  const query = useQuery({
    queryKey: ['finance', 'advances', status, page],
    queryFn: () =>
      financeApi.advances({
        ...(status ? { status } : {}),
        page,
        size: 20,
      }),
  })

  const rows = pageContent<AdvanceResponse>(query.data)
  const meta = pageMeta(query.data)

  return (
    <div>
      <PageHeader
        title="Float advances"
        description="Review referred requests, monitor outstanding advances, and open a record for approve / reject / repay actions."
      />
      <ErrorAlert error={query.error} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        {STATUS_OPTIONS.map((opt) => (
          <Button
            key={opt.value || 'all'}
            type="button"
            size="sm"
            variant={status === opt.value ? 'default' : 'outline'}
            onClick={() => {
              setStatus(opt.value)
              setPage(0)
            }}
          >
            {opt.label}
          </Button>
        ))}
      </div>

      {query.isLoading ? (
        <LoadingBlock />
      ) : rows.length === 0 ? (
        <EmptyState title="No advances" message="No float advances match the selected filter." />
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reference</TableHead>
                  <TableHead>Agent</TableHead>
                  <TableHead>Requested</TableHead>
                  <TableHead>Outstanding</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Requested at</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="font-mono text-xs">{row.reference}</TableCell>
                    <TableCell>{row.agentCode}</TableCell>
                    <TableCell>{money(row.requestedAmount)}</TableCell>
                    <TableCell>{money(row.totalOutstanding)}</TableCell>
                    <TableCell>
                      <Badge variant={advanceStatusVariant(row.status)}>{row.status}</Badge>
                    </TableCell>
                    <TableCell className="text-ink-muted">{formatInstant(row.requestedAt)}</TableCell>
                    <TableCell>
                      <Button asChild variant="ghost" size="sm">
                        <Link to={`/finance/advances/${row.id}`}>Open</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {meta.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm text-ink-muted">
          <span>
            Page {meta.page + 1} of {meta.totalPages} · {meta.totalElements} total
          </span>
          <div className="flex gap-2">
            <Button type="button" size="sm" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={page + 1 >= meta.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  )
}