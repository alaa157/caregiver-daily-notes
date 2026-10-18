# Tasks 1–3: real-backend browser smoke checks

This opt-in suite complements the 101 unit/component tests and the two contract
browser suites. It sends real HTTP requests to the project's running Spring
Boot backend. No success/error responses or JWTs are fabricated.

Use a **disposable local database containing synthetic data only**. Each of the
four cases creates two unique test caregivers, a recipient and a historical
note. Successful cases deactivate their recipient. Accounts and historical
records remain until you discard the test database; no delete endpoint exists.
The configuration accepts loopback backend origins only.

## Run

Start the current backend with Java 21 and Maven in a separate terminal. For
an isolated in-memory H2 database, use its existing test dependency and Flyway
migrations, without modifying backend source or shared infrastructure:

```sh
JWT_SECRET=disposable-local-smoke-secret-at-least-32-bytes \
SPRING_DATASOURCE_URL='jdbc:h2:mem:websmoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATASOURCE_USERNAME=sa \
SPRING_DATASOURCE_PASSWORD='' \
SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT=false \
mvn -f backend/pom.xml test-compile spring-boot:run \
  -Dspring-boot.run.useTestClasspath=true
```

The displayed signing value is a public, disposable-test-only value. Use an
environment-provided private signing secret for shared/deployed environments.
H2 verifies integration with the real application/security chain but does not
substitute for testing the owner's PostgreSQL deployment.

From `web/`, after installing dependencies and Playwright Chromium:

```sh
npm run build
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/integration/playwright.config.ts
```

An existing browser can be selected with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. The backend URL is a test-process setting;
the production app still reads its single URL from runtime `config.json`.

## Coverage and limits

English/Arabic cases run at desktop and 320px widths. They exercise protected
deep links, a real invalid-password response, valid login, tab restoration,
list/create/details/edit, cancellation and confirmed deactivation, read-only
inactive records, logout and protected access after logout. Direct API checks
verify that a second caregiver cannot list/read/update/deactivate the owner's
recipient, inactive updates return 409, unauthenticated requests return 401,
and an existing historical note remains unchanged after deactivation.

The browser's `/api` requests are forwarded to the real backend using
Playwright's transport bridge. This models the same-origin proxy documented by
the app; it does not verify deployed proxy routing or enable backend CORS.
Runtime-config rotation, session expiry, API failures, conflict recovery,
accessibility and all four responsive widths retain their separate existing
unit/component and browser coverage.

Traces, screenshots and video are disabled for this suite so saved artifacts
do not capture authentication traffic. Test assertions avoid printing tokens.
The suite does not call an LLM or modify backend/mobile/workflow files.
