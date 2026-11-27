import { Platform } from 'react-native';
import Constants from 'expo-constants';

/**
 * Spring Boot API base URL (configurable).
 *
 * Priority:
 * 1. EXPO_PUBLIC_API_URL (recommended for Docker Compose / device testing)
 * 2. app.json → expo.extra.apiUrl
 * 3. Platform defaults (Android emulator → 10.0.2.2:18080, else localhost:18080)
 *
 * Examples:
 *   EXPO_PUBLIC_API_URL=http://localhost:18080/api/v1
 *   EXPO_PUBLIC_API_URL=http://10.0.2.2:18080/api/v1
 *   EXPO_PUBLIC_API_URL=http://192.168.1.20:18080/api/v1
 */
function resolveApiUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (fromEnv) {
    return fromEnv.replace(/\/$/, '');
  }
  const fromExtra = Constants.expoConfig?.extra?.apiUrl as string | undefined;
  if (fromExtra?.trim()) {
    return fromExtra.replace(/\/$/, '');
  }
  // Default to Compose-published backend port (18080).
  if (Platform.OS === 'android') {
    return 'http://10.0.2.2:18080/api/v1';
  }
  return 'http://localhost:18080/api/v1';
}

export const API_BASE_URL = resolveApiUrl();

/** STOMP endpoint derived from API_BASE_URL (…/api/v1 → …/ws). */
export function resolveWsUrl(): string {
  const base = API_BASE_URL.replace(/\/api\/v1\/?$/, '');
  if (base.startsWith('https://')) return `${base.replace('https://', 'wss://')}/ws`;
  if (base.startsWith('http://')) return `${base.replace('http://', 'ws://')}/ws`;
  return 'ws://localhost:18080/ws';
}

export const DEMO_HINT = {
  agent: { username: 'agent1', password: 'Demo@2026!' },
  manager: { username: 'manager', password: 'Demo@2026!' },
};

/** Harare CBD fallback used only when the user denies location permission (simulated demo coords). */
export const DEMO_COORDINATES = {
  latitude: -17.8292,
  longitude: 31.0522,
};
