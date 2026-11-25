export const API_BASE_URL =
  (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') || '/api/v1'

export const DEMO_ACCOUNTS = [
  { username: 'risk', password: 'Demo@2026!', role: 'RISK_OFFICER' },
  { username: 'finance', password: 'Demo@2026!', role: 'FINANCE_OFFICER' },
  { username: 'admin', password: 'Demo@2026!', role: 'ADMIN' },
] as const
