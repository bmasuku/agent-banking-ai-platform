import type { SimulationScenarioName } from './adminTypes'

export interface SimulationAction {
  label: string
  scenario: SimulationScenarioName
  hint?: string
}

export interface SimulationSection {
  title: string
  description: string
  actions: SimulationAction[]
}

export const SIMULATION_SECTIONS: SimulationSection[] = [
  {
    title: 'Daily activity',
    description: 'Typical trading patterns across the demo network.',
    actions: [
      { label: 'Normal transactions', scenario: 'NORMAL_AGENT_TRANSACTIONS' },
      { label: 'Customer deposits', scenario: 'CUSTOMER_DEPOSITS' },
      { label: 'Customer withdrawals', scenario: 'CUSTOMER_WITHDRAWALS' },
      { label: 'Accelerated activity', scenario: 'ACCELERATED_ACTIVITY' },
    ],
  },
  {
    title: 'Float stress',
    description: 'Liquidity limits, exchanges, advances, and forecasting.',
    actions: [
      { label: 'Insufficient float', scenario: 'INSUFFICIENT_FLOAT' },
      { label: 'Float exchange requests', scenario: 'FLOAT_EXCHANGE_REQUESTS' },
      { label: 'Float advance requests', scenario: 'FLOAT_ADVANCE_REQUESTS' },
      { label: 'Float predictions', scenario: 'FLOAT_PREDICTIONS' },
      { label: 'Area manager alerts', scenario: 'AREA_MANAGER_ALERTS' },
    ],
  },
  {
    title: 'Risk & fraud',
    description: 'Velocity, location, and credit stress scenarios.',
    actions: [
      { label: 'Suspicious transactions', scenario: 'SUSPICIOUS_TRANSACTIONS' },
      { label: 'Location mismatch alerts', scenario: 'LOCATION_MISMATCH_ALERTS' },
      { label: 'Loan default pressure', scenario: 'LOAN_DEFAULT_PRESSURE' },
    ],
  },
  {
    title: 'Commissions',
    description: 'Accrual and payout batches.',
    actions: [
      { label: 'Accumulate commissions', scenario: 'ACCUMULATE_COMMISSIONS' },
      { label: 'Commission disbursement', scenario: 'COMMISSION_DISBURSEMENT' },
    ],
  },
  {
    title: 'Legacy & other',
    description: 'Original scripted demo scenarios.',
    actions: [
      { label: 'Morning rush withdrawals', scenario: 'MORNING_RUSH_WITHDRAWALS' },
      { label: 'Market day deposits', scenario: 'MARKET_DAY_DEPOSITS' },
      { label: 'Mixed trading hour', scenario: 'MIXED_TRADING_HOUR' },
      { label: 'Fraud structuring attack', scenario: 'FRAUD_STRUCTURING_ATTACK' },
      { label: 'Peer rebalancing cycle', scenario: 'PEER_REBALANCING_CYCLE', hint: 'Needs ≥2 agents' },
    ],
  },
]