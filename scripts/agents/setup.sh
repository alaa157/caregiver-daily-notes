#!/usr/bin/env bash
# setup — prepare this machine to run its lanes.
#
#   ./scripts/agents/setup.sh m1        # machine 1 runs lanes a and b
#   ./scripts/agents/setup.sh m2        # machine 2 runs lanes c and d
#
# Creates one worktree per lane owned by this machine, writes local.properties,
# and records the machine id so guard can refuse a lane that belongs elsewhere.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONF="$ROOT/scripts/agents/lanes.conf"
BASE="${BASE_BRANCH:-feat/standalone-byok-ai}"
MACHINE="${1:?usage: setup.sh <m1|m2>}"
LANES_DIR="${LANES_DIR:-$HOME/agents/lanes}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"

case "$MACHINE" in
  m1) LANES="a b" ;;
  m2) LANES="c d" ;;
  *) echo "unknown machine '$MACHINE' (expected m1 or m2)" >&2; exit 2 ;;
esac

mkdir -p "$HOME/agents/logs" "$LANES_DIR"
echo "$MACHINE" > "$HOME/agents/machine-id"
echo "machine id: $MACHINE -> lanes $LANES"

cd "$ROOT"
git fetch origin "$BASE" -q

for L in $LANES; do
  W="$LANES_DIR/$L"
  if [ -d "$W" ]; then
    echo "lane $L: worktree exists at $W"
  else
    git worktree add -f "$W" -b "lane/$L" "origin/$BASE" >/dev/null 2>&1 \
      && echo "lane $L: created $W on lane/$L" \
      || { echo "lane $L: FAILED to create worktree" >&2; continue; }
  fi
  printf 'sdk.dir=%s\n' "$SDK" > "$W/local.properties"
done

# Keep lane scaffolding out of git status on every lane worktree.
for L in $LANES; do
  W="$LANES_DIR/$L"
  [ -d "$W" ] || continue
  EXCLUDE="$(git -C "$W" rev-parse --git-path info/exclude)"
  case "$EXCLUDE" in
    /*) ;;
    *) EXCLUDE="$W/$EXCLUDE" ;;
  esac
  mkdir -p "$(dirname "$EXCLUDE")"
  grep -qxF 'local.properties' "$EXCLUDE" 2>/dev/null \
    || printf '\nlocal.properties\nLANE.md\n' >> "$EXCLUDE"
done

echo
echo "Ready. Verify with:"
echo "  ./scripts/agents/guard status"
for L in $LANES; do echo "  ./scripts/agents/guard check $L"; done
