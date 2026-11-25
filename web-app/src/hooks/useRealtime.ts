import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { connectRealtime, type RealtimeHandle } from '@/lib/realtime'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/api'

function topicsFor(role: Role | undefined, agentId?: string | null, areaId?: string | null): string[] {
  const topics = ['/user/queue/notifications']
  if (!role) return topics
  if (role === 'RISK_OFFICER' || role === 'ADMIN') topics.push('/topic/risk')
  if (role === 'FINANCE_OFFICER' || role === 'ADMIN') topics.push('/topic/finance')
  if (role === 'AREA_MANAGER' && areaId) topics.push(`/topic/areas/${areaId}`)
  if (role === 'AGENT' && agentId) topics.push(`/topic/agents/${agentId}`)
  if (role === 'ADMIN' || role === 'RISK_OFFICER' || role === 'FINANCE_OFFICER') {
    // staff also listen to finance/risk already; area topics subscribed per area when needed
  }
  return topics
}

export function useRealtime() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)
  const qc = useQueryClient()
  const handleRef = useRef<RealtimeHandle | null>(null)

  useEffect(() => {
    if (!accessToken || !user) return

    const handle = connectRealtime(accessToken)
    handleRef.current = handle
    const topics = topicsFor(user.role, user.agentId, user.areaId)

    const unsubs = topics.map((topic) =>
      handle.subscribe(topic, (event) => {
        const type = event.eventType
        if (type.includes('FRAUD') || type.includes('INVESTIGATION') || type.includes('ALERT')) {
          toast.message(`Risk: ${type.replace(/_/g, ' ').toLowerCase()}`)
          void qc.invalidateQueries({ queryKey: ['risk'] })
        }
        if (type.includes('ADVANCE') || type.includes('COMMISSION') || type.includes('POOL')) {
          toast.message(`Finance: ${type.replace(/_/g, ' ').toLowerCase()}`)
          void qc.invalidateQueries({ queryKey: ['finance'] })
        }
        if (type.includes('REPLENISH') || type.includes('LOW_LIQUIDITY') || type.includes('BALANCE')) {
          toast.message(`Ops: ${type.replace(/_/g, ' ').toLowerCase()}`)
          void qc.invalidateQueries({ queryKey: ['area'] })
          void qc.invalidateQueries({ queryKey: ['agent'] })
        }
        if (type.includes('EXCHANGE') || type.includes('TRANSACTION')) {
          void qc.invalidateQueries({ queryKey: ['agent'] })
          void qc.invalidateQueries({ queryKey: ['area'] })
          void qc.invalidateQueries({ queryKey: ['finance'] })
        }
        void qc.invalidateQueries({ queryKey: ['notifications'] })
      }),
    )

    return () => {
      unsubs.forEach((u) => u())
      void handle.deactivate()
      handleRef.current = null
    }
  }, [accessToken, user, qc])
}
