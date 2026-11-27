import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { connectRealtime, type RealtimeClient } from '../services/realtime';
import { useAuthStore } from '../store/authStore';

export function useRealtime() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();
  const ref = useRef<RealtimeClient | null>(null);

  useEffect(() => {
    if (!accessToken || !user) return;

    const client = connectRealtime(accessToken);
    ref.current = client;
    const topics: string[] = ['/user/queue/notifications'];
    if (user.role === 'AGENT' && user.agentId) topics.push(`/topic/agents/${user.agentId}`);
    if (user.role === 'AREA_MANAGER' && user.areaId) topics.push(`/topic/areas/${user.areaId}`);

    const unsubs = topics.map((t) =>
      client.subscribe(t, (event) => {
        const type = event.eventType;
        if (type.includes('TRANSACTION') || type.includes('BALANCE') || type.includes('COMMISSION')) {
          void qc.invalidateQueries({ queryKey: ['agent'] });
        }
        if (type.includes('EXCHANGE') || type.includes('REPLENISH') || type.includes('LOW_LIQUIDITY')) {
          void qc.invalidateQueries({ queryKey: ['agent'] });
          void qc.invalidateQueries({ queryKey: ['area'] });
        }
        if (type.includes('ADVANCE')) {
          void qc.invalidateQueries({ queryKey: ['agent'] });
        }
        void qc.invalidateQueries({ queryKey: ['notifications'] });
      }),
    );

    return () => {
      unsubs.forEach((u) => u());
      void client.deactivate();
      ref.current = null;
    };
  }, [accessToken, user, qc]);
}
