#!/usr/bin/env bash
# brief <lane> — print the standing brief for a lane, generated from lanes.conf.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONF="$ROOT/scripts/agents/lanes.conf"
LANES_DIR="${LANES_DIR:-$HOME/agents/lanes}"
lane="${1:?usage: brief.sh <lane>}"

machine=$(awk -F'|' -v l="$lane" '$1==l{print $2; exit}' "$CONF")
[ -n "$machine" ] || { echo "unknown lane '$lane'" >&2; exit 2; }

files=$(awk -F'|' -v l="$lane" '$1==l{print "  - `" $3 "`"}' "$CONF")
first=$("$ROOT/scripts/agents/guard" first "$lane" 2>/dev/null || echo "(could not read issues)")

cat <<EOF
# Lane $lane — standing brief

Machine: **$machine**. Worktree: \`$LANES_DIR/$lane\`. Branch: \`lane/$lane\`.

You are one of four agents working in parallel across two machines. Another
agent on another machine is editing different files at the same time.

## Your first issue

$first

## Hard rules

1. Work only inside \`$LANES_DIR/$lane\`. Never cd into another lane's worktree.
2. Edit only files your lane owns. Before editing:
   \`\`\`
   ./scripts/agents/guard owns $lane <path>
   \`\`\`
   DENIED or UNOWNED means stop and report. Do not edit.
3. Never run gradle directly. Use:
   \`\`\`
   ./scripts/agents/gradle-lane $lane <task>
   \`\`\`
   It serializes builds on this machine and caps heap.
4. Do not run full test, build or lint suites. Write code and tests, then stop.
5. One branch (\`lane/$lane\`), one PR per issue, into \`$BASE_BRANCH\`.
6. Claim your issue before starting: \`./scripts/agents/guard claim $lane <issue>\`

## Files your lane owns

$files

## Before you start

\`\`\`
./scripts/agents/guard check $lane
./scripts/agents/guard claim $lane <issue>
\`\`\`

## When done

\`\`\`
./scripts/agents/guard verify $lane
./scripts/agents/guard release $lane <issue>
git push -u origin lane/$lane
\`\`\`
EOF
