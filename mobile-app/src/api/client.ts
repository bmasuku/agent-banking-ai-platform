import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config';
import { TokenResponse } from '../types/api';

type TokenGetter = () => { accessToken: string | null; refreshToken: string | null };
type TokenSetter = (tokens: TokenResponse | null) => void | Promise<void>;

let getTokens: TokenGetter = () => ({ accessToken: null, refreshToken: null });
let setTokens: TokenSetter = () => undefined;
let onAuthFailure: () => void = () => undefined;
let refreshPromise: Promise<string | null> | null = null;

export function bindAuthHandlers(handlers: {
  getTokens: TokenGetter;
  setTokens: TokenSetter;
  onAuthFailure: () => void;
}) {
  getTokens = handlers.getTokens;
  setTokens = handlers.setTokens;
  onAuthFailure = handlers.onAuthFailure;
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const { accessToken } = getTokens();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken } = getTokens();
  if (!refreshToken) {
    return null;
  }
  try {
    const { data } = await axios.post<TokenResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    await setTokens(data);
    return data.accessToken;
  } catch {
    await setTokens(null);
    onAuthFailure();
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status !== 401 || !original || original._retry) {
      return Promise.reject(error);
    }
    if (original.url?.includes('/auth/login') || original.url?.includes('/auth/refresh')) {
      return Promise.reject(error);
    }
    original._retry = true;
    refreshPromise = refreshPromise ?? refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
    const next = await refreshPromise;
    if (!next) {
      return Promise.reject(error);
    }
    original.headers.Authorization = `Bearer ${next}`;
    return api.request(original);
  },
);
