# Web Feature and Data Parity Implementation Roadmap

**Repository:** `alaa157/caregiver-daily-notes`  
**Target documentation path:** `docs/product-alignment/web-parity-roadmap/README.md`  
**Planning baseline:** Phase 1, Phase 2, and Phase 3A documentation retrieved from `main` on 2026-11-03  
**Document status:** Proposed implementation roadmap — product-owner approval required for unresolved decisions  
**Scope:** Web application parity and data/business-rule alignment only; no implementation authorization

---

## 1\. Executive Summary

This roadmap defines a dependency-aware sequence for bringing the React/Spring Boot/PostgreSQL Web application closer to the caregiver capabilities already available in the Kotlin/Jetpack Compose Android application.

The objective is **capability and semantic parity**, not identical screens, databases, or infrastructure:

- Android remains a standalone, offline-first application using Room as its primary local store.  
- Android BYOK provider credentials, prompts, consent, and configuration remain on-device and are never sent to the Web backend.  
- Web remains an authenticated React/TypeScript application backed by Spring Boot and PostgreSQL, with backend-managed AI.  
- Both platforms should represent the same care concepts consistently: recipients, daily notes, observations, unknown and missing values, addenda, plans, medication events, dates, safety evidence, and history.  
- Existing working Web capabilities must be preserved and improved, not replaced merely to resemble Android.  
- Historical records, corrections, uncertainty, and provenance must remain traceable.

### Current documented parity position

Phase 1 records **33 feature-matrix rows**:

| Documented status | Rows |
| :---- | ----: |
| MATCH | 1 |
| PARTIAL | 17 |
| DIFFERENT IMPLEMENTATION | 4 |
| MISSING ON WEB | 10 |
| MISSING ON ANDROID | 1 |
| PLACEHOLDER | 0 |
| UNVERIFIED as primary matrix status | 0 |
| **Total** | **33** |

The 33-row count and classifications are reproduced in Section 4\. They describe feature rows, not a percentage of completed work or a runtime test result.

The highest-risk gap is not a missing screen. It is the difference between the two platforms' data semantics and business rules: observation nullability, legacy defaults, recipient lifecycle, note/date uniqueness, addenda, deterministic safety rules, care-plan representation, and medication tracking. The Web already has substantial recipient, note, history, plan, summary, and authenticated-account functionality. Those areas should be aligned and strengthened rather than recreated.

### Recommended strategy

The recommended sequence is:

1. Resolve decisions that affect data meaning and define a Web parity contract.  
2. Align recipient, note, observation, addendum, date, and lifecycle semantics with safe database migrations.  
3. Complete the everyday caregiver workflow using the existing Web application.  
4. Align deterministic safety evaluation, summary evidence, and AI failure behavior while keeping provider architectures separate.  
5. Align care-plan structures and version history.  
6. Add medication management only after schedule and intake semantics are approved.  
7. Add Web CSV import/export as a separate portability capability.  
8. Design and implement independent Web backup/restore.  
9. Add useful Web reports, PDF output, printing, and sharing.  
10. Complete settings, localization, accessibility, and responsive UX.  
11. Run a cross-platform parity and release-readiness gate.

Some presentation and settings work can proceed in parallel with data-contract work, but no feature that depends on unresolved clinical or data semantics should be released ahead of those decisions.

**This roadmap does not implement or design the Android-to-Web transfer protocol.** It deliberately stops at Web parity and readiness. Transfer sessions, device linking, manifests, chunks, receipts, synchronization, and transfer-specific authentication remain out of scope.

---

(Full content continues in the original Google Doc — truncated here for the tool call length limit. The complete 100k+ character document is available locally.)