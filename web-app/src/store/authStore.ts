import { create } from 'zustand'
import { authApi } from '@/api/endpoints'
import { bindAuthHandlers } from '@/api/client'
import type { AuthenticatedUser, Role, TokenResponse } from '@/types/api'

const ACCESS_KEY = 'ab_web_access'
const REFRESH_KEY = 'ab_web_refresh'
const USER_KEY = 'ab_web_user'

type AuthState = {
  hydrated: boolean
  accessToken: string | null
  refreshToken: string | null
  user: AuthenticatedUser | null
  hydrate: () => void
  login: (username: string, password: string) => Promise<AuthenticatedUser>
  logout: () => Promise<void>
  setSession: (tokens: TokenResponse | null) => void
  hasRole: (...roles: Role[]) => boolean
}

function loadUser(): AuthenticatedUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthenticatedUser) : null
  } catch {
    return null
  }
}

export const useAuthStore = create<AuthState>((set, get) => {
  bindAuthHandlers({
    getTokens: () => ({
      accessToken: get().accessToken,
      refreshToken: get().refreshToken,
    }),
    setTokens: (tokens) => {
      get().setSession(tokens)
    },
    onAuthFailure: () => {
      get().setSession(null)
    },
  })

  return {
    hydrated: false,
    accessToken: null,
    refreshToken: null,
    user: null,

    setSession: (tokens) => {
      if (!tokens) {
        localStorage.removeItem(ACCESS_KEY)
        localStorage.removeItem(REFRESH_KEY)
        localStorage.removeItem(USER_KEY)
        set({ accessToken: null, refreshToken: null, user: null })
        return
      }
      localStorage.setItem(ACCESS_KEY, tokens.accessToken)
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
      localStorage.setItem(USER_KEY, JSON.stringify(tokens.user))
      set({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        user: tokens.user,
      })
    },

    hydrate: () => {
      const accessToken = localStorage.getItem(ACCESS_KEY)
      const refreshToken = localStorage.getItem(REFRESH_KEY)
      const user = loadUser()
      set({ accessToken, refreshToken, user, hydrated: true })
      if (accessToken) {
        void authApi
          .me()
          .then((me) => {
            localStorage.setItem(USER_KEY, JSON.stringify(me))
            set({ user: me })
          })
          .catch(() => undefined)
      }
    },

    login: async (username, password) => {
      const tokens = await authApi.login(username.trim(), password)
      get().setSession(tokens)
      return tokens.user
    },

    logout: async () => {
      try {
        await authApi.logout(get().refreshToken)
      } catch {
        // ignore
      }
      get().setSession(null)
    },

    hasRole: (...roles) => {
      const role = get().user?.role
      return role ? roles.includes(role) : false
    },
  }
})
