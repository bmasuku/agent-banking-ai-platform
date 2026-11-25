import { format } from 'date-fns'
import type { FlaggedTransaction, InvestigationDetail, RiskLevel, ReviewStatus } from '@/types/api'

export function formatInstant(value?: string | null): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return format(d, 'dd MMM yyyy HH:mm')
}

export function riskLevelVariant(level: RiskLevel): 'danger' | 'warning' | 'secondary' | 'default' {
  if (level === 'CRITICAL' || level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'secondary'
}

export function reviewStatusVariant(status: ReviewStatus): 'warning' | 'success' | 'danger' | 'info' | 'secondary' {
  if (status === 'PENDING') return 'warning'
  if (status === 'CLEARED') return 'success'
  if (status === 'CONFIRMED_FRAUD') return 'danger'
  if (status === 'UNDER_INVESTIGATION') return 'info'
  return 'secondary'
}

export type NormalizedInvestigation = InvestigationDetail & {
  notes: Array<{ id: string; note: string; authorName?: string; createdAt: string }>
  linkedFraudChecks?: FlaggedTransaction[]
}

type NestedInvestigationDetail = {
  investigation?: Record<string, unknown>
  fraudCheck?: FlaggedTransaction
  notes?: NormalizedInvestigation['notes']
}

export function normalizeInvestigationDetail(raw: unknown): NormalizedInvestigation {
  const data = raw as InvestigationDetail & NestedInvestigationDetail
  if (data.investigation && typeof data.investigation === 'object') {
    const inv = data.investigation as unknown as InvestigationDetail & {
      assignedToId?: string
      assignedToName?: string
      actionsTaken?: string[]
    }
    return {
      ...inv,
      assigneeId: inv.assigneeId ?? inv.assignedToId,
      assigneeName: inv.assigneeName ?? inv.assignedToName,
      notes: data.notes ?? [],
      linkedFraudChecks: data.fraudCheck ? [data.fraudCheck] : inv.linkedFraudChecks,
      actions: inv.actions ?? inv.actionsTaken,
    }
  }
  return {
    ...data,
    notes: data.notes ?? [],
  }
}

export function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const toRad = (d: number) => (d * Math.PI) / 180
  const R = 6371
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function featureNumber(features: Record<string, unknown>, key: string): number | null {
  const v = features[key]
  if (typeof v === 'number' && !Number.isNaN(v)) return v
  if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v)
  return null
}

export const selectClass =
  'h-10 w-full rounded-md border border-line bg-card px-3 text-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'