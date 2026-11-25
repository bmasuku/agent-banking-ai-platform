import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { API_BASE_URL } from '@/config'

export type RealtimeEnvelope = {
  eventId: string
  eventType: string
  timestamp: string
  payload: unknown
}

function wsBase(): string {
  const api = API_BASE_URL
  if (api.startsWith('http')) {
    const u = new URL(api)
    const wsProto = u.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProto}//${u.host}/ws`
  }
  const loc = window.location
  const wsProto = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProto}//${loc.host}/ws`
}

export type RealtimeHandle = {
  subscribe: (destination: string, onEvent: (e: RealtimeEnvelope) => void) => () => void
  deactivate: () => Promise<void>
}

export function connectRealtime(accessToken: string): RealtimeHandle {
  const client = new Client({
    brokerURL: wsBase(),
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  })

  const pending: Array<() => void> = []
  client.onConnect = () => {
    pending.splice(0).forEach((fn) => fn())
  }
  client.activate()

  return {
    subscribe(destination, onEvent) {
      let sub: StompSubscription | null = null
      const start = () => {
        sub = client.subscribe(destination, (msg: IMessage) => {
          try {
            onEvent(JSON.parse(msg.body) as RealtimeEnvelope)
          } catch {
            // ignore malformed
          }
        })
      }
      if (client.connected) start()
      else pending.push(start)
      return () => {
        sub?.unsubscribe()
      }
    },
    async deactivate() {
      await client.deactivate()
    },
  }
}
