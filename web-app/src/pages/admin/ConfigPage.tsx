import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { ErrorAlert } from '@/components/ErrorAlert'
import { LoadingBlock } from '@/components/LoadingBlock'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { money } from '@/lib/utils'
import { filterSettings, groupSettingsByCategory, selectClass, type SettingTab } from './adminHelpers'
import type { SettingResponse, TariffResponse } from '@/types/api'

const TABS: { id: SettingTab; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'commission', label: 'Commission' },
  { id: 'interest', label: 'Interest' },
  { id: 'credit', label: 'Credit' },
  { id: 'fraud', label: 'Fraud' },
  { id: 'recovery', label: 'Recovery' },
]

const TX_TYPES = ['CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER'] as const

function emptyTariff() {
  return {
    transactionType: 'CASH_WITHDRAWAL' as (typeof TX_TYPES)[number],
    minAmount: '0.01',
    maxAmount: '1000',
    feePercent: '0',
    feeFlat: '0',
    commissionPercent: '0',
    commissionFlat: '0.50',
    active: true,
  }
}

export function ConfigPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<SettingTab>('all')
  const [edits, setEdits] = useState<Record<string, string>>({})
  const [tariffOpen, setTariffOpen] = useState(false)
  const [editTariff, setEditTariff] = useState<TariffResponse | null>(null)
  const [tariffForm, setTariffForm] = useState(emptyTariff())

  const settingsQ = useQuery({ queryKey: ['admin', 'settings'], queryFn: adminApi.settings })
  const tariffsQ = useQuery({ queryKey: ['admin', 'tariffs'], queryFn: adminApi.tariffs })

  const saveSettingM = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => adminApi.updateSetting(key, value),
    onSuccess: (_, { key }) => {
      toast.success(`Updated ${key}`)
      setEdits((e) => {
        const next = { ...e }
        delete next[key]
        return next
      })
      void qc.invalidateQueries({ queryKey: ['admin', 'settings'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const saveTariffM = useMutation({
    mutationFn: () => {
      const body = {
        transactionType: tariffForm.transactionType,
        minAmount: Number(tariffForm.minAmount),
        maxAmount: Number(tariffForm.maxAmount),
        feePercent: Number(tariffForm.feePercent),
        feeFlat: Number(tariffForm.feeFlat),
        commissionPercent: Number(tariffForm.commissionPercent),
        commissionFlat: Number(tariffForm.commissionFlat),
        active: tariffForm.active,
      }
      return editTariff ? adminApi.updateTariff(editTariff.id, body) : adminApi.createTariff(body)
    },
    onSuccess: () => {
      toast.success(editTariff ? 'Tariff updated' : 'Tariff created')
      setTariffOpen(false)
      setEditTariff(null)
      setTariffForm(emptyTariff())
      void qc.invalidateQueries({ queryKey: ['admin', 'tariffs'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const filtered = useMemo(() => filterSettings(settingsQ.data ?? [], tab), [settingsQ.data, tab])
  const grouped = useMemo(() => groupSettingsByCategory(filtered), [filtered])
  const tariffs = tariffsQ.data ?? []

  if (settingsQ.isLoading || tariffsQ.isLoading) return <LoadingBlock label="Loading configuration…" />

  const openCreateTariff = () => {
    setEditTariff(null)
    setTariffForm(emptyTariff())
    setTariffOpen(true)
  }

  const openEditTariff = (t: TariffResponse) => {
    setEditTariff(t)
    setTariffForm({
      transactionType: t.transactionType as (typeof TX_TYPES)[number],
      minAmount: String(t.minAmount),
      maxAmount: String(t.maxAmount),
      feePercent: String(t.feePercent),
      feeFlat: String(t.feeFlat),
      commissionPercent: String(t.commissionPercent),
      commissionFlat: String(t.commissionFlat),
      active: t.active,
    })
    setTariffOpen(true)
  }

  return (
    <div>
      <PageHeader title="Configuration" description="Runtime policy settings and transaction tariff bands." />
      <ErrorAlert error={settingsQ.error ?? tariffsQ.error} />
      <section className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Button
            key={t.id}
            type="button"
            size="sm"
            variant={tab === t.id ? 'default' : 'outline'}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </Button>
        ))}
      </section>
      <section className="mb-8 space-y-6">
        {[...grouped.entries()].map(([category, rows]) => (
          <div key={category} className="rounded-xl border border-line bg-white">
            <div className="border-b border-line px-4 py-3 text-sm font-semibold text-ink-muted">{category}</div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Key</TableHead>
                  <TableHead>Value</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((s: SettingResponse) => {
                  const value = edits[s.key] ?? s.value
                  const dirty = edits[s.key] != null && edits[s.key] !== s.value
                  return (
                    <TableRow key={s.key}>
                      <TableCell className="font-mono text-xs">{s.key}</TableCell>
                      <TableCell>
                        <Input
                          value={value}
                          className="max-w-xs font-mono text-xs"
                          onChange={(e) => setEdits((prev) => ({ ...prev, [s.key]: e.target.value }))}
                        />
                      </TableCell>
                      <TableCell className="max-w-md text-xs text-ink-muted">{s.description ?? '—'}</TableCell>
                      <TableCell>
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          disabled={!dirty || saveSettingM.isPending}
                          onClick={() => saveSettingM.mutate({ key: s.key, value })}
                        >
                          Save
                        </Button>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        ))}
      </section>
      <section className="rounded-xl border border-line bg-white">
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 className="font-display text-lg font-semibold">Tariffs</h2>
          <Button type="button" size="sm" onClick={openCreateTariff}>
            Add band
          </Button>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Type</TableHead>
              <TableHead>Range</TableHead>
              <TableHead>Fees</TableHead>
              <TableHead>Commission</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {tariffs.map((t) => (
              <TableRow key={t.id}>
                <TableCell>{t.transactionType}</TableCell>
                <TableCell>
                  {money(t.minAmount)} – {money(t.maxAmount)}
                </TableCell>
                <TableCell className="text-xs">
                  {t.feePercent}% + {money(t.feeFlat)}
                </TableCell>
                <TableCell className="text-xs">
                  {t.commissionPercent}% + {money(t.commissionFlat)}
                </TableCell>
                <TableCell>
                  <Badge variant={t.active ? 'success' : 'secondary'}>{t.active ? 'Active' : 'Inactive'}</Badge>
                </TableCell>
                <TableCell>
                  <Button type="button" variant="ghost" size="sm" onClick={() => openEditTariff(t)}>
                    Edit
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </section>
      <Dialog open={tariffOpen} onOpenChange={setTariffOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editTariff ? 'Edit tariff band' : 'New tariff band'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3">
            <div className="space-y-1">
              <Label>Transaction type</Label>
              <select
                className={selectClass}
                value={tariffForm.transactionType}
                onChange={(e) =>
                  setTariffForm((f) => ({ ...f, transactionType: e.target.value as (typeof TX_TYPES)[number] }))
                }
              >
                {TX_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Min amount" value={tariffForm.minAmount} onChange={(v) => setTariffForm((f) => ({ ...f, minAmount: v }))} />
              <Field label="Max amount" value={tariffForm.maxAmount} onChange={(v) => setTariffForm((f) => ({ ...f, maxAmount: v }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Fee %" value={tariffForm.feePercent} onChange={(v) => setTariffForm((f) => ({ ...f, feePercent: v }))} />
              <Field label="Fee flat" value={tariffForm.feeFlat} onChange={(v) => setTariffForm((f) => ({ ...f, feeFlat: v }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field
                label="Commission %"
                value={tariffForm.commissionPercent}
                onChange={(v) => setTariffForm((f) => ({ ...f, commissionPercent: v }))}
              />
              <Field
                label="Commission flat"
                value={tariffForm.commissionFlat}
                onChange={(v) => setTariffForm((f) => ({ ...f, commissionFlat: v }))}
              />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={tariffForm.active}
                onChange={(e) => setTariffForm((f) => ({ ...f, active: e.target.checked }))}
              />
              Active band
            </label>
          </div>
          <DialogFooter>
            <Button type="button" disabled={saveTariffM.isPending} onClick={() => saveTariffM.mutate()}>
              {saveTariffM.isPending ? 'Saving…' : 'Save tariff'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="space-y-1">
      <Label>{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}
