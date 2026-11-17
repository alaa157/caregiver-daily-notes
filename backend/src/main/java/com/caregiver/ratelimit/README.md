# Rate limiting (#841)

A dependency-free, in-memory token bucket protects the two exposed surfaces:

- `POST /api/auth/login` and `POST /api/auth/register`: limited per source IP
  and per normalized account email (independent buckets).
- `POST /api/summaries/byok`, `POST /api/plans/generate/byok` and
  `POST /api/ai/byok/connection-test`: limited per verified caregiver id,
  falling back to the source IP when no verified principal exists.

The filter runs inside the Spring Security chain, after the JWT filter, so the
caregiver id is available for AI keys. It is registered only in that chain (a
disabled `FilterRegistrationBean` prevents double registration). A denial is
HTTP `429` with `Retry-After` (whole seconds, at least 1) and the stable JSON
envelope `{"code":"RATE_LIMITED","message":"..."}`. Nothing else is filtered,
so normal usage is unaffected.

Limits live under `rate-limit.*` in `application.yml` and can be tuned without a
rebuild:

| Property | Default | Meaning |
| --- | --- | --- |
| `rate-limit.enabled` | `true` | Master switch |
| `rate-limit.auth-ip-max-requests` / `auth-ip-window` | `200` / `1m` | Per-IP auth ceiling |
| `rate-limit.auth-account-max-requests` / `auth-account-window` | `50` / `5m` | Per-email auth ceiling |
| `rate-limit.ai-max-requests` / `ai-window` | `120` / `1m` | Per-caregiver AI ceiling |

Buckets refill continuously at `capacity / window`. Keys are bounded to keep
memory usage predictable. This is intentionally single-instance; the
`TokenBucketLimiter` can be replaced with a Redis-backed implementation later
without changing the filter. Account lockout, CAPTCHA, WAF and gateway changes
are out of scope.
