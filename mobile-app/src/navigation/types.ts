import type { NavigatorScreenParams } from '@react-navigation/native';
import type { TransactionResult } from '../types/api';

export type AuthStackParamList = {
  Login: undefined;
};

export type AgentTabParamList = {
  AgentHome: undefined;
  AgentOps: undefined;
  AgentHistory: undefined;
  AgentAssistant: undefined;
  AgentMore: undefined;
};

export type AgentStackParamList = {
  AgentTabs: NavigatorScreenParams<AgentTabParamList>;
  CashDeposit: undefined;
  Withdrawal: undefined;
  TransactionConfirm: { result: TransactionResult };
  Efloat: undefined;
  CashInHand: undefined;
  RequestAdvance: undefined;
  NearbyAgents: undefined;
  FloatExchange: undefined;
  ExchangeDetail: { exchangeId: string };
  Commission: undefined;
  CreditScore: undefined;
  AdvanceHistory: undefined;
  Notifications: undefined;
  Profile: undefined;
};

export type AreaTabParamList = {
  AreaHome: undefined;
  AreaAgents: undefined;
  AreaMap: undefined;
  AreaAssistant: undefined;
  AreaMore: undefined;
};

export type AreaStackParamList = {
  AreaTabs: NavigatorScreenParams<AreaTabParamList>;
  AgentProfile: { agentId: string };
  FieldVisits: undefined;
  CheckIn: { agentId?: string } | undefined;
  Replenish: { agentId?: string } | undefined;
  FloatPredictions: undefined;
  ExchangeMonitor: undefined;
  AreaPerformance: undefined;
  Notifications: undefined;
  Profile: undefined;
};

export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>;
  Agent: NavigatorScreenParams<AgentStackParamList>;
  Area: NavigatorScreenParams<AreaStackParamList>;
};
