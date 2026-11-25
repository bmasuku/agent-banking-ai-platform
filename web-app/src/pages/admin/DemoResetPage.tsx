import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import type { DemoResetResult } from './adminTypes'

export function DemoResetPage() {
  const [open, setOpen] = useState(false)
  const [confirm, setConfirm] = useState('')
  const [result, setResult] = useState<DemoResetResult | null>(null)

  const resetM = useMutation({
    mutationFn: adminApi.demoReset,
    onSuccess: (data) => {
      setResult(data as DemoResetResult)
      setOpen(false)
      setConfirm('')
      toast.success('Demo dataset rebuilt')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <div>
      <PageHeader title="Demo reset" description="Wipe operational data and reseed the demonstration ledger." />
      <p className="-mt-4 mb-6 text-sm text-ink-muted">
        After reset, use the{' '}
        <Link to="/admin/simulations" className="font-medium text-forest underline-offset-2 hover:underline">
          Simulation Control Centre
        </Link>{' '}
        to repopulate activity.
      </p>
      <Card className="max-w-xl border-danger/30">
        <CardHeader>
          <CardTitle className="text-danger">Destructive operation</CardTitle>
          <CardDescription>
            This truncates transactions, agents, users (except bootstrap), and settings defaults, then rebuilds the seeded demo.
            Type RESET in the confirmation dialog to proceed.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button type="button" variant="danger">
                Reset demo data
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Confirm demo reset</DialogTitle>
              </DialogHeader>
              <p className="text-sm text-ink-muted">Type RESET to confirm you understand this cannot be undone.</p>
              <div className="space-y-1">
                <Label htmlFor="reset-confirm">Confirmation</Label>
                <Input id="reset-confirm" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" />
              </div>
              <DialogFooter>
                <Button
                  type="button"
                  variant="danger"
                  disabled={confirm !== 'RESET' || resetM.isPending}
                  onClick={() => resetM.mutate()}
                >
                  {resetM.isPending ? 'Resetting…' : 'Execute reset'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>
      {result ? (
        <Card className="mt-6 max-w-2xl">
          <CardHeader>
            <CardTitle className="text-base">Reset summary</CardTitle>
          </CardHeader>
          <CardContent>
            {result.message ? <p className="mb-3 text-sm text-ink">{result.message}</p> : null}
            <pre className="max-h-80 overflow-auto rounded-md bg-sand/50 p-3 text-xs">
              {JSON.stringify(result.seed ?? result, null, 2)}
            </pre>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}