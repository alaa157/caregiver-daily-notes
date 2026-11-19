name: Bug report
description: Something behaves incorrectly in Rafeek (web, Android, or backend)
labels: ["bug"]
body:
  - type: markdown
    attributes:
      value: |
        Thanks for reporting. Defects are triaged into the [defect register](https://github.com/alaa157/caregiver-daily-notes/blob/main/docs/testing/defects/README.md) with evidence, repro, fix, and verification. Never include real care-recipient data or API keys in reports.
  - type: dropdown
    id: component
    attributes:
      label: Component
      options:
        - Web app
        - Android app
        - Backend / API
        - AI features (BYOK)
        - Docs / CI
    validations:
      required: true
  - type: textarea
    id: what-happened
    attributes:
      label: What happened
      description: Actual behavior, including any error message or status code.
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
    validations:
      required: true
  - type: textarea
    id: repro
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. Sign in as …
        2. Open …
        3. Tap/click …
    validations:
      required: true
  - type: textarea
    id: environment
    attributes:
      label: Environment
      description: Client and version (APK release tag or web build), browser/OS or Android version, language (EN/AR).
    validations:
      required: true
  - type: textarea
    id: evidence
    attributes:
      label: Evidence
      description: Screenshots, screen recordings, or log excerpts (redact keys and personal data).
