export const SESSION_STORAGE_KEY = 'caregiver-auth-session-v1';

export interface Session {
  token: string;
  expiresAt: number;
}

export type SessionReason = 'expired' | 'signedOut' | null;
export interface SessionState { session: Session | null; reason: SessionReason }

function decodePart(part: string): unknown {
  const normalized = part.replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')));
}

/** Decode only for expiry UX. JWT signatures and authorization remain backend-owned. */
export function parseSession(token: unknown, now = Date.now()): Session {
  try {
    if (typeof token !== 'string' || token.length > 8192 || !/^[\w-]+\.[\w-]+\.[\w-]+$/.test(token)) throw new Error();
    const [headerPart, payloadPart] = token.split('.');
    const header = decodePart(headerPart);
    const payload = decodePart(payloadPart);
    if (typeof header !== 'object' || header === null || !('alg' in header) || header.alg !== 'HS256') throw new Error();
    if (typeof payload !== 'object' || payload === null || !('sub' in payload) || !('exp' in payload)) throw new Error();
    if (typeof payload.sub !== 'string' || !/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(payload.sub)) throw new Error();
    if (typeof payload.exp !== 'number' || !Number.isSafeInteger(payload.exp * 1000) || payload.exp * 1000 <= now) throw new Error();
    return { token, expiresAt: payload.exp * 1000 };
  } catch {
    // Never include tokens or decoded claims in errors.
    throw new Error('Invalid authentication response.');
  }
}

export function clearStoredSession(): void {
  try { sessionStorage.removeItem(SESSION_STORAGE_KEY); } catch { /* Storage can be blocked. */ }
}

export function storeSession(session: Session, baseUrl: string): void {
  try { sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: session.token, baseUrl })); }
  catch { clearStoredSession(); /* Keep in memory; never fall back to localStorage. */ }
}

export function restoreSession(baseUrl: string): SessionState {
  let raw: string | null;
  try { raw = sessionStorage.getItem(SESSION_STORAGE_KEY); } catch { return { session: null, reason: null }; }
  if (!raw) return { session: null, reason: null };
  try {
    const value: unknown = JSON.parse(raw);
    if (typeof value !== 'object' || value === null || !('baseUrl' in value) || value.baseUrl !== baseUrl || !('token' in value)) throw new Error();
    return { session: parseSession(value.token), reason: null };
  } catch {
    return { session: null, reason: 'expired' };
  }
}
