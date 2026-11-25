import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { riskApi } from '@/api/endpoints'
import { AiBadge } from '@/components/AiBadge'
import { EmptyState } from '@/components/EmptyState'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { downloadCsv, money } from '@/lib/utils'
import type { ReviewStatus, RiskLevel } from '@/types/api'
import { formatInstant, reviewStatusVariant, riskLevelVariant, selectClass } from './riskHelpers'

const PAGE_SIZE = 20
const REVIEW_STATUSES: Array<ReviewStatus | ''> = ['', 'PENDING', 'CLEARED', 'CONFIRMED_FRAUD', 'UNDER_INVESTIGATION']
const RISK_LEVELS: Array<RiskLevel | ''> = ['', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export function FlaggedTransactionsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [reviewStatus, setReviewStatus] = useState<ReviewStatus | ''>('')
  const [riskLevel, setRiskLevel] = useState<RiskLevel | ''>('')

  const params = useMemo(() => {
    const p: Record<string, unknown> = { page, size: PAGE_SIZE }
    if (reviewStatus) p.reviewStatus = reviewStatus
    if (riskLevel) p.riskLevel = riskLevel
    return p
  }, [page, reviewStatus, riskLevel])

  const q = useQuery({
    queryKey: ['risk', 'flagged', params],
    queryFn: () => riskApi.flagged(params),
  })

  const onExport = () => {
    const rows = q.data?.content ?? []
    if (rows.length === 0) return
    downloadCsv(
      `flagged-transactions-page-${page + 1}.csv`,
      rows.map((r) => ({
        reference: r.reference,
        agentCode: r.agentCode,
        amount: r.amount,
        currency: r.currency,
        score: r.score,
        riskLevel: r.riskLevel,
        decision: r.decision,
        reviewStatus: r.reviewStatus,
        createdAt: r.createdAt,
      })),
    )
  }

  return (
    <div>
      <PageHeader
        title="Flagged transactions"
        description="Fraud-scored transactions requiring or having completed review. Scores are model outputs; amounts are ledger."
        actions={
          <>
            <AiBadge label="AI scores" />
            <Button type="button" variant="secondary" onClick={onExport} disabled={!q.data?.content.length}>
              Export CSV
            </Button>
          </>
        }
      />

      <Card className="mb-4">
        <CardContent className="grid gap-4 pt-6 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="reviewStatus">Review status</Label>
            <select
              id="reviewStatus"
              className={selectClass}
              value={reviewStatus}
              onChange={(e) => {
                setPage(0)
                setReviewStatus(e.target.value as ReviewStatus | '')
              }}
            >
              {REVIEW_STATUSES.map((s) => (
                <option key={s || 'all'} value={s}>
                  {s || 'All statuses'}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="riskLevel">Risk level</Label>
            <select
              id="riskLevel"
              className={selectClass}
              value={riskLevel}
              onChange={(e) => {
                setPage(0)
                setRiskLevel(e.target.value as RiskLevel | '')
              }}
            >
              {RISK_LEVELS.map((s) => (
                <option key={s || 'all'} value={s}>
                  {s || 'All levels'}
                </option>
              ))}
            </select>
          </div>
        </CardContent>
      </Card>

      {q.isLoading ? <LoadingBlock /> : null}
      {q.isError ? <ErrorAlert error={q.error} /> : null}

      {q.data && q.data.content.length === 0 ? (
        <EmptyState title="No matches" message="No flagged transactions match the current filters." />
      ) : null}

      {q.data && q.data.content.length > 0 ? (
        <>
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Reference</TableHead>
                    <TableHead>Agent</TableHead>
                    <TableHead>Amount</TableHead>
                    <TableHead>AI score</TableHead>
                    <TableHead>Risk</TableHead>
                    <TableHead>Decision</TableHead>
                    <TableHead>Review</TableHead>
                    <TableHead>Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {q.data.content.map((row) => (
                    <TableRow
                      key={row.fraudCheckId}
                      className="cursor-pointer"
                      onClick={() => navigate(`/risk/fraud/${row.fraudCheckId}`)}
                    >
                      <TableCell className="font-mono text-xs">{row.reference}</TableCell>
                      <TableCell>{row.agentCode}</TableCell>
                      <TableCell>{money(row.amount, row.currency)}</TableCell>
                      <TableCell>{Number(row.score).toFixed(2)}</TableCell>
                      <TableCell>
                        <Badge variant={riskLevelVariant(row.riskLevel)}>{row.riskLevel}</Badge>
                      </TableCell>
                      <TableCell>{row.decision}</TableCell>
                      <TableCell>
                        <Badge variant={reviewStatusVariant(row.reviewStatus)}>{row.reviewStatus}</Badge>
                      </TableCell>
                      <TableCell className="text-xs text-ink-muted">{formatInstant(row.createdAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <div className="mt-4 flex items-center justify-between gap-2">
            <p className="text-sm text-ink-muted">
              Page {q.data.page + 1} of {Math.max(1, q.data.totalPages)} · {q.data.totalElements} total
            </p>
            <div className="flex gap-2">
              <Button type="button" variant="outline" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={page + 1 >= q.data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  )
}