export type Role = 'AGENT' | 'AREA_MANAGER' | 'RISK_OFFICER' | 'FINANCE_OFFICER' | 'ADMIN'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ReviewStatus = 'PENDING' | 'CLEARED' | 'CONFIRMED_FRAUD' | 'UNDER_INVESTIGATION'
export type FraudDecision = 'ALLOW' | 'REVIEW' | 'BLOCK'

export interface AuthenticatedUser {
  id: string
  username: string
  fullName: string
  email: string
  role: Role
  areaId?: string | null
  areaName?: string | null
  agentId?: string | null
  agentCode?: string | null
  client?: string | null
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  refreshExpiresIn: number
  user: AuthenticatedUser
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RiskDashboard {
  pendingReviews: number
  openInvestigations: number
  investigationsBySeverity: Record<string, number>
  flaggedLast24h: number
  blockedLast24h: number
  highestRiskAgents: AgentRiskResponse[]
}

export interface AgentRiskResponse {
  agentId: string
  agentCode: string
  businessName: string
  areaCode?: string
  status: string
  riskLevel: RiskLevel
  creditScore?: number
  openInvestigations?: number
  flaggedTransactions?: number
}

export interface FlaggedTransaction {
  fraudCheckId: string
  transactionId: string
  reference: string
  transactionType: string
  transactionStatus: string
  amount: number
  currency: string
  agentId: string
  agentCode: string
  customerName?: string
  customerPhone?: string
  score: number
  riskLevel: RiskLevel
  decision: FraudDecision
  reviewStatus: ReviewStatus
  reasonCodes: string[]
  explanation: string
  modelSource: string
  createdAt: string
}

export interface FeatureContribution {
  feature: string
  value: string
  contribution: number
  direction: string
  description: string
}

export interface FraudCheckDetail {
  summary: FlaggedTransaction
  featureContributions: FeatureContribution[]
  features: Record<string, unknown>
  modelVersion?: string
  reviewedBy?: string
  reviewNote?: string
  reviewedAt?: string
  investigationId?: string
  investigationCaseNumber?: string
}

export interface Investigation {
  id: string
  caseNumber: string
  title: string
  status: string
  severity: RiskLevel
  agentId: string
  agentCode: string
  assigneeId?: string
  assigneeName?: string
  openedAt: string
  closedAt?: string
  outcome?: string
}

export interface InvestigationDetail extends Investigation {
  description?: string
  notes: Array<{ id: string; note: string; authorName?: string; createdAt: string }>
  linkedFraudChecks?: FlaggedTransaction[]
  resolutionNotes?: string
  actions?: string[]
}

export interface DisbursementPreview {
  agentId: string
  agentCode: string
  grossCommission: number
  interestRecovered: number
  principalRecovered: number
  netCommission: number
  outstandingAdvanceBefore: number
  outstandingAdvanceAfter: number
  recoveryOrder: string
  maxRecoveryPercent: number
  recoveryEnabled: boolean
  recoveryCap: number
  minimumPayout: number
  eligible: boolean
  reason?: string
}

export interface AdvanceResponse {
  id: string
  reference: string
  agentId: string
  agentCode: string
  agentName?: string
  requestedAmount: number
  approvedAmount?: number
  dailyInterestRate: number
  termDays: number
  interestAmount?: number
  principalOutstanding: number
  interestOutstanding: number
  totalOutstanding: number
  status: string
  purpose?: string
  decisionNote?: string
  creditScoreAtRequest?: number
  requestedAt: string
  dueDate?: string
}

export interface AdvanceDetail extends AdvanceResponse {
  creditExplanation?: string
  creditFactors?: FeatureContribution[]
  repayments?: Array<{ id: string; amount: number; createdAt: string; source?: string }>
}

export interface AssistantChatResponse {
  interactionId: string
  answer: string
  refused: boolean
  engine: string
  toolsUsed: Array<{ name: string; classification: string; summary: string; ok: boolean }>
  dataClassifications: string[]
  disclaimer: string
}

export interface SettingResponse {
  key: string
  value: string
  description?: string
  category?: string
  updatedAt?: string
}

export interface TariffResponse {
  id: string
  transactionType: string
  minAmount: number
  maxAmount: number
  feeFlat: number
  feePercent: number
  commissionFlat: number
  commissionPercent: number
  active: boolean
}
