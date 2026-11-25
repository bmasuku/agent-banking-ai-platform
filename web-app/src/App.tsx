import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AppShell } from '@/layouts/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { AssistantPage } from '@/pages/AssistantPage'
import {
  AgentRiskPage,
  FlaggedTransactionsPage,
  FraudDetailPage,
  InvestigationDetailPage,
  InvestigationsPage,
  RiskDashboardPage,
} from '@/pages/risk'
import {
  AdvanceDetailPage,
  AdvancesPage,
  AgentStatementsPage,
  CommissionsPage,
  FinanceDashboardPage,
  ReconciliationPage,
  ReportsPage,
} from '@/pages/finance'
import {
  AdminDashboardPage,
  AgentsAdminPage,
  AiMonitoringPage,
  AreasPage,
  AuditLogsPage,
  ConfigPage,
  DemoResetPage,
  SimulationsPage,
  UsersPage,
} from '@/pages/admin'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/api'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000, refetchOnWindowFocus: false },
  },
})

function HomeRedirect() {
  const role = useAuthStore((s) => s.user?.role)
  if (role === 'FINANCE_OFFICER') return <Navigate to="/finance" replace />
  if (role === 'ADMIN') return <Navigate to="/admin" replace />
  return <Navigate to="/risk" replace />
}

function RoleHome({ roles, children }: { roles: Role[]; children: React.ReactNode }) {
  const role = useAuthStore((s) => s.user?.role)
  if (!role) return <Navigate to="/login" replace />
  if (role !== 'ADMIN' && !roles.includes(role)) {
    return <HomeRedirect />
  }
  return <>{children}</>
}

export default function App() {
  const hydrate = useAuthStore((s) => s.hydrate)

  useEffect(() => {
    hydrate()
  }, [hydrate])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route index element={<HomeRedirect />} />
              <Route path="risk" element={<RoleHome roles={['RISK_OFFICER']}><RiskDashboardPage /></RoleHome>} />
              <Route path="risk/flagged" element={<RoleHome roles={['RISK_OFFICER']}><FlaggedTransactionsPage /></RoleHome>} />
              <Route path="risk/fraud/:id" element={<RoleHome roles={['RISK_OFFICER']}><FraudDetailPage /></RoleHome>} />
              <Route path="risk/investigations" element={<RoleHome roles={['RISK_OFFICER']}><InvestigationsPage /></RoleHome>} />
              <Route path="risk/investigations/:id" element={<RoleHome roles={['RISK_OFFICER']}><InvestigationDetailPage /></RoleHome>} />
              <Route path="risk/agents" element={<RoleHome roles={['RISK_OFFICER']}><AgentRiskPage /></RoleHome>} />

              <Route path="finance" element={<RoleHome roles={['FINANCE_OFFICER']}><FinanceDashboardPage /></RoleHome>} />
              <Route path="finance/commissions" element={<RoleHome roles={['FINANCE_OFFICER']}><CommissionsPage /></RoleHome>} />
              <Route path="finance/advances" element={<RoleHome roles={['FINANCE_OFFICER']}><AdvancesPage /></RoleHome>} />
              <Route path="finance/advances/:id" element={<RoleHome roles={['FINANCE_OFFICER']}><AdvanceDetailPage /></RoleHome>} />
              <Route path="finance/reports" element={<RoleHome roles={['FINANCE_OFFICER']}><ReportsPage /></RoleHome>} />
              <Route path="finance/statements" element={<RoleHome roles={['FINANCE_OFFICER']}><AgentStatementsPage /></RoleHome>} />
              <Route path="finance/reconciliation" element={<RoleHome roles={['FINANCE_OFFICER']}><ReconciliationPage /></RoleHome>} />

              <Route path="admin" element={<RoleHome roles={['ADMIN']}><AdminDashboardPage /></RoleHome>} />
              <Route path="admin/users" element={<RoleHome roles={['ADMIN']}><UsersPage /></RoleHome>} />
              <Route path="admin/agents" element={<RoleHome roles={['ADMIN']}><AgentsAdminPage /></RoleHome>} />
              <Route path="admin/areas" element={<RoleHome roles={['ADMIN']}><AreasPage /></RoleHome>} />
              <Route path="admin/config" element={<RoleHome roles={['ADMIN']}><ConfigPage /></RoleHome>} />
              <Route path="admin/ai" element={<RoleHome roles={['ADMIN']}><AiMonitoringPage /></RoleHome>} />
              <Route path="admin/simulations" element={<RoleHome roles={['ADMIN']}><SimulationsPage /></RoleHome>} />
              <Route path="admin/audit" element={<RoleHome roles={['ADMIN']}><AuditLogsPage /></RoleHome>} />
              <Route path="admin/demo" element={<RoleHome roles={['ADMIN']}><DemoResetPage /></RoleHome>} />

              <Route path="assistant" element={<AssistantPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  )
}
