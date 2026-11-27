import { useRealtime } from './hooks/useRealtime';

/** Mount inside QueryClientProvider to keep agent/area queries fresh from STOMP events. */
export function RealtimeBridge() {
  useRealtime();
  return null;
}
