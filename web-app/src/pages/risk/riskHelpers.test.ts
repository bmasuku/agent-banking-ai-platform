import { describe, expect, it } from 'vitest'
import { haversineKm, normalizeInvestigationDetail, riskLevelVariant } from './riskHelpers'

describe('riskLevelVariant', () => {
  it('maps severity levels', () => {
    expect(riskLevelVariant('CRITICAL')).toBe('danger')
    expect(riskLevelVariant('HIGH')).toBe('danger')
    expect(riskLevelVariant('MEDIUM')).toBe('warning')
    expect(riskLevelVariant('LOW')).toBe('secondary')
  })
})

describe('haversineKm', () => {
  it('returns ~0 for identical points', () => {
    expect(haversineKm(-17.8, 31.0, -17.8, 31.0)).toBeCloseTo(0, 5)
  })
})

describe('normalizeInvestigationDetail', () => {
  it('unwraps nested investigation payloads', () => {
    const result = normalizeInvestigationDetail({
      investigation: {
        id: '1',
        caseNumber: 'INV-1',
        title: 'Case',
        status: 'OPEN',
        severity: 'HIGH',
        agentId: 'a1',
        agentCode: 'AGT-001',
        openedAt: '2026-01-01T00:00:00Z',
        assignedToName: 'Risk Officer',
      },
      notes: [{ id: 'n1', note: 'Looking into it', createdAt: '2026-01-01T01:00:00Z' }],
    })
    expect(result.caseNumber).toBe('INV-1')
    expect(result.assigneeName).toBe('Risk Officer')
    expect(result.notes).toHaveLength(1)
  })
})
