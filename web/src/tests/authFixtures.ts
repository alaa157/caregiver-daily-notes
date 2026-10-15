/** Synthetic, unverified JWTs for frontend contract tests only; never demo accounts. */
export function testToken(expiresInSeconds = 3600, suffix = 'test-signature'): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + expiresInSeconds }), suffix].join('.');
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}
