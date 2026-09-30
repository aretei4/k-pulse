const dateFormatter = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const shortFormatter = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return dateFormatter.format(new Date(iso));
}

export function formatShortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return shortFormatter.format(new Date(iso));
}

export function percent(part: number, total: number): string {
  if (!total) return '0%';
  return `${Math.round((part / total) * 100)}%`;
}

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
