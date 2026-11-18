# AGENTS.md

This file is the durable contract for anyone — human or agent — picking up an issue in this repository. Read it before changing code. `CONTRIBUTING.md` covers general project conventions; this file covers how issues are written, scoped, implemented, and verified.

## 1. Source of truth

- GitHub is the source of truth. Do not rely on local worktrees, chat history, or `.perch/` state as the only copy of a decision.
- `.perch/` is local tooling state, not a repo artifact. `.perch/rules/` and `.perch/PERCH.md` are not committed and are not durable for anyone who clones the repo or starts a new agent session.
- The current Android model-catalog work lives on `feat/standalone-byok-ai`, not `main`. Base model-catalog issues on that branch until it is merged.
- Do not create or keep long-lived local copies of the repo for issue work.

## 2. Maximum independence

- No issue may depend on another issue. Do not write "see issue X" or "wait for PR Y".
- Each issue must be implementable by an agent that has only the issue body and the repository.
- If a change genuinely requires two layers, the issue must own both layers and list both in "Files in scope".
- Six agents may work on separate machines at the same time. Assume they cannot talk to each other.

## 3. Fully self-contained briefs

Every issue must contain:

- **Snapshot:** repo, base branch, area, scope, priority.
- **Why:** the user-visible problem and the root cause.
- **Exact files and lines:** path plus line numbers on the base branch.
- **Required change:** concrete behavior, not a vague goal.
- **Acceptance criteria:** checkbox list that can be verified without asking a question.
- **Out of scope:** what the agent must not touch.
- **Hard rules:** the no-test rule below.
- **Branch and PR:** exact branch name, PR title, PR base, `Closes #<issue-number>`.
- **Verification:** commands for the reviewer only.

The agent cannot ask clarifying questions. If the issue is missing a fact, the issue is wrong; fix the issue, not the agent.

## 4. Issues only

- The issue author creates **issues only**. Do not create branches or PRs while writing the issue batch.
- The implementer creates exactly one branch and one PR for the issue.
- Do not bundle unrelated fixes into one PR.

## 5. Assignment and labels

- Assign every issue to `alaa157`.
- Use the existing labels. At minimum:
  - `bug` for a defect, `enhancement` for new behavior.
  - `mobile` + `kotlin` for Android code.
  - `ai` + `byok` for the AI/BYOK area.
  - `frontend` for settings UI.
  - `priority: high`, `priority: medium`, or `priority: low`.
  - `documentation` / `docs` for docs-only work.
- Do not invent a new label when an existing one fits.

## 6. No tests, no installs, no app start

This rule overrides `CONTRIBUTING.md`'s "Say what you tested" for agent-driven issue work.

- Do **not** run tests, Gradle, `./gradlew`, `npm`, `pytest`, `mvn`, `make`, or any build/verification command.
- Do **not** install dependencies.
- Do **not** start an emulator, a device, the app, or a server.
- The reviewer runs verification. Commands in the issue are **reference for the reviewer only**, not instructions for the implementer.
- The implementer's PR body states what changed and which files changed; it must not claim tests passed.

## 7. Branch and PR naming

- One issue = one branch = one PR.
- Branch: `fix/<area>-<issue-number>-<slug>` for fixes, `feat/<area>-<issue-number>-<slug>` for new behavior, `docs/<area>-<issue-number>-<slug>` for docs-only.
- PR base: the branch named in the issue (usually `feat/standalone-byok-ai`).
- PR body: `Closes #<issue-number>`, a short "What changed", a "Files changed" list, and the reviewer-only verification commands.
- Never force-push, rewrite history, or delete a branch you did not create for this issue.

## 8. Scope discipline

- Touch only the files listed in "Files in scope".
- If a change seems to require another area, leave a comment on the issue instead of expanding the PR.
- Do not delete other providers' source classes, tests, or branches as "cleanup" unless the issue explicitly says so.

## 9. Reporting

- In the PR body, list each acceptance criterion and how the code satisfies it.
- Call out anything the reviewer must check manually.
- If a criterion is impossible as written, stop and comment on the issue; do not silently drop it.

## 10. Issue brief template

Copy-paste this template when writing an issue:

```markdown
## Snapshot
- Repo:
- Base branch:
- Area:
- Scope:
- Priority:

## Why this exists

## Exact files and lines

## Required change

## Acceptance criteria
- [ ]

## Out of scope

## Hard rules
- No tests, no installs, no app/emulator/server start. Reviewer runs verification.

## Branch and PR
- Branch:
- PR title:
- PR base:
- PR body: `Closes #<issue-number>`

## Verification (reviewer only)
```
