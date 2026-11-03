## 3. Source Documents and Planning Baseline

The following committed documents are the primary source of truth. This roadmap extends them and does not repeat their audits.

| Source | Purpose in this roadmap | Reference |
| :---- | :---- | :---- |
| Phase 1 — Comprehensive Product Discovery and Cross-Platform Alignment | Android product inventory, Web specification, data models, 33-row parity matrix, business logic/AI comparison, localization, import/export/backup readiness | `docs/product-alignment/phase-1/README.md` |
| Phase 2 — Canonical Data Model, Shared API Contracts & Cross-Platform Architecture Design | Proposed canonical semantics, field-level models, decision register PO-01–PO-23, architecture constraints, migration and security principles | `docs/product-alignment/phase-2/README.md` |
| Phase 3A — Cross-Platform Implementation Readiness Audit | Current readiness and gap inventory, Web readiness, data-model risks, implementation sequencing, testing requirements | `docs/product-alignment/phase-3a/README.md` |

### 3.1 Source snapshots recorded by the documents

| Document | Snapshot information recorded |
| :---- | :---- |
| Phase 1 | Android `feat/standalone-byok-ai` at `21876285ad808a6518593ea3974c51b6b1f6bb31`; Web/backend `main` at `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7` |
| Phase 2 | Android source at `21876285ad808a6518593ea3974c51b6b1f6bb31`; Web/backend application source at `14dfff2b7a7049d4ac6285fe340b29c59ba5b595`; Phase 2 documentation commit/current main before its revision recorded as `25c031b2bda50a5daa9cda9cb62f2f4cd306a579` |
| Phase 3A | Android branch HEAD `21876285ad808a6518593ea3974c51b6b1f6bb31`; `main` HEAD `bc9a673e48e0f4955b9435a411e85f84ac39c399` |

These are historical references recorded in the source documents, not a claim that the application code was re-audited at those commits for this roadmap.

### 3.2 Evidence and status vocabulary

This roadmap distinguishes:

- **Documented existing capability**
- **Documented partial parity**
- **Documented Web gap**
- **Proposed improvement**
- **Decision required**
- **External review required**
- **Runtime/operational unknown**

---

## 4. Current Parity Baseline

### 4.1 Exact 33-row matrix

| # | Feature | Phase 1 status |
| ----: | :---- | :---- |
| 1 | Home / overview | PARTIAL |
| 2 | Main navigation | PARTIAL |
| 3 | Onboarding | MISSING ON WEB |
| 4 | Recipient creation | PARTIAL |
| 5 | Recipient edit | PARTIAL |
| 6 | Recipient lifecycle | DIFFERENT IMPLEMENTATION |
| 7 | Recipient relationship data | MISSING ON ANDROID |
| 8 | Daily note creation | PARTIAL |
| 9 | Note detail | PARTIAL |
| 10 | Addendum/correction | PARTIAL |
| 11 | History search/filter | PARTIAL |
| 12 | Care trends | PARTIAL |
| 13 | Summary window selection | PARTIAL |
| 14 | Summary result/evidence | PARTIAL |
| 15 | Deterministic safety logic | DIFFERENT IMPLEMENTATION |
| 16 | AI provider configuration | DIFFERENT IMPLEMENTATION |
| 17 | AI consent and prompt customization | MISSING ON WEB |
| 18 | Plan generation | PARTIAL |
| 19 | Plan lifecycle | PARTIAL |
| 20 | Plan edit and version history | PARTIAL |
| 21 | Medication tracker | MISSING ON WEB |
| 22 | Language choice | MATCH |
| 23 | RTL / mixed text direction | PARTIAL |
| 24 | Theme modes and palettes | MISSING ON WEB |
| 25 | Settings hub | PARTIAL |
| 26 | CSV import/export | MISSING ON WEB |
| 27 | Full backup/restore | MISSING ON WEB |
| 28 | PDF summary/share | MISSING ON WEB |
| 29 | About / app updater | MISSING ON WEB |
| 30 | Backend URL / connection selection | DIFFERENT IMPLEMENTATION |
| 31 | Authentication / access | PARTIAL |
| 32 | Sample/demo data | MISSING ON WEB |
| 33 | Optional offline outbox/transfer | MISSING ON WEB |

**Count check:** 1 MATCH + 17 PARTIAL + 4 DIFFERENT IMPLEMENTATION + 10 MISSING ON WEB + 1 MISSING ON ANDROID = 33.

### 4.2 Important documented Web foundations to preserve

The Web application already includes authenticated accounts, recipient CRUD, daily notes, addenda, summaries, care-plan generation, typed React API modules, Spring Boot services, PostgreSQL + Flyway, and backend-managed AI.

### 4.3 Main documented semantic gaps

- Recipient lifecycle (archive vs deactivate)
- Observation nullability and verification
- Note/date uniqueness
- Addenda semantics (text events vs structured corrections)
- Safety rule differences
- Care-plan structure (free-text vs typed actions)
- Missing medication domain on Web
- Missing CSV, full backup/restore, PDF, themes on Web
