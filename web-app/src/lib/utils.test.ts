import { describe, expect, it } from 'vitest'
import { cn, money, newIdempotencyKey } from './utils'

describe('money', () => {
  it('formats numeric values with currency', () => {
    expect(money(1234.5)).toBe('USD 1,234.50')
  })

  it('handles invalid values', () => {
    expect(money('abc')).toBe('USD —')
  })
})

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('px-2', 'px-4')).toContain('px-4')
  })
})

describe('newIdempotencyKey', () => {
  it('prefixes generated keys', () => {
    expect(newIdempotencyKey('WEB')).toMatch(/^WEB-/)
  })
})
