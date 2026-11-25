import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/api'

const STAFF_ROLES: Role[] = ['RISK_OFFICER', 'FINANCE_OFFICER', 'ADMIN']

export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const hydrated = useAuthStore((s) => s.hydrated)
  const accessToken = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center text-ink-muted">
        Restoring session…
      </div>
    )
  }

  if (!accessToken || !user) {
    return <Navigate to="/login" replace />
  }

  if (!STAFF_ROLES.includes(user.role)) {
    return <Navigate to="/login" replace />
  }

  if (roles && !roles.includes(user.role) && user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
