import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { errorMessage } from '@/api/client'
import { BrandLogo, BrandMark } from '@/components/BrandLogo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DEMO_ACCOUNTS } from '@/config'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/api'

function homeForRole(role: Role) {
  if (role === 'FINANCE_OFFICER') return '/finance'
  if (role === 'ADMIN') return '/admin'
  return '/risk'
}

export function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const user = useAuthStore((s) => s.user)
  const accessToken = useAuthStore((s) => s.accessToken)
  const [username, setUsername] = useState('risk')
  const [password, setPassword] = useState('Demo@2026!')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (accessToken && user) {
    return <Navigate to={homeForRole(user.role)} replace />
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const u = await login(username, password)
      navigate(homeForRole(u.role), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden p-6">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-14 bg-forest-deep" aria-hidden />
      <div
        className="pointer-events-none absolute inset-0 opacity-90"
        aria-hidden
        style={{
          backgroundImage:
            'radial-gradient(ellipse at 12% 18%, rgba(0,83,157,0.16), transparent 45%), radial-gradient(ellipse at 88% 78%, rgba(226,10,28,0.08), transparent 40%)',
        }}
      />
      <Card className="relative w-full max-w-md border-forest/15 shadow-lg shadow-forest/10">
        <CardHeader className="space-y-4">
          <div className="flex items-center gap-3">
            <BrandMark />
            <BrandLogo />
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-forest">Staff operations</p>
            <CardTitle className="mt-1 text-2xl font-semibold text-ink">Agent Banking Portal</CardTitle>
          </div>
          <CardDescription className="text-sm leading-relaxed">
            Sign in as Risk Officer, Finance Officer, or Admin. Role and permissions are assigned by the server —
            balances shown after login are live ledger data.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            {error ? (
              <div className="rounded-md border border-danger/20 bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
                {error}
              </div>
            ) : null}
            <div className="space-y-2">
              <Label htmlFor="username">Username</Label>
              <Input id="username" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          <div className="mt-6 space-y-2 rounded-lg border border-forest/10 bg-mint p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-forest">Demo accounts</p>
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.username}
                type="button"
                className="block w-full rounded-md px-2 py-1.5 text-left text-sm text-ink transition-colors hover:bg-white"
                onClick={() => {
                  setUsername(a.username)
                  setPassword(a.password)
                }}
              >
                <span className="font-semibold">{a.username}</span>
                <span className="text-ink-muted"> · {a.role}</span>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
