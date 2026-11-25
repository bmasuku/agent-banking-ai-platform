import {
  Bot,
  Building2,
  CircleDollarSign,
  FileSpreadsheet,
  LayoutDashboard,
  LogOut,
  Scale,
  Settings2,
  ShieldAlert,
  Sparkles,
  Users,
} from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useRealtime } from '@/hooks/useRealtime'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/api'

type NavItem = { to: string; label: string; icon: React.ComponentType<{ className?: string }>; roles: Role[] }

const NAV: NavItem[] = [
  { to: '/risk', label: 'Risk dashboard', icon: ShieldAlert, roles: ['RISK_OFFICER', 'ADMIN'] },
  { to: '/risk/flagged', label: 'Flagged transactions', icon: ShieldAlert, roles: ['RISK_OFFICER', 'ADMIN'] },
  { to: '/risk/investigations', label: 'Investigations', icon: Scale, roles: ['RISK_OFFICER', 'ADMIN'] },
  { to: '/risk/agents', label: 'Agent risk', icon: Building2, roles: ['RISK_OFFICER', 'ADMIN'] },
  { to: '/finance', label: 'Finance dashboard', icon: CircleDollarSign, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/finance/commissions', label: 'Commissions', icon: CircleDollarSign, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/finance/advances', label: 'Advances', icon: CircleDollarSign, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/finance/reports', label: 'Reports', icon: FileSpreadsheet, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/finance/statements', label: 'Agent statements', icon: FileSpreadsheet, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/finance/reconciliation', label: 'Reconciliation', icon: LayoutDashboard, roles: ['FINANCE_OFFICER', 'ADMIN'] },
  { to: '/admin', label: 'Admin overview', icon: Settings2, roles: ['ADMIN'] },
  { to: '/admin/users', label: 'Users & roles', icon: Users, roles: ['ADMIN'] },
  { to: '/admin/agents', label: 'Agents', icon: Building2, roles: ['ADMIN'] },
  { to: '/admin/areas', label: 'Areas', icon: Building2, roles: ['ADMIN'] },
  { to: '/admin/config', label: 'Configuration', icon: Settings2, roles: ['ADMIN'] },
  { to: '/admin/ai', label: 'AI monitoring', icon: Sparkles, roles: ['ADMIN'] },
  { to: '/admin/simulations', label: 'Simulations', icon: Settings2, roles: ['ADMIN'] },
  { to: '/admin/audit', label: 'Audit logs', icon: FileSpreadsheet, roles: ['ADMIN'] },
  { to: '/admin/demo', label: 'Demo reset', icon: Settings2, roles: ['ADMIN'] },
  { to: '/assistant', label: 'AI assistant', icon: Bot, roles: ['RISK_OFFICER', 'FINANCE_OFFICER', 'ADMIN'] },
]

export function AppShell() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const role = user?.role
  const items = NAV.filter((n) => role && (role === 'ADMIN' || n.roles.includes(role)))
  useRealtime()

  return (
    <div className="mx-auto flex min-h-screen max-w-[1600px]">
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-line bg-white/80 p-4 backdrop-blur md:flex">
        <div className="mb-8 px-2">
          <div className="font-display text-xl font-semibold text-forest">Agent Banking</div>
          <p className="text-xs text-ink-muted">Staff operations portal</p>
        </div>
        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/risk' || item.to === '/finance' || item.to === '/admin'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-ink-muted hover:bg-mint hover:text-forest',
                  isActive && 'bg-mint text-forest',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-4 border-t border-line pt-4">
          <div className="px-2 text-sm font-semibold text-ink">{user?.fullName}</div>
          <div className="px-2 text-xs text-ink-muted">{user?.role}</div>
          <Button variant="ghost" className="mt-2 w-full justify-start" onClick={() => void logout()}>
            <LogOut className="h-4 w-4" /> Sign out
          </Button>
        </div>
      </aside>
      <div className="flex flex-1 flex-col min-w-0">
        <header className="border-b border-line bg-white/70 backdrop-blur md:hidden">
          <div className="flex items-center justify-between px-4 py-3">
            <div>
              <div className="font-display text-lg font-semibold text-forest">Agent Banking</div>
              <p className="text-[11px] text-ink-muted">{user?.role?.replace(/_/g, ' ')}</p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-3">
            {items.map((item) => (
              <NavLink
                key={`m-${item.to}`}
                to={item.to}
                end={item.to === '/risk' || item.to === '/finance' || item.to === '/admin'}
                className={({ isActive }) =>
                  cn(
                    'shrink-0 rounded-md px-3 py-1.5 text-xs font-medium text-ink-muted whitespace-nowrap',
                    isActive && 'bg-mint text-forest',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="flex-1 p-4 md:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
