import type { AdvanceDetail, AdvanceResponse } from '@/types/api'

export function asNumber(value: unknown): number {
  if (typeof value === 'number' && !Number.isNaN(value)) return value
  if (typeof value === 'string') {
    const n = Number(value)
    return Number.isNaN(n) ? 0 : n
  }
  return 0
}

export function pageContent<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  if (data && typeof data === 'object' && 'content' in data) {
    const content = (data as { content?: unknown }).content
    return Array.isArray(content) ? (content as T[]) : []
  }
  return []
}

export function pageMeta(data: unknown) {
  const d = data as { page?: number; totalPages?: number; totalElements?: number } | null | undefined
  const content = pageContent(data)
  return {
    page: d?.page ?? 0,
    totalPages: d?.totalPages ?? 1,
    totalElements: d?.totalElements ?? content.length,
  }
}

export function recordRows(data: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(data)) return data as Array<Record<string, unknown>>
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>
    if (Array.isArray(obj.lines)) return obj.lines as Array<Record<string, unknown>>
    if (Array.isArray(obj.agents)) return obj.agents as Array<Record<string, unknown>>
    if (Array.isArray(obj.byStatus)) return obj.byStatus as Array<Record<string, unknown>>
    if (Array.isArray(obj.repaymentsInPeriod)) return obj.repaymentsInPeriod as Array<Record<string, unknown>>
    if (Array.isArray(obj.areas)) return obj.areas as Array<Record<string, unknown>>
  }
  return pageContent<Record<string, unknown>>(data)
}

export function unwrapAdvanceDetail(raw: AdvanceDetail | { advance: AdvanceResponse; repayments?: unknown[] }): {
  advance: AdvanceResponse & Record<string, unknown>
  repayments: Array<Record<string, unknown>>
} {
  if (raw && typeof raw === 'object' && 'advance' in raw && raw.advance) {
    const nested = raw as { advance: AdvanceResponse; repayments?: unknown[] }
    return {
      advance: nested.advance as AdvanceResponse & Record<string, unknown>,
      repayments: Array.isArray(nested.repayments)
        ? (nested.repayments as Array<Record<string, unknown>>)
        : [],
    }
  }
  const flat = raw as AdvanceResponse & Record<string, unknown>
  const repayments = Array.isArray(flat.repayments) ? (flat.repayments as Array<Record<string, unknown>>) : []
  return { advance: flat, repayments }
}

export function advanceStatusVariant(status: string | undefined): 'default' | 'warning' | 'danger' | 'success' | 'info' {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'OVERDUE':
      return 'danger'
    case 'ACTIVE':
      return 'info'
    case 'REPAID':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return 'default'
  }
}

export function formatInstant(value: unknown): string {
  if (typeof value !== 'string') return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}