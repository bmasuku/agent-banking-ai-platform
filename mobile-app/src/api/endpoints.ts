import { api } from './client';
import {
  AdvanceResponse,
  AgentBalanceView,
  AgentLocation,
  AgentProfile,
  AgentSummary,
  AreaReport,
  AreaResponse,
  AssistantChatResponse,
  AuthenticatedUser,
  CommissionSummary,
  CreditScoreResponse,
  ExchangeChatMessage,
  ExchangeInterestView,
  ExchangeResponse,
  FieldVisitResponse,
  OpenListingView,
  NotificationItem,
  PageResponse,
  PredictionResponse,
  QuoteResponse,
  TokenResponse,
  TransactionResult,
  TransactionSummary,
} from '../types/api';

export const authApi = {
  login: (username: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { username, password }).then((r) => r.data),
  me: () => api.get<AuthenticatedUser>('/auth/me').then((r) => r.data),
  logout: (refreshToken: string | null, allSessions = false) =>
    api.post('/auth/logout', { refreshToken, allSessions }).then((r) => r.data),
};

export const agentApi = {
  me: () => api.get<AgentProfile>('/agent/me').then((r) => r.data),
  balances: () => api.get<AgentBalanceView>('/agent/balances').then((r) => r.data),
  statement: (type: 'EFLOAT' | 'CASH' | 'COMMISSION_PAYABLE' | 'ADVANCE_PRINCIPAL', page = 0) =>
    api.get(`/agent/statement`, { params: { type, page, size: 30 } }).then((r) => r.data),
  peers: () => api.get<AgentSummary[]>('/agent/peers').then((r) => r.data),
  quote: (type: string, amount: number) =>
    api.post<QuoteResponse>('/agent/transactions/quote', { type, amount }).then((r) => r.data),
  deposit: (body: Record<string, unknown>, key: string) =>
    api
      .post<TransactionResult>('/agent/transactions/deposit', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  withdrawal: (body: Record<string, unknown>, key: string) =>
    api
      .post<TransactionResult>('/agent/transactions/withdrawal', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  transactions: (params?: Record<string, unknown>) =>
    api
      .get<PageResponse<TransactionSummary>>('/agent/transactions', { params: { size: 30, ...params } })
      .then((r) => r.data),
  transaction: (id: string) => api.get(`/agent/transactions/${id}`).then((r) => r.data),
  commissionsSummary: () => api.get<CommissionSummary>('/agent/commissions/summary').then((r) => r.data),
  commissions: () =>
    api.get<PageResponse<unknown>>('/agent/commissions', { params: { size: 30 } }).then((r) => r.data),
  advances: () =>
    api.get<PageResponse<AdvanceResponse>>('/agent/advances', { params: { size: 30 } }).then((r) => r.data),
  advance: (id: string) => api.get<AdvanceResponse>(`/agent/advances/${id}`).then((r) => r.data),
  requestAdvance: (body: { amount: number; termDays: number; purpose: string }, key: string) =>
    api
      .post<AdvanceResponse>('/agent/advances', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  repayAdvance: (id: string, amount: number, key: string) =>
    api
      .post(`/agent/advances/${id}/repayments`, { amount }, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  exchanges: () =>
    api.get<PageResponse<ExchangeResponse>>('/agent/exchanges', { params: { size: 30 } }).then((r) => r.data),
  exchangeBoard: () => api.get<OpenListingView[]>('/agent/exchanges/board').then((r) => r.data),
  openExchangeListing: (
    body: { direction: 'EFLOAT_FOR_CASH' | 'CASH_FOR_EFLOAT'; amount: number; note?: string },
    key: string,
  ) =>
    api
      .post<ExchangeResponse>('/agent/exchanges/open', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  proposeExchange: (
    body: { counterpartyAgentId: string; direction: string; amount: number; note?: string },
    key: string,
  ) =>
    api
      .post<ExchangeResponse>('/agent/exchanges', body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  expressExchangeInterest: (id: string, note?: string) =>
    api.post<ExchangeInterestView>(`/agent/exchanges/${id}/interest`, { note }).then((r) => r.data),
  exchangeInterests: (id: string) =>
    api.get<ExchangeInterestView[]>(`/agent/exchanges/${id}/interests`).then((r) => r.data),
  matchExchangeInterest: (id: string, interestId: string) =>
    api.post<ExchangeResponse>(`/agent/exchanges/${id}/match`, { interestId }).then((r) => r.data),
  verifyExchange: (id: string, partnerToken: string) =>
    api.post<ExchangeResponse>(`/agent/exchanges/${id}/verify`, { partnerToken }).then((r) => r.data),
  completeExchange: (id: string) =>
    api.post<ExchangeResponse>(`/agent/exchanges/${id}/complete`, {}).then((r) => r.data),
  exchangeMessages: (id: string) =>
    api.get<ExchangeChatMessage[]>(`/agent/exchanges/${id}/messages`).then((r) => r.data),
  postExchangeMessage: (id: string, body: string) =>
    api.post<ExchangeChatMessage>(`/agent/exchanges/${id}/messages`, { body }).then((r) => r.data),
  getExchange: (id: string) => api.get<ExchangeResponse>(`/agent/exchanges/${id}`).then((r) => r.data),
  acceptExchange: (id: string, key: string) =>
    api.post(`/agent/exchanges/${id}/accept`, {}, { headers: { 'Idempotency-Key': key } }).then((r) => r.data),
  rejectExchange: (id: string, key: string) =>
    api.post(`/agent/exchanges/${id}/reject`, {}, { headers: { 'Idempotency-Key': key } }).then((r) => r.data),
  cancelExchange: (id: string, key: string) =>
    api.post(`/agent/exchanges/${id}/cancel`, {}, { headers: { 'Idempotency-Key': key } }).then((r) => r.data),
};

export const areaApi = {
  area: () => api.get<AreaResponse>('/area-manager/area').then((r) => r.data),
  agents: () => api.get<AgentSummary[]>('/area-manager/agents').then((r) => r.data),
  agent: (id: string) => api.get<AgentProfile>(`/area-manager/agents/${id}`).then((r) => r.data),
  agentBalances: (id: string) => api.get<AgentBalanceView>(`/area-manager/agents/${id}/balances`).then((r) => r.data),
  agentTransactions: (id: string) =>
    api
      .get<PageResponse<TransactionSummary>>(`/area-manager/agents/${id}/transactions`, { params: { size: 30 } })
      .then((r) => r.data),
  locations: () => api.get<AgentLocation[]>('/area-manager/locations').then((r) => r.data),
  predictions: () => api.get<PredictionResponse[]>('/area-manager/predictions').then((r) => r.data),
  predict: (agentId: string, horizonHours = 24) =>
    api
      .post<PredictionResponse>(`/area-manager/agents/${agentId}/predictions`, null, { params: { horizonHours } })
      .then((r) => r.data),
  replenish: (
    agentId: string,
    body: { type: 'EFLOAT' | 'CASH'; amount: number; reason: string; predictionId?: string },
    key: string,
  ) =>
    api
      .post(`/area-manager/agents/${agentId}/replenishments`, body, { headers: { 'Idempotency-Key': key } })
      .then((r) => r.data),
  replenishments: () => api.get('/area-manager/replenishments', { params: { size: 30 } }).then((r) => r.data),
  exchanges: () =>
    api.get<PageResponse<ExchangeResponse>>('/area-manager/exchanges', { params: { size: 30 } }).then((r) => r.data),
  exchangeBoard: () => api.get<OpenListingView[]>('/area-manager/exchanges/board').then((r) => r.data),
  transactions: () =>
    api
      .get<PageResponse<TransactionSummary>>('/area-manager/transactions', { params: { size: 30 } })
      .then((r) => r.data),
  alerts: () => api.get('/area-manager/alerts', { params: { size: 30 } }).then((r) => r.data),
  report: () => api.get<AreaReport>('/area-manager/reports/area').then((r) => r.data),
  optimisation: () => api.get('/area-manager/optimisation').then((r) => r.data),
  forecast: () => api.get('/area-manager/forecast').then((r) => r.data),
  createFieldVisit: (body: {
    agentId: string;
    notes: string;
    visitType?: 'ROUTINE_AUDIT' | 'REPLENISHMENT' | 'RISK_FOLLOWUP' | 'ONBOARDING' | 'OTHER';
    latitude?: number;
    longitude?: number;
    locationSimulated?: boolean;
    verifiedCashBalance?: number;
    clientLocalId?: string;
    visitedAt?: string;
  }) => api.post<FieldVisitResponse>('/area-manager/field-visits', body).then((r) => r.data),
  fieldVisits: (params?: { agentId?: string; page?: number; size?: number }) =>
    api
      .get<PageResponse<FieldVisitResponse>>('/area-manager/field-visits', {
        params: { size: 50, ...params },
      })
      .then((r) => r.data),
};

export const sharedApi = {
  prediction: (agentId: string) =>
    api.get<PredictionResponse>(`/predictions/agents/${agentId}`).then((r) => r.data),
  creditScore: (agentId: string) =>
    api.get<CreditScoreResponse>(`/credit-scores/agents/${agentId}`).then((r) => r.data),
  notifications: () =>
    api.get<PageResponse<NotificationItem>>('/notifications', { params: { size: 50 } }).then((r) => r.data),
  unreadCount: () => api.get<{ count: number } | number>('/notifications/unread-count').then((r) => r.data),
  markRead: (id: string) => api.post(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.post('/notifications/read-all').then((r) => r.data),
  assistantChat: (question: string, conversation: Array<{ role: string; content: string }> = []) =>
    api.post<AssistantChatResponse>('/assistant/chat', { question, conversation }).then((r) => r.data),
};
