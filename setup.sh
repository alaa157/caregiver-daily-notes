#!/usr/bin/env bash
#
# Caregiver Daily Notes: one-command project setup.
# Run from the project root:   ./setup.sh [options]
#
# Options:
#   --test    also run backend and web unit tests
#   --e2e     also install the Chromium browser used by Playwright
#   --run     start the backend and the web dev server when setup is done
#   --tunnel  also expose the backend through a Cloudflare quick tunnel
#             (implies --run)
#   -h|--help show this help
#
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

RUN_TESTS=false
INSTALL_E2E=false
START_APP=false
USE_TUNNEL=false

usage() {
  cat <<'EOF'
Caregiver Daily Notes: one-command project setup.
Run from the project root:   ./setup.sh [options]

Options:
  --test    also run backend and web unit tests
  --e2e     also install the Chromium browser used by Playwright
  --run     start the backend and the web dev server when setup is done
  --tunnel  also expose the backend through a Cloudflare quick tunnel
            (implies --run)
  -h|--help show this help
EOF
}

for arg in "$@"; do
  case "$arg" in
    --test) RUN_TESTS=true ;;
    --e2e)  INSTALL_E2E=true ;;
    --run)  START_APP=true ;;
    --tunnel) USE_TUNNEL=true; START_APP=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)"; exit 1 ;;
  esac
done

info() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ok\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !!\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERR\033[0m %s\n' "$*" >&2; exit 1; }

# Download the official cloudflared binary into the user's bin directory.
ensure_cloudflared() {
  if command -v cloudflared >/dev/null 2>&1; then
    CLOUDFLARED_BIN="$(command -v cloudflared)"
    ok "cloudflared found at $CLOUDFLARED_BIN"
    return
  fi

  info "Installing cloudflared (one-time download)"
  local arch bindir url
  case "$(uname -m)" in
    x86_64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) fail "Unsupported architecture for cloudflared: $(uname -m)" ;;
  esac
  bindir="$HOME/.local/bin"
  mkdir -p "$bindir"
  url="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${arch}"
  curl -fsSL "$url" -o "$bindir/cloudflared" || fail "Could not download cloudflared. Install it manually, then re-run."
  chmod +x "$bindir/cloudflared"
  CLOUDFLARED_BIN="$bindir/cloudflared"
  export PATH="$bindir:$PATH"
  ok "cloudflared installed at $CLOUDFLARED_BIN"
}

# ---------------------------------------------------------------- prerequisites
info "Checking prerequisites"

command -v java >/dev/null 2>&1 || fail "Java 21 is required but 'java' was not found."
JAVA_MAJOR=$(java -version 2>&1 | awk -F'[".]' '/version/ {print $2; exit}')
[ "${JAVA_MAJOR:-0}" -ge 21 ] || fail "Java 21+ is required (found ${JAVA_MAJOR:-unknown})."
ok "Java $JAVA_MAJOR"

command -v mvn >/dev/null 2>&1 || fail "Maven is required but 'mvn' was not found."
ok "Maven"

command -v node >/dev/null 2>&1 || fail "Node.js is required but 'node' was not found."
command -v npm  >/dev/null 2>&1 || fail "npm is required but was not found."
NODE_MAJOR=$(node -v | sed -E 's/^v([0-9]+).*/\1/')
[ "$NODE_MAJOR" -ge 18 ] || fail "Node.js 18+ is required (found $(node -v))."
ok "Node $(node -v)"

command -v curl >/dev/null 2>&1 || fail "curl is required to download tooling."
ok "curl"

# ----------------------------------------------------------------- environment
info "Preparing .env"

if [ ! -f .env ]; then
  cp .env.example .env
  ok "Created .env from .env.example"
else
  ok ".env already exists (left untouched)"
fi

if grep -q '^JWT_SECRET=CHANGEME' .env; then
  if command -v openssl >/dev/null 2>&1; then
    SECRET=$(openssl rand -base64 32)
    sed -i.bak "s|^JWT_SECRET=CHANGEME|JWT_SECRET=${SECRET}|" .env && rm -f .env.bak
    ok "Generated a random JWT_SECRET"
  else
    warn "openssl not found. Set JWT_SECRET in .env yourself (32+ random bytes)."
  fi
fi

DB_READY=true
if grep -Eq '^(DATABASE_URL|DATABASE_PASSWORD)=.*CHANGEME' .env; then
  DB_READY=false
  warn "Database settings in .env still say CHANGEME."
  warn "Add your Supabase values (DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD) before running the backend."
fi

# --------------------------------------------------------------------- backend
info "Building the backend (downloads Maven dependencies)"
mvn -f backend/pom.xml -q -DskipTests package
ok "Backend built"

# ------------------------------------------------------------------------- web
info "Installing web dependencies"
(cd web && npm ci)
ok "Web dependencies installed"

if [ "$INSTALL_E2E" = true ]; then
  info "Installing Playwright Chromium"
  (cd web && npx playwright install chromium)
  ok "Chromium installed"
fi

# ----------------------------------------------------------------------- tests
if [ "$RUN_TESTS" = true ]; then
  info "Running backend tests"
  mvn -f backend/pom.xml -q test
  ok "Backend tests passed"

  info "Running web type-check and unit tests"
  (cd web && npm run typecheck && npm test)
  ok "Web tests passed"
fi

# ------------------------------------------------------------------------- run
if [ "$START_APP" = true ]; then
  [ "$DB_READY" = true ] || fail "Fill in the database values in .env first, then run: ./setup.sh --run"

  if [ "$USE_TUNNEL" = true ]; then
    ensure_cloudflared
  fi

  info "Starting backend on http://localhost:8080 and web dev server"
  set -a; . ./.env; set +a

  BACKEND_PID=""
  TUNNEL_PID=""
  cleanup() {
    [ -n "$TUNNEL_PID" ] && kill "$TUNNEL_PID" 2>/dev/null || true
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
  }
  trap cleanup EXIT INT TERM

  mvn -f backend/pom.xml spring-boot:run &
  BACKEND_PID=$!

  if [ "$USE_TUNNEL" = true ]; then
    info "Starting Cloudflare tunnel to the backend (URL is printed below)"
    "$CLOUDFLARED_BIN" tunnel --url http://localhost:8080 &
    TUNNEL_PID=$!
  fi

  (cd web && npm run dev)
  exit 0
fi

# ---------------------------------------------------------------------- finish
info "Setup complete"
if [ "$DB_READY" = true ]; then
  echo "Start everything with:  ./setup.sh --run"
else
  echo "Next: edit .env with your database values, then run:  ./setup.sh --run"
fi
echo "Or expose the backend with a tunnel:  ./setup.sh --tunnel"
echo "Or start them separately:"
echo "  backend:  set -a; source .env; set +a; mvn -f backend/pom.xml spring-boot:run"
echo "  web:      cd web && npm run dev"
