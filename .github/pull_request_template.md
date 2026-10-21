## What changed

<!-- Which areas did you touch, and which issue does this close? -->

Fixes #

## Why

<!-- The problem, in a sentence or two. Not the solution. -->

## How it was verified

<!-- The exact commands you ran and what they printed. -->

- [ ] `mvn -f backend/pom.xml test`
- [ ] `cd android && ./gradlew :app:testDebugUnitTest`
- [ ] `cd android && ./gradlew :app:connectedDebugAndroidTest` (device required)
- [ ] Manual check on a device or emulator (screenshots below)

## Screenshots / recordings

<!-- For UI changes. Before/after, or a short clip. -->

## Checklist

- [ ] One concern only — no unrelated refactors
- [ ] No secrets, keys or real patient data in the diff
- [ ] New behaviour covered by a test
- [ ] User-facing strings added to `values/strings.xml` **and** `values-ar/strings.xml`
- [ ] A reviewer other than the author has approved
