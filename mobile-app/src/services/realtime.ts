import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { resolveWsUrl } from '../config';

export type RealtimeEnvelope = {
  eventId: string;
  eventType: string;
  timestamp: string;
  payload: unknown;
};

function wsUrl(): string {
  return resolveWsUrl();
}

export type RealtimeClient = {
  subscribe: (destination: string, handler: (e: RealtimeEnvelope) => void) => () => void;
  deactivate: () => Promise<void>;
};

export function connectRealtime(accessToken: string): RealtimeClient {
  const client = new Client({
    brokerURL: wsUrl(),
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    forceBinaryWSFrames: true,
    appendMissingNULLonIncoming: true,
  });

  const pending: Array<() => void> = [];
  client.onConnect = () => {
    pending.splice(0).forEach((fn) => fn());
  };
  client.activate();

  return {
    subscribe(destination, handler) {
      let sub: StompSubscription | null = null;
      const start = () => {
        sub = client.subscribe(destination, (msg: IMessage) => {
          try {
            handler(JSON.parse(msg.body) as RealtimeEnvelope);
          } catch {
            // ignore
          }
        });
      };
      if (client.connected) start();
      else pending.push(start);
      return () => sub?.unsubscribe();
    },
    async deactivate() {
      await client.deactivate();
    },
  };
}
