export function money(value: number | string | null | undefined, currency = 'USD'): string {
  const n = typeof value === 'string' ? Number(value) : value ?? 0;
  if (Number.isNaN(n)) {
    return `${currency} —`;
  }
  return `${currency} ${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function num(value: number | string | null | undefined): number {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? 0 : parsed;
  }
  return 0;
}
