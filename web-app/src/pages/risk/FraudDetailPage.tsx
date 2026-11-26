import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { errorMessage } from '@/api/client'
import { riskApi } from '@/api/endpoints'
import { AiBadge } from '@/components/AiBadge'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { money } from '@/lib/utils'
import type { ReviewStatus } from '@/types/api'
import { featureNumber, formatInstant, haversineKm, reviewStatusVariant, riskLevelVariant, selectClass } from './riskHelpers'

const REVIEW_OPTIONS: ReviewStatus[] = ['CLEARED', 'CONFIRMED_FRAUD', 'UNDER_INVESTIGATION']

export function FraudDetailPage() {
  const { id } = useParams<{ id: string }>()
  const qc = useQueryClient()
  const [reviewOpen, setReviewOpen] = useState(false)
  const [reviewStatus, setReviewStatus] = useState<ReviewStatus>('CLEARED')
  const [reviewNote, setReviewNote] = useState('')

  const q = useQuery({
    queryKey: ['risk', 'fraud', id],
    queryFn: () => riskApi.fraudCheck(id!),
    enabled: Boolean(id),
  })

  const reviewMut = useMutation({
    mutationFn: () => riskApi.review(id!, { status: reviewStatus, note: reviewNote.trim() }),
    onSuccess: () => {
      toast.success('Review recorded')
      setReviewOpen(false)
      setReviewNote('')
      void qc.invalidateQueries({ queryKey: ['risk', 'fraud', id] })
      void qc.invalidateQueries({ queryKey: ['risk', 'flagged'] })
      void qc.invalidateQueries({ queryKey: ['risk', 'dashboard'] })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (!id) return <ErrorAlert error={new Error('Missing fraud check id')} />
  if (q.isLoading) return <LoadingBlock label="Loading fraud evaluation…" />
  if (q.isError) return <ErrorAlert error={q.error} />
  if (!q.data) return null

  const { summary, featureContributions, features } = q.data
  const contribChart = [...featureContributions]
    .sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution))
    .slice(0, 8)
    .map((c) => ({ name: c.feature, contribution: c.contribution }))

  const agentLat = featureNumber(features, 'agentLatitude')
  const agentLon = featureNumber(features, 'agentLongitude')
  const txLat = featureNumber(features, 'transactionLatitude')
  const txLon = featureNumber(features, 'transactionLongitude')
  const distanceKm =
    featureNumber(features, 'distanceFromRegisteredKm') ??
    (agentLat != null && agentLon != null && txLat != null && txLon != null
      ? haversineKm(agentLat, agentLon, txLat, txLon)
      : null)
  const hasLocation = agentLat != null && agentLon != null && txLat != null && txLon != null

  return (
    <div>
      <PageHeader
        title={`Fraud check · ${summary.reference}`}
        description={summary.explanation}
        actions={
          <Dialog open={reviewOpen} onOpenChange={setReviewOpen}>
            <DialogTrigger asChild>
              <Button type="button">Submit review</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Review fraud alert</DialogTitle>
                <DialogDescription>Record CLEARED, CONFIRMED_FRAUD, or open UNDER_INVESTIGATION.</DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="reviewStatus">Decision</Label>
                  <select
                    id="reviewStatus"
                    className={selectClass}
                    value={reviewStatus}
                    onChange={(e) => setReviewStatus(e.target.value as ReviewStatus)}
                  >
                    {REVIEW_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="reviewNote">Note</Label>
                  <Textarea
                    id="reviewNote"
                    rows={4}
                    value={reviewNote}
                    onChange={(e) => setReviewNote(e.target.value)}
                    placeholder="Rationale for this decision"
                  />
                </div>
                <Button
                  type="button"
                  disabled={reviewNote.trim().length === 0 || reviewMut.isPending}
                  onClick={() => reviewMut.mutate()}
                >
                  {reviewMut.isPending ? 'Saving…' : 'Save review'}
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        }
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-ink-muted">AI score</CardTitle>
          </CardHeader>
          <CardContent className="font-display text-2xl font-semibold">{Number(summary.score).toFixed(3)}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-ink-muted">Risk level</CardTitle>
          </CardHeader>
          <CardContent>
            <Badge variant={riskLevelVariant(summary.riskLevel)}>{summary.riskLevel}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-ink-muted">Review status</CardTitle>
          </CardHeader>
          <CardContent>
            <Badge variant={reviewStatusVariant(summary.reviewStatus)}>{summary.reviewStatus}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-ink-muted">Amount</CardTitle>
          </CardHeader>
          <CardContent className="font-semibold">{money(summary.amount, summary.currency)}</CardContent>
        </Card>
      </div>

      {q.data.investigationId ? (
        <Card className="mb-6 border-info/30">
          <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
            <p className="text-sm">
              Linked investigation{' '}
              <span className="font-semibold">{q.data.investigationCaseNumber ?? q.data.investigationId}</span>
            </p>
            <Link to={`/risk/investigations/${q.data.investigationId}`}>
              <Button type="button" variant="secondary" size="sm">
                Open case
              </Button>
            </Link>
          </CardContent>
        </Card>
      ) : null}

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Reason codes</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {summary.reasonCodes.length === 0 ? (
              <p className="text-sm text-ink-muted">None</p>
            ) : (
              summary.reasonCodes.map((code) => (
                <Badge key={code} variant="secondary">
                  {code}
                </Badge>
              ))
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-2">
            <CardTitle className="text-base">Model</CardTitle>
            <AiBadge label="AI score" />
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <p>
              <span className="text-ink-muted">Source:</span> {summary.modelSource}
            </p>
            {q.data.modelVersion ? (
              <p>
                <span className="text-ink-muted">Version:</span> {q.data.modelVersion}
              </p>
            ) : null}
            {q.data.reviewedAt ? (
              <p>
                <span className="text-ink-muted">Reviewed:</span> {formatInstant(q.data.reviewedAt)}
                {q.data.reviewNote ? ` — ${q.data.reviewNote}` : ''}
              </p>
            ) : null}
          </CardContent>
        </Card>
      </div>

      {hasLocation ? (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">Location mismatch</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-3">
            <LocationField title="Registered agent">
              {agentLat?.toFixed(5)}, {agentLon?.toFixed(5)}
            </LocationField>
            <LocationField title="Transaction capture">
              {txLat?.toFixed(5)}, {txLon?.toFixed(5)}
            </LocationField>
            <LocationField title="Separation">
              {distanceKm != null ? `${distanceKm.toFixed(2)} km` : '—'}
              {distanceKm != null && distanceKm > 5 ? (
                <Badge className="ml-2" variant="danger">
                  Mismatch
                </Badge>
              ) : (
                <Badge className="ml-2" variant="success">
                  Near
                </Badge>
              )}
            </LocationField>
          </CardContent>
        </Card>
      ) : null}

      <div className="mb-6 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Feature contributions</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Feature</TableHead>
                  <TableHead>Value</TableHead>
                  <TableHead>Impact</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {featureContributions.map((f) => (
                  <TableRow key={f.feature}>
                    <TableCell>
                      <div className="font-medium">{f.feature}</div>
                      <div className="text-xs text-ink-muted">{f.description}</div>
                    </TableCell>
                    <TableCell>{f.value}</TableCell>
                    <TableCell>
                      {f.contribution >= 0 ? '+' : ''}
                      {f.contribution.toFixed(3)} ({f.direction})
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Contribution chart</CardTitle>
          </CardHeader>
          <CardContent className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={contribChart} layout="vertical" margin={{ left: 8, right: 8 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="name" width={100} tick={{ fontSize: 10 }} />
                <Tooltip />
                <Bar dataKey="contribution" fill="#00539d" />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function LocationField({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-line bg-sand/40 p-4">
      <p className="text-xs font-semibold uppercase text-ink-muted">{title}</p>
      <p className="mt-2 font-mono text-sm">{children}</p>
    </div>
  )
}