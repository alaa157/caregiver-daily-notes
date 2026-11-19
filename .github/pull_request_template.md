## Summary

<!-- What does this PR change, and why? Link the issue(s): Closes #NNN -->

## Changes

<!-- Bullet the meaningful changes; note any contract, migration, or UI-string changes explicitly. -->

## Testing

- [ ] Backend: `mvn -f backend/pom.xml test` green (or unaffected)
- [ ] Web: `npx vitest run` green (or unaffected)
- [ ] Android JVM tests green (or unaffected)
- [ ] UI changes: screenshots attached (EN and AR where strings changed)
- [ ] Migrations: portable across PostgreSQL and H2; checksum impact noted (existing DBs may need `flyway repair`)

## Docs

- [ ] README / `docs/` updated where behavior or contracts changed
- [ ] No credentials, keys, or real care-recipient data in code, tests, or screenshots
