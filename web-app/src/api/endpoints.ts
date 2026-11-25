import { api } from './client'
import type {
  AdvanceDetail,
  AdvanceResponse,
  AgentRiskResponse,
  AssistantChatResponse,
  AuthenticatedUser,
  DisbursementPreview,
  FlaggedTransaction,
  FraudCheckDetail,
  Investigation,
  InvestigationDetail,
  PageResponse,
  RiskDashboard,
  SettingResponse,
  TariffResponse,
  TokenResponse,
} from '@/types/api'

export const authApi = {
  login: (username: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { username, password }).then((r) => r.data),
  me: () => api.get<AuthenticatedUser>('/auth/me').then((r) => r.data),
  logout: (refreshToken: string | null) =>
    api.post('/auth/logout', { refreshToken, allSessions: false }).then((r) => r.data),
}

export const riskApi = {
  dashboard: () => api.get<RiskDashboard>('/risk/dashboard').then((r) => r.data),
  flagged: (params?: Record<string, unknown>) =>
    api.get<PageResponse<FlaggedTransaction>>('/risk/flagged-transactions', { params }).then((r) => r.data),
  fraudCheck: (id: string) => api.get<FraudCheckDetail>(`/risk/fraud-checks/${id}`).then((r) => r.data),
  review: (id: string, body: { status: string; note: string }) =>
    api.post<FraudCheckDetail>(`/risk/fraud-checks/${id}/review`, body).then((r) => r.data),
  investigations: (params?: Record<string, unknown>) =>
    api.get<PageResponse<Investigation>>('/risk/investigations', { params }).then((r) => r.data),
  investigation: (id: string) => api.get<InvestigationDetail>(`/risk/investigations/${id}`).then((r) => r.data),
  createInvestigation: (body: Record<string, unknown>) =>
    api.post<Investigation>('/risk/investigations', body).then((r) => r.data),
  addNote: (id: string, note: string) =>
    api.post(`/risk/investigations/${id}/notes`, { note }).then((r) => r.data),
  updateStatus: (id: string, status: string, note?: string) =>
    api.post(`/risk/investigations/${id}/status`, { status, note }).then((r) => r.data),
  outcome: (id: string, body: Record<string, unknown>) =>
    api.post(`/risk/investigations/${id}/outcome`, body).then((r) => r.data),
  agents: (areaId?: string) =>
    api.get<AgentRiskResponse[]>('/risk/agents', { params: { areaId } }).then((r) => r.data),
  setAgentStatus: (agentId: string, status: string, reason: string) =>
    api.post(`/risk/agents/${agentId}/status`, { status, reason }).then((r) => r.data),
  anomalies: (agentId: string) =>
    api.get(`/risk/agents/${agentId}/anomalies`).then((r) => r.data),
  peerComparison: (areaId: string) =>
    api.get('/risk/peer-comparison', { params: { areaId } }).then((r) => r.data),
}

export const financeApi = {
  commissions: (params?: Record<string, unknown>) =>
    api.get('/finance/commissions', { params }).then((r) => r.data),
  preview: (agentId: string) =>
    api.get<DisbursementPreview>(`/finance/commissions/agents/${agentId}/preview`).then((r) => r.data),
  disburse: (agentId: string, key: string) =>
    api
      .post(`/finance/commissions/agents/${agentId}/disburse`, {}, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  disburseBatch: (key: string) =>
    api
      .post('/finance/commissions/disburse-batch', {}, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  disbursements: (params?: Record<string, unknown>) =>
    api.get('/finance/disbursements', { params }).then((r) => r.data),
  advances: (params?: Record<string, unknown>) =>
    api.get<PageResponse<AdvanceResponse>>('/finance/advances', { params }).then((r) => r.data),
  advance: (id: string) => api.get<AdvanceDetail>(`/finance/advances/${id}`).then((r) => r.data),
  approveAdvance: (id: string, body: Record<string, unknown>) =>
    api.post(`/finance/advances/${id}/approve`, body).then((r) => r.data),
  rejectAdvance: (id: string, note: string) =>
    api.post(`/finance/advances/${id}/reject`, { note }).then((r) => r.data),
  repayAdvance: (id: string, amount: number, key: string) =>
    api
      .post(`/finance/advances/${id}/repayments`, { amount }, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  runPenalties: () => api.post('/finance/advances/penalties/run').then((r) => r.data),
  replenishments: (params?: Record<string, unknown>) =>
    api.get('/finance/replenishments', { params }).then((r) => r.data),
  trialBalance: () => api.get('/finance/reports/trial-balance').then((r) => r.data),
  incomeStatement: (params?: Record<string, unknown>) =>
    api.get('/finance/reports/income-statement', { params }).then((r) => r.data),
  floatPosition: () => api.get('/finance/reports/float-position').then((r) => r.data),
  advanceBook: () => api.get('/finance/reports/advance-book').then((r) => r.data),
  commissionsReport: () => api.get('/finance/reports/commissions').then((r) => r.data),
  transactionsReport: (params?: Record<string, unknown>) =>
    api.get('/finance/reports/transactions', { params }).then((r) => r.data),
  areaReport: (areaId: string) => api.get(`/finance/reports/areas/${areaId}`).then((r) => r.data),
  fundVault: (body: { amount: number; reason?: string }, key: string) =>
    api
      .post('/finance/treasury/vault-funding', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  fundPool: (areaId: string, body: Record<string, unknown>, key: string) =>
    api
      .post(`/finance/areas/${areaId}/pool-funding`, body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
}

export const adminApi = {
  users: (params?: Record<string, unknown>) => api.get('/admin/users', { params }).then((r) => r.data),
  user: (id: string) => api.get(`/admin/users/${id}`).then((r) => r.data),
  createUser: (body: Record<string, unknown>) => api.post('/admin/users', body).then((r) => r.data),
  updateUser: (id: string, body: Record<string, unknown>) =>
    api.put(`/admin/users/${id}`, body).then((r) => r.data),
  userStatus: (id: string, status: string) =>
    api.post(`/admin/users/${id}/status`, { status }).then((r) => r.data),
  resetPassword: (id: string, body: Record<string, unknown>) =>
    api.post(`/admin/users/${id}/reset-password`, body).then((r) => r.data),
  createAgent: (body: Record<string, unknown>) => api.post('/admin/agents', body).then((r) => r.data),
  updateAgent: (id: string, body: Record<string, unknown>) =>
    api.put(`/admin/agents/${id}`, body).then((r) => r.data),
  settings: () => api.get<SettingResponse[]>('/admin/settings').then((r) => r.data),
  updateSetting: (key: string, value: string) =>
    api.put(`/admin/settings/${key}`, { value }).then((r) => r.data),
  tariffs: () => api.get<TariffResponse[]>('/admin/tariffs').then((r) => r.data),
  createTariff: (body: Record<string, unknown>) => api.post('/admin/tariffs', body).then((r) => r.data),
  updateTariff: (id: string, body: Record<string, unknown>) =>
    api.put(`/admin/tariffs/${id}`, body).then((r) => r.data),
  aiStatus: () => api.get('/admin/ai/status').then((r) => r.data),
  aiModels: () => api.get('/admin/ai/models').then((r) => r.data),
  aiPredictions: (params?: Record<string, unknown>) =>
    api.get('/admin/ai/predictions', { params }).then((r) => r.data),
  aiSummary: () => api.get('/admin/ai/summary').then((r) => r.data),
  demoReset: () => api.post('/admin/demo/reset', { confirm: 'RESET' }).then((r) => r.data),
  simScenarios: () => api.get('/admin/simulations/scenarios').then((r) => r.data),
  /** POST /api/v1/admin/simulations */
  runSimulation: (body: Record<string, unknown>) => api.post('/admin/simulations', body).then((r) => r.data),
  simulations: (params?: Record<string, unknown>) =>
    api.get('/admin/simulations', { params }).then((r) => r.data),
  simulation: (id: string) => api.get(`/admin/simulations/${id}`).then((r) => r.data),
  createArea: (body: Record<string, unknown>) => api.post('/admin/areas', body).then((r) => r.data),
}

export const sharedApi = {
  agents: (params?: Record<string, unknown>) => api.get('/agents', { params }).then((r) => r.data),
  agent: (id: string) => api.get(`/agents/${id}`).then((r) => r.data),
  agentBalances: (id: string) => api.get(`/agents/${id}/balances`).then((r) => r.data),
  agentStatement: (id: string, params?: Record<string, unknown>) =>
    api.get(`/agents/${id}/statement`, { params }).then((r) => r.data),
  agentLocations: () => api.get('/agents/locations').then((r) => r.data),
  transactions: (params?: Record<string, unknown>) =>
    api.get('/transactions', { params }).then((r) => r.data),
  transaction: (id: string) => api.get(`/transactions/${id}`).then((r) => r.data),
  areas: () => api.get('/areas').then((r) => r.data),
  area: (id: string) => api.get(`/areas/${id}`).then((r) => r.data),
  creditScore: (agentId: string) =>
    api.get(`/credit-scores/agents/${agentId}`).then((r) => r.data),
  evaluateCredit: (agentId: string, requestedAmount?: number) =>
    api
      .post(`/credit-scores/agents/${agentId}/evaluate`, null, { params: { requestedAmount } })
      .then((r) => r.data),
  notifications: () => api.get('/notifications', { params: { size: 50 } }).then((r) => r.data),
  unreadCount: () => api.get('/notifications/unread-count').then((r) => r.data),
  auditLogs: (params?: Record<string, unknown>) =>
    api.get('/audit-logs', { params }).then((r) => r.data),
  assistantChat: (question: string, conversation: Array<{ role: string; content: string }> = []) =>
    api
      .post<AssistantChatResponse>('/assistant/chat', { question, conversation })
      .then((r) => r.data),
}
