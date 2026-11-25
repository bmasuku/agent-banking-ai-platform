import { Inbox } from 'lucide-react'

export function EmptyState({
  title = 'Nothing here yet',
  message,
}: {
  title?: string
  message: string
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-line bg-white/70 px-6 py-14 text-center shadow-sm shadow-forest/5">
      <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-mint text-forest">
        <Inbox className="h-5 w-5" aria-hidden />
      </div>
      <h3 className="font-display text-lg font-semibold text-ink">{title}</h3>
      <p className="mt-1 max-w-md text-sm leading-relaxed text-ink-muted">{message}</p>
    </div>
  )
}
