# Parallel agent lanes

Four agents run at once across **two machines**, implementing epic #581 (local
accounts, per-account databases). This directory is the shared config: both
boxes read the same ownership map, so an agent on machine 1 cannot edit a file
that machine 2 owns.

## Machine split

| Machine | Lanes | Theme |
|---|---|---|
| **m1** | A, B | Crypto/policy/AI-key scoping, persistence and migration |
| **m2** | C, D | Domain/auth/session, UI/strings/docs |

The split is deliberate: `m1` lanes are mostly pure Kotlin and Room, `m2` lanes
are mostly Android UI and resources. Neither machine needs the other's build
output.

## One-time setup per machine

```bash
# on machine 1
./scripts/agents/setup.sh m1

# on machine 2
./scripts/agents/setup.sh m2
```

That creates a worktree per lane this machine owns, writes `local.properties`,
and records the machine id in `~/agents/machine-id`.

## Where the config lives

| File | Purpose |
|---|---|
| `scripts/agents/lanes.conf` | **The source of truth.** Which lane owns which file, and which machine runs it. |
| `scripts/agents/guard` | Ownership check, issue claims, lane status. |
| `scripts/agents/gradle-lane` | The only sanctioned way to run Gradle. |
| `scripts/agents/setup.sh` | Per-machine worktree setup. |
| `scripts/agents/brief.sh` | Generates an agent's standing brief. |

## The commands

```bash
./scripts/agents/guard status              # lanes, machine, next issue per lane
./scripts/agents/guard first a             # lane a's next issue
./scripts/agents/guard owns a <path>       # is this file lane a's?
./scripts/agents/guard verify a            # did lane a touch anything it doesn't own?
./scripts/agents/guard claim a 586         # take an exclusive claim
./scripts/agents/guard release a 586
./scripts/agents/gradle-lane a :app:compileDebugKotlin
./scripts/agents/brief.sh a > ~/agents/lanes/a/LANE.md
```

## What is enforced, and how

**File ownership is global.** `lanes.conf` maps each file to exactly one lane.
`guard verify` diffs the lane's branch against the merge base and fails on any
file the lane does not own. `gradle-lane` runs that check first, so a lane that
edited someone else's file cannot even compile.

**Machine assignment is enforced.** `guard check <lane>` refuses to run a lane
on the wrong machine, so a copy-pasted command on the wrong box fails loudly
instead of silently forking the work.

**Issue claims are shared.** Claims are posted as GitHub comments, not local
files, because two machines cannot share a local lock. `guard claim` reads the
last claim comment and refuses if another lane holds it.

**Gradle is serialized per machine.** `gradle-lane` takes a per-machine `flock`
and caps heap at 900 MB with `--max-workers=1 --no-daemon`. Four parallel
Gradle builds on a 3.8 GB box would OOM it. The lock is intentionally
per-machine: two boxes have separate CPUs and RAM, so there is nothing to
serialize across them.

## Unowned files

`CaregiverApp.kt` and `core/navigation/` are integration points. No lane owns
them. After the lanes merge, the human wires them by hand — that is the one
place parallel work has to be reconciled by someone who can see all four lanes.

## Merge order

Lanes merge independently into `feat/standalone-byok-ai`. File sets are disjoint,
so order does not matter. Each lane pushes its own branch and opens one PR per
issue.
