/** Accept only local workspace destinations, including their query and fragment. */
export function safeReturnPath(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return '/app';
  try {
    const url = new URL(value, 'https://caregiver.invalid');
    if (url.origin === 'https://caregiver.invalid' && (url.pathname === '/app' || url.pathname.startsWith('/app/'))) return url.pathname + url.search + url.hash;
  } catch { /* Ignore untrusted navigation state. */ }
  return '/app';
}
