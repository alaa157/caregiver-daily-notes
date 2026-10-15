# Caregiver authentication — issue #14

## Backend contract

Source of truth: backend AuthController, LoginRequest, AuthResponse, AuthService,
JwtService and SecurityConfig at commit cff5ecd3945c98cdf4ca3a991513811e38c27c40.

- POST /api/auth/login with JSON { "email": "...", "password": "..." }.
- Success: { "token": "<HS256 JWT>" }, with a UUID subject and numeric expiry.
  Email is trimmed and lowercased; the password is preserved exactly. Validation
  follows the 320-character email and 72-byte UTF-8 password limits, without
  inventing a password length minimum.
- Invalid credentials return 401; validation returns 422. The UI maps statuses
  to fixed translated messages and never renders backend diagnostic text.
- Protected requests use Authorization: Bearer <token>.
- There is no refresh, current-user or logout endpoint. Logout clears this tab's
  session; an issued token remains backend-valid until expiry. No endpoint is
  invented for these operations.

Runtime config may name an HTTPS backend origin or include its /api prefix; the
central client joins that prefix once. Empty apiBaseUrl requires a same-origin
proxy for /api. Cross-origin browser requests require deployment/backend CORS
support. Current backend SecurityConfig does not configure CORS, so use the
same-origin proxy unless the owner provisions cross-origin access.

## Sessions and protected routes

- The JWT lives in memory and sessionStorage, scoped to the configured backend.
  It is never written to localStorage, URLs, logs or config. Blocked storage
  falls back to memory, so a reload then requires login.
- Restoration rejects malformed, expired and differently scoped tokens. JWT
  decoding is only an expiry hint; it does not verify signatures. The backend
  still owns signature verification and all data authorization. Client routes
  control navigation and are not a security boundary.
- sessionStorage is accessible to same-origin JavaScript. Browser behavior can
  copy it into newly opened tabs or retain it during session restore. It provides
  tab-lifetime persistence, not HttpOnly protection. JWT expiry is always checked;
  do not rely solely on tab closure for logout.
- Timers and focus, pageshow and visibility checks clear expired sessions.
  Protected calls also check expiry and centrally handle 401 responses, including
  malformed error bodies. Resource-level 403 errors do not log out the user.
- Responses from previous sessions are discarded. A delayed 401 cannot clear a
  newer login. Leaving the form or signing out cancels pending login work.
- Protected redirects retain local workspace paths, queries and fragments.
  External destinations, controls and traversal outside /app are rejected.
- Requests disable caching/cookies and reject HTTP redirects. Login sends no
  existing bearer token. Password fields clear after failed requests.

## Integration

The existing app/AuthContext and app/ProtectedRoute import paths re-export the
feature and routes/ProtectedRoute. App imports LoginPage; AppShell selects the
dashboard/logout navigation. The main.tsx provider order is unchanged.

Protected features use the existing useApiClient hook under AuthProvider.
That boundary adds the current bearer token and shared rejection handling.
The outer client is used privately for login. Existing API-client and app-file
edits are the minimal bridges required by the Task #13 foundation. All changed
files are confined to web/.

## Verification

From web/: npm test, npm run build, npm run test:e2e.
Install Playwright Chromium first, or set PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
to an installed compatible Chromium.

Component tests cover validation, contract payloads, success/failure, restoration,
storage failures, protected redirects, logout, expiry, central 401/403 handling,
cancellation, stale-response races, Arabic and RTL. Browser tests cover keyboard
navigation, validation/error focus, tab reload, logout/history, expiry, responsive
layout and axe WCAG 2.1 A/AA scans in both languages at desktop, tablet, mobile
and 320px widths.

Frontend tests intercept responses with synthetic JWTs; they do not verify
backend signatures or a live deployment. No live tunnel or test caregiver
credentials were available during implementation. Live login smoke testing
requires the owner's current backend/proxy configuration and an existing
synthetic caregiver account. This change creates no accounts.
