import type { PageResponse, Role, SettingResponse, TariffResponse } from '@/types/api'

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'LOCKED'

export type StaffRole = Exclude<Role, 'AGENT'>

export interface AdminUser {
  id: string
  username: string
  email: string
  fullName: string
  phoneNumber?: string | null
  role: Role
  status: UserStatus
  areaId?: string | null
  areaName?: string | null
  lastLoginAt?: string | null
  createdAt?: string
}

export interface AgentSummary {
  id: string
  agentCode: string
  businessName: string
  fullName: string
  areaCode?: string
  status: string
  riskLevel: string
  tier?: string
  creditScore?: number
  efloat?: number
  cash?: number
}

export interface AreaRow {
  id: string
  code: string
  name: string
  province: string
  centerLatitude: number
  centerLongitude: number
  agentCount: number
  managerName?: string | null
  efloatPoolBalance?: number
  cashPoolBalance?: number
}

export interface AiStatus {
  configEnabled: boolean
  settingEnabled: boolean
  baseUrl: string
  reachable: boolean
  health?: Record<string, unknown> | null
  coolingDownUntil?: string | null
  activeSource: string
}

/** Enum names returned by the simulation API (`SimulationScenario` on the backend). */
export type SimulationScenarioName =
  | 'MORNING_RUSH_WITHDRAWALS'
  | 'MARKET_DAY_DEPOSITS'
  | 'MIXED_TRADING_HOUR'
  | 'FRAUD_STRUCTURING_ATTACK'
  | 'PEER_REBALANCING_CYCLE'
  | 'NORMAL_AGENT_TRANSACTIONS'
  | 'CUSTOMER_DEPOSITS'
  | 'CUSTOMER_WITHDRAWALS'
  | 'INSUFFICIENT_FLOAT'
  | 'FLOAT_EXCHANGE_REQUESTS'
  | 'SUSPICIOUS_TRANSACTIONS'
  | 'LOCATION_MISMATCH_ALERTS'
  | 'LOAN_DEFAULT_PRESSURE'
  | 'FLOAT_ADVANCE_REQUESTS'
  | 'ACCUMULATE_COMMISSIONS'
  | 'COMMISSION_DISBURSEMENT'
  | 'ACCELERATED_ACTIVITY'
  | 'FLOAT_PREDICTIONS'
  | 'AREA_MANAGER_ALERTS'

export interface SimulationScenario {
  scenario: SimulationScenarioName | string
  description: string
}

export interface RunSimulationRequest {
  scenario: SimulationScenarioName
  areaId?: string
  agentId?: string
  transactionCount?: number
  useAi?: boolean
  randomSeed?: number
}

export interface SimulationRun {
  id: string
  scenario: string
  status: string
  parameters?: Record<string, unknown>
  transactionsAttempted: number
  transactionsCompleted: number
  transactionsBlocked: number
  transactionsFailed: number
  summary?: Record<string, unknown>
  errorMessage?: string | null
  startedAt?: string
  finishedAt?: string | null
}

export interface AuditLogRow {
  id: string
  actorId?: string
  actorUsername?: string
  actorRole?: string
  action: string
  entityType?: string
  entityId?: string
  details?: Record<string, unknown>
  createdAt: string
}

export interface AiPredictionItem {
  id: string
  kind?: string
  subject_id?: string
  created_at?: string
  score?: number
  decision?: string
}

export interface AiPredictionsPage {
  total?: number
  items?: AiPredictionItem[]
}

export interface DemoResetResult {
  message?: string
  seed?: Record<string, unknown>
}

export type { PageResponse, SettingResponse, TariffResponse }