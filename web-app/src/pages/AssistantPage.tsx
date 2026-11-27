import { useEffect, useRef, useState } from 'react'
import { sharedApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { AiBadge } from '@/components/AiBadge'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
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
  const threadRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = threadRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [messages, loading])

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
    <div className="flex min-h-[calc(100dvh-5rem)] flex-col gap-4 md:min-h-[calc(100dvh-4rem)]">
      <PageHeader
        title="AI operations assistant"
        description="Answers use secured backend tools scoped to your role. Read-only — cannot move money or approve advances."
        actions={<AiBadge label="AI assistant" />}
      />

      {error ? <div className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{error}</div> : null}

      <div className="flex flex-wrap gap-2">
        {prompts.map((p) => (
          <Button
            key={p}
            variant="secondary"
            size="sm"
            className="max-w-full whitespace-normal text-left"
            disabled={loading}
            onClick={() => void send(p)}
          >
            {p}
          </Button>
        ))}
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-line bg-sand/60 shadow-sm">
        <div ref={threadRef} className="min-h-[280px] flex-1 space-y-3 overflow-y-auto p-4 md:p-5">
          {messages.length === 0 && !loading ? (
            <div className="flex h-full min-h-[220px] items-center justify-center">
              <EmptyState
                title="Ask a question"
                message="Use a suggested prompt or type below. Your question and the assistant reply appear in this conversation pane."
              />
            </div>
          ) : (
            <>
              {messages.map((m, i) => (
                <div
                  key={i}
                  className={cn(
                    'max-w-[92%] rounded-xl border px-4 py-3 shadow-sm',
                    m.role === 'user'
                      ? 'ml-auto border-forest/15 bg-mint/50'
                      : 'mr-auto border-info/20 bg-white',
                  )}
                >
                  <div className="mb-1 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">
                    {m.role === 'user' ? 'You' : 'Assistant'}
                    {m.role === 'assistant' ? <AiBadge /> : null}
                  </div>
                  <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink">{m.content}</p>
                </div>
              ))}
              {loading ? (
                <div className="mr-auto max-w-[92%] rounded-xl border border-info/20 bg-white px-4 py-3 shadow-sm">
                  <div className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">Assistant</div>
                  <p className="text-sm italic text-ink-muted">Thinking…</p>
                </div>
              ) : null}
            </>
          )}
        </div>

        <div className="shrink-0 space-y-3 border-t border-line bg-white/90 p-4 backdrop-blur">
          {disclaimer ? <p className="text-xs text-ink-faint">{disclaimer}</p> : null}
          <Textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask about risk, finance, or platform operations…"
            className="min-h-24"
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                void send(question)
              }
            }}
          />
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-ink-faint">Enter to send · Shift+Enter for a new line</p>
            <Button disabled={loading || !question.trim()} onClick={() => void send(question)}>
              {loading ? 'Thinking…' : 'Send'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
