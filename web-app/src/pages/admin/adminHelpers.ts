import type { SettingResponse } from '@/types/api'
import type { AiPredictionItem, AiPredictionsPage, AiStatus } from './adminTypes'

export const selectClass =
  'flex h-10 w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-forest/30'

export function asPageContent<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  if (data && typeof data === 'object' && 'content' in data) {
    const page = data as { content?: T[] }
    return page.content ?? []
  }
  return []
}

export function asPredictions(data: unknown): AiPredictionItem[] {
  if (!data || typeof data !== 'object') return []
  const page = data as AiPredictionsPage
  return Array.isArray(page.items) ? page.items : []
}

export function aiStatusExplanation(status: AiStatus): string {
  const parts = [
    `Active inference source: ${status.activeSource}.`,
    status.reachable
      ? 'The AI microservice responded to health checks.'
      : 'The AI microservice is unreachable; the platform is using rule-based fallbacks where configured.',
    status.configEnabled ? 'AI is enabled in service configuration.' : 'AI is disabled in service configuration.',
    status.settingEnabled ? 'The runtime AI toggle setting is ON.' : 'The runtime AI toggle setting is OFF.',
  ]
  if (status.coolingDownUntil) {
    parts.push(`Circuit breaker cooling down until ${status.coolingDownUntil}.`)
  }
  return parts.join(' ')
}

export type SettingTab = 'all' | 'commission' | 'interest' | 'credit' | 'fraud' | 'recovery'

const TAB_PATTERNS: Record<Exclude<SettingTab, 'all'>, RegExp> = {
  commission: /commission/i,
  interest: /interest/i,
  credit: /credit/i,
  fraud: /fraud/i,
  recovery: /recovery/i,
}

export function filterSettings(settings: SettingResponse[], tab: SettingTab): SettingResponse[] {
  if (tab === 'all') return settings
  const pattern = TAB_PATTERNS[tab]
  return settings.filter((s) => pattern.test(s.key))
}

export function groupSettingsByCategory(settings: SettingResponse[]): Map<string, SettingResponse[]> {
  const map = new Map<string, SettingResponse[]>()
  for (const s of settings) {
    const cat = s.category ?? 'General'
    const list = map.get(cat) ?? []
    list.push(s)
    map.set(cat, list)
  }
  return map
}