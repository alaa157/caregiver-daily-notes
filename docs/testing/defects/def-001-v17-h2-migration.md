# DEF-001 — V17 migration incompatible with H2 (test-layer blocker)

- **Severity:** Blocker (test layer only; production PostgreSQL unaffected)
- **Component:** `backend/src/main/resources/db/migration/V17__add_medication_intake_event_void.sql`
- **Environment:** OpenJDK 21.0.12, H2 2.2.224 (`MODE=PostgreSQL`), Maven Surefire
- **Discovered:** 2026-11-19, full backend suite run during the test-estate audit

## Repro

```bash
mvn -f backend/pom.xml test
```

Every `@SpringBootTest` class fails to load its ApplicationContext: **325 errors** across ~30 classes, all cascading from one bean failure:

```
BeanCreationException: Error creating bean 'flywayInitializer':
Migration V17__add_medication_intake_event_void.sql failed
SQL State: 42000 — Syntax error in SQL statement
"ALTER TABLE medication_intake_events ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE[*], ..."
```

Isolation (H2 `RunScript`, both 2.2.224 and 2.3.232):

| Statement | Result |
|---|---|
| `ALTER TABLE t ADD COLUMN a TIMESTAMP WITH TIME ZONE` | passes |
| `ALTER TABLE t ADD COLUMN a INT, ADD COLUMN b VARCHAR(10)` | **syntax error** |

Root cause: H2 does not accept the PostgreSQL repeated-`ADD COLUMN` form, regardless of type or version. The type was a red herring.

## Fix

Commit `610e21ab`: one `ALTER TABLE … ADD COLUMN` per column — semantically identical, portable across PostgreSQL and H2. FK and CHECK constraints unchanged.

## Verification

- H2 `RunScript` with the full edited V17 (incl. FK + CHECK): passes.
- Full suite: errors **325 → 0**; final state 700/700 green.
- Existing databases: V17's checksum changes, so each database where V17 already ran needs a one-time `flyway repair`. Applied to the project's local PostgreSQL (history realigned, `current=17, pending=0`); the app then boots with schema validation passing. Any other environment (e.g. a hosted deployment) must run the same repair before its next startup:

```bash
mvn -f backend/pom.xml org.flywaydb:flyway-maven-plugin:10.10.0:repair \
    -Dflyway.url=<jdbc-url> -Dflyway.user=<user> -Dflyway.password=<password>
```

**Status: fixed.**
