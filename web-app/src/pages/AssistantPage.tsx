import { useState } from 'react'
import { sharedApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { AiBadge } from '@/components/AiBadge'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { useAuthStore } from '@/store/authStore'

type Msg = { role: 'user' | 'assistant'; content: string }

const PROMPTS: Record<string, string[]> = {
  RISK_OFFICER: [
    'Which agents have the highest fraud risk right now?',
    'Summarise open investigations by severity',
    'Which flagged transactions need review first?',
  ],
  FINANCE_OFFICER: [
    'Which agents are eligible for commission disbursement?',
    'What is outstanding advance interest income?',
    'Which advances need manual credit review?',
  ],
  ADMIN: [
    'What is the AI service status?',
    'Summarise recent simulation runs',
    'Which configuration settings affect fraud thresholds?',
  ],
}

export function AssistantPage() {
  const role = useAuthStore((s) => s.user?.role) ?? 'ADMIN'
  const prompts = PROMPTS[role] ?? PROMPTS.ADMIN
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<Msg[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [disclaimer, setDisclaimer] = useState<string | null>(null)

  const send = async (text: string) => {
    const q = text.trim()
    if (!q || loading) return
    setError(null)
    setLoading(true)
    setQuestion('')
    const next: Msg[] = [...messages, { role: 'user', content: q }]
    setMessages(next)
    try {
      const res = await sharedApi.assistantChat(
        q,
        next.map((m) => ({ role: m.role, content: m.content })),
      )
      setMessages([...next, { role: 'assistant', content: res.answer }])
      setDisclaimer(res.disclaimer ?? null)
    } catch (e) {
      setError(errorMessage(e))
      setMessages(messages)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="AI operations assistant"
        description="Answers use secured backend tools scoped to your role. Read-only — cannot move money or approve advances."
        actions={<AiBadge label="AI assistant" />}
      />
      {error ? <div className="mb-4 rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{error}</div> : null}
      <div className="mb-4 flex flex-wrap gap-2">
        {prompts.map((p) => (
          <Button key={p} variant="secondary" size="sm" disabled={loading} onClick={() => void send(p)}>
            {p}
          </Button>
        ))}
      </div>
      <div className="mb-4 space-y-3">
        {messages.length === 0 ? (
          <EmptyState
            title="Start a conversation"
            message="Ask a question or use a suggested prompt. Replies are AI-assisted and grounded in live API tools for your role."
          />
        ) : (
          messages.map((m, i) => (
            <Card key={i} className={m.role === 'user' ? 'bg-mint/40' : 'border-info/20'}>
              <CardContent className="space-y-1 pt-5">
                <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">
                  {m.role === 'user' ? 'You' : 'Assistant'}
                  {m.role === 'assistant' ? <AiBadge /> : null}
                </div>
                <p className="whitespace-pre-wrap text-sm text-ink">{m.content}</p>
              </CardContent>
            </Card>
          ))
        )}
      </div>
      {disclaimer ? <p className="mb-3 text-xs text-ink-faint">{disclaimer}</p> : null}
      <div className="space-y-3">
        <Textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask about risk, finance, or platform operations…"
        />
        <Button disabled={loading} onClick={() => void send(question)}>
          {loading ? 'Thinking…' : 'Send'}
        </Button>
      </div>
    </div>
  )
}
