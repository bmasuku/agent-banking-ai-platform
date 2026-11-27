import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';
import { authApi } from '../api/endpoints';
import { bindAuthHandlers } from '../api/client';
import { AuthenticatedUser, Role, TokenResponse } from '../types/api';

const ACCESS_KEY = 'ab_access';
const REFRESH_KEY = 'ab_refresh';
const USER_KEY = 'ab_user';

export type AuthUser = AuthenticatedUser;

type AuthState = {
  hydrated: boolean;
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  hydrate: () => Promise<void>;
  login: (username: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  setSession: (tokens: TokenResponse | null) => Promise<void>;
  primaryRole: () => Role | null;
};

function pickPrimaryRole(user: AuthUser | null): Role | null {
  if (!user?.role) return null;
  return user.role;
}

async function persistUser(user: AuthUser) {
  await SecureStore.setItemAsync(USER_KEY, JSON.stringify(user));
}

export const useAuthStore = create<AuthState>((set, get) => {
  bindAuthHandlers({
    getTokens: () => ({
      accessToken: get().accessToken,
      refreshToken: get().refreshToken,
    }),
    setTokens: async (tokens) => {
      if (!tokens) {
        await SecureStore.deleteItemAsync(ACCESS_KEY);
        await SecureStore.deleteItemAsync(REFRESH_KEY);
        await SecureStore.deleteItemAsync(USER_KEY);
        set({ accessToken: null, refreshToken: null, user: null });
        return;
      }
      await SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken);
      await SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken);
      await persistUser(tokens.user);
      set({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        user: tokens.user,
      });
    },
    onAuthFailure: () => {
      void SecureStore.deleteItemAsync(ACCESS_KEY);
      void SecureStore.deleteItemAsync(REFRESH_KEY);
      void SecureStore.deleteItemAsync(USER_KEY);
      set({ accessToken: null, refreshToken: null, user: null });
    },
  });

  return {
    hydrated: false,
    accessToken: null,
    refreshToken: null,
    user: null,

    setSession: async (tokens) => {
      if (!tokens) {
        await SecureStore.deleteItemAsync(ACCESS_KEY);
        await SecureStore.deleteItemAsync(REFRESH_KEY);
        await SecureStore.deleteItemAsync(USER_KEY);
        set({ accessToken: null, refreshToken: null, user: null });
        return;
      }
      await SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken);
      await SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken);
      await persistUser(tokens.user);
      set({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        user: tokens.user,
      });
    },

    hydrate: async () => {
      try {
        const [access, refresh, rawUser] = await Promise.all([
          SecureStore.getItemAsync(ACCESS_KEY),
          SecureStore.getItemAsync(REFRESH_KEY),
          SecureStore.getItemAsync(USER_KEY),
        ]);
        const user = rawUser ? (JSON.parse(rawUser) as AuthUser) : null;
        set({ accessToken: access, refreshToken: refresh, user, hydrated: true });
        if (access) {
          try {
            const me = await authApi.me();
            await persistUser(me);
            set({ user: me });
          } catch {
            // keep cached session; interceptor clears on hard auth failure
          }
        }
      } catch {
        set({ hydrated: true });
      }
    },

    login: async (username, password) => {
      const tokens = await authApi.login(username.trim(), password);
      await get().setSession(tokens);
      return tokens.user;
    },

    logout: async () => {
      const refresh = get().refreshToken;
      try {
        await authApi.logout(refresh);
      } catch {
        // ignore network errors on logout
      }
      await get().setSession(null);
    },

    primaryRole: () => pickPrimaryRole(get().user),
  };
});
