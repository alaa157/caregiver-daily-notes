name: Feature request
description: Suggest an improvement for Rafeek
labels: ["enhancement"]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem
      description: What caregiver or developer need is unmet today?
    validations:
      required: true
  - type: textarea
    id: proposal
    attributes:
      label: Proposed solution
    validations:
      required: true
  - type: dropdown
    id: component
    attributes:
      label: Affected component
      options:
        - Web app
        - Android app
        - Backend / API
        - AI features (BYOK)
        - Docs / CI
    validations:
      required: true
  - type: textarea
    id: alternatives
    attributes:
      label: Alternatives considered
  - type: markdown
    attributes:
      value: |
        Safety note: AI features must stay evidence-grounded and BYOK (no server-side keys). Proposals that send care data to a shared key or bypass the medication hard-lock will be declined.
