export type Role = 'AGENT' | 'AREA_MANAGER' | 'RISK_OFFICER' | 'FINANCE_OFFICER' | 'ADMIN';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type LiquidityRisk = 'NONE' | 'EFLOAT_SHORTAGE' | 'CASH_SHORTAGE' | 'BOTH_SHORTAGE';
export type TransactionType =
  | 'CASH_DEPOSIT'
  | 'CASH_WITHDRAWAL'
  | 'AGENT_TRANSFER_TO_CUSTOMER'
  | 'ADVANCE_DISBURSEMENT'
  | 'ADVANCE_REPAYMENT'
  | 'FLOAT_REPLENISHMENT'
  | 'FLOAT_EXCHANGE'
  | string;

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  errorCode?: string;
  message?: string;
  path?: string;
  details?: Record<string, unknown>;
  fieldErrors?: Array<{ field: string; message: string }> | null;
}

export interface AuthenticatedUser {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  areaId?: string | null;
  areaName?: string | null;
  agentId?: string | null;
  agentCode?: string | null;
  client?: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
  user: AuthenticatedUser;
}

export interface BalanceSnapshot {
  efloat: number;
  cash: number;
  commissionPayable: number;
  advancePrincipalOutstanding: number;
  advanceInterestOutstanding: number;
}

export interface AgentBalanceView {
  agentId: string;
  agentCode: string;
  businessName: string;
  currency: string;
  balances: BalanceSnapshot;
  minEfloatThreshold: number;
  minCashThreshold: number;
  efloatLow: boolean;
  cashLow: boolean;
  creditLimit: number;
  availableCredit: number;
  dailyTransactionLimit: number;
  usedToday: number;
  asOf: string;
}

export interface AgentProfile {
  id: string;
  agentCode: string;
  businessName: string;
  userId: string;
  fullName: string;
  username: string;
  email: string;
  phoneNumber: string;
  areaId: string;
  areaCode: string;
  areaName: string;
  tier: string;
  status: string;
  riskLevel: RiskLevel;
  creditScore: number;
  creditLimit: number;
  latitude: number;
  longitude: number;
  address: string;
  minEfloatThreshold: number;
  minCashThreshold: number;
  onboardedAt: string;
  accountNumber: string;
  dailyTransactionLimit: number;
  accountStatus: string;
}

export interface AgentSummary {
  id: string;
  agentCode: string;
  businessName: string;
  fullName: string;
  areaCode: string;
  status: string;
  riskLevel: RiskLevel;
  tier: string;
  creditScore: number;
  efloat: number;
  cash: number;
  commissionPayable: number;
  advanceOutstanding: number;
  efloatLow: boolean;
  cashLow: boolean;
}

export interface AgentLocation {
  agentId: string;
  agentCode: string;
  businessName: string;
  address: string;
  latitude: number;
  longitude: number;
  status: string;
  riskLevel: RiskLevel;
  efloat: number;
  cash: number;
  efloatLow: boolean;
  cashLow: boolean;
  predictedRisk: LiquidityRisk;
}

export interface AreaResponse {
  id: string;
  code: string;
  name: string;
  province: string;
  centerLatitude: number;
  centerLongitude: number;
  agentCount: number;
  managerName?: string;
  efloatPoolBalance: number;
  cashPoolBalance: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TransactionSummary {
  id: string;
  reference: string;
  type: TransactionType;
  status: string;
  agentId?: string;
  agentCode?: string;
  counterpartyAgentCode?: string;
  customerName?: string;
  customerPhone?: string;
  amount: number;
  fee: number;
  commission: number;
  currency: string;
  description?: string;
  createdAt: string;
}

export interface QuoteResponse {
  type: TransactionType;
  amount: number;
  fee: number;
  commission: number;
  customerPays: number;
  customerReceives: number;
  efloatChange: number;
  cashChange: number;
  currentEfloat: number;
  currentCash: number;
  projectedEfloat: number;
  projectedCash: number;
  sufficientLiquidity: boolean;
  explanation: string;
}

export interface TransactionResult {
  id: string;
  reference: string;
  type: TransactionType;
  status: string;
  agentCode: string;
  amount: number;
  fee: number;
  commission: number;
  balancesAfter?: BalanceSnapshot;
  fraud?: {
    score: number;
    riskLevel: RiskLevel;
    decision: string;
    reasonCodes: string[];
    explanation: string;
    modelSource: string;
  };
  replayed?: boolean;
}

export interface PredictionResponse {
  id: string;
  agentId: string;
  agentCode: string;
  horizonHours: number;
  currentEfloat: number;
  currentCash: number;
  projectedEfloat: number;
  projectedCash: number;
  minProjectedEfloat: number;
  minProjectedCash: number;
  predictedEfloatNetFlow: number;
  predictedCashNetFlow: number;
  hoursToEfloatStockout?: number | null;
  hoursToCashStockout?: number | null;
  riskType: LiquidityRisk;
  recommendedEfloatTopup: number;
  recommendedCashTopup: number;
  confidence: number;
  explanation: string;
  modelSource: string;
  modelVersion: string;
  createdAt: string;
  trajectory?: Array<{
    hour: string;
    efloat: number;
    efloatLower: number;
    efloatUpper: number;
    cash: number;
    cashLower: number;
    cashUpper: number;
  }>;
}

export interface CreditScoreResponse {
  id: string;
  agentId: string;
  agentCode: string;
  score: number;
  riskBand: RiskLevel;
  probabilityOfDefault: number;
  recommendedLimit: number;
  recommendedAdvanceAmount?: number;
  recommendedDailyRate: number;
  eligible?: boolean;
  recommendation?: string;
  factors: Array<{
    feature: string;
    value: string;
    contribution: number;
    direction: string;
    description: string;
  }>;
  explanation: string;
  modelSource: string;
  modelVersion: string;
  createdAt: string;
}

export interface CommissionSummary {
  agentId: string;
  agentCode: string;
  accruedPayable: number;
  accruedCount: number;
  lifetimeGrossDisbursed: number;
  lifetimeNetDisbursed: number;
}

export interface AdvanceResponse {
  id: string;
  reference: string;
  agentId: string;
  agentCode: string;
  agentName?: string;
  requestedAmount: number;
  approvedAmount?: number;
  dailyInterestRate: number;
  termDays: number;
  interestAmount?: number;
  principalOutstanding: number;
  interestOutstanding: number;
  totalOutstanding: number;
  status: string;
  purpose?: string;
  decisionNote?: string;
  creditScoreAtRequest?: number;
  requestedAt: string;
  dueDate?: string;
}

export interface ExchangeResponse {
  id: string;
  reference: string;
  anonymityCode?: string | null;
  status: string;
  direction: 'EFLOAT_FOR_CASH' | 'CASH_FOR_EFLOAT' | string;
  amount: number;
  requesterAgentId?: string | null;
  requesterAgentCode?: string | null;
  requesterBusinessName?: string | null;
  counterpartyAgentId?: string | null;
  counterpartyAgentCode?: string | null;
  counterpartyBusinessName?: string | null;
  note?: string;
  responseNote?: string;
  requesterGives?: string;
  requesterReceives?: string;
  requestedAt: string;
  expiresAt?: string;
  respondedAt?: string;
  matchedAt?: string | null;
  identityRevealed?: boolean;
  requesterVerified?: boolean;
  counterpartyVerified?: boolean;
  myVerifyToken?: string | null;
  chatEnabled?: boolean;
  /** @deprecated API returns requestedAt; kept optional for older clients */
  createdAt?: string;
}

export interface OpenListingView {
  id: string;
  reference: string;
  anonymityCode: string;
  direction: string;
  amount: number;
  note?: string | null;
  distanceKm?: number | null;
  requestedAt: string;
  expiresAt: string;
  seeking: 'EFLOAT' | 'CASH' | string;
  interestCount: number;
}

export interface ExchangeInterestView {
  id: string;
  anonymityCode: string;
  note?: string | null;
  status: string;
  createdAt: string;
  mine: boolean;
}

export interface ExchangeChatMessage {
  id: string;
  mine: boolean;
  body: string;
  createdAt: string;
  senderLabel: string;
}

export interface NotificationItem {
  id: string;
  type: string;
  severity?: RiskLevel;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  entityType?: string;
  entityId?: string;
}

export interface FeatureContribution {
  feature: string;
  value: string;
  contribution: number;
  direction: string;
  description: string;
}

export interface AssistantChatResponse {
  interactionId: string;
  answer: string;
  refused: boolean;
  engine: string;
  toolsUsed: Array<{
    name: string;
    classification: string;
    summary: string;
    ok: boolean;
  }>;
  dataClassifications: string[];
  disclaimer: string;
}

export interface AreaReport {
  areaCode: string;
  areaName: string;
  efloatPool: number;
  cashPool: number;
  agents: AgentSummary[];
  flaggedTransactions: number;
  agentsLowOnEfloat: number;
  agentsLowOnCash: number;
  transactions?: { count?: number; volume?: number };
}

export type FieldVisitType = 'ROUTINE_AUDIT' | 'REPLENISHMENT' | 'RISK_FOLLOWUP' | 'ONBOARDING' | 'OTHER';

export interface FieldVisitResponse {
  id: string;
  agentId: string;
  agentCode: string;
  agentName: string;
  areaId: string;
  areaManagerId: string;
  areaManagerName: string;
  visitType: FieldVisitType;
  notes: string;
  latitude?: number | null;
  longitude?: number | null;
  locationSimulated: boolean;
  verifiedCashBalance?: number | null;
  ledgerCashAtVisit?: number | null;
  discrepancyAmount?: number | null;
  clientLocalId?: string | null;
  visitedAt: string;
  createdAt: string;
}
