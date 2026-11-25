import { errorMessage } from '@/api/client'

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null
  return (
    <div className="mb-4 rounded-lg border border-danger/20 bg-danger-soft px-4 py-3 text-sm text-danger">
      {errorMessage(error)}
    </div>
  )
}
