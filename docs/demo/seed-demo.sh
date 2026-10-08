#!/usr/bin/env bash
# Demo seed for the team demo (plan §3): builds the full story on a FRESH
# backend in one run. Script, not migration — demo rows must never ship in
# migrations. Safe to re-run: caregiver login, recipients-by-name, today's
# notes, existing addenda/plans are reused, never duplicated.
#
# Backend caps one note per recipient per day (server-assigned date), so a
# single run seeds at most one note per recipient.
#
# Usage: docs/demo/seed-demo.sh [BASE_URL]   (default http://localhost:8080)
# Needs: curl, python3.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
EMAIL="demo@caregiver.app"
PASSWORD='Demo1234!'

command -v curl >/dev/null || { echo "need curl" >&2; exit 1; }
command -v python3 >/dev/null || { echo "need python3" >&2; exit 1; }

jget() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

api() { # METHOD PATH [TOKEN] [BODY] -> prints response body
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  if [ -n "$token" ]; then
    curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      ${body:+ -d "$body"}
  else
    curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H 'Content-Type: application/json' \
      ${body:+ -d "$body"}
  fi
}

split() { # "body\nCODE" -> CODE (last line)
  printf '%s' "$1" | tail -n 1
}

body_of() { # "body\nCODE" -> BODY (all but last line)
  printf '%s' "$1" | head -n -1
}

# die unless CODE is one of the expected ones; prints the body for diagnosis.
expect() { # CODE BODY... EXPECTED...
  local code="$1"; shift
  local body="$1"; shift
  local want
  for want in "$@"; do
    if [ "$code" = "$want" ]; then return 0; fi
  done
  echo "expected HTTP [$*], got $code: $body" >&2
  exit 1
}

echo "== demo seed @ $BASE =="

# 1. Caregiver: register, fall back to login when the demo user exists.
echo "-- caregiver $EMAIL"
resp=$(api POST /api/auth/register "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
code=$(split "$resp")
if [ "$code" = "201" ]; then
  TOKEN=$(body_of "$resp" | jget "['token']")
  echo "registered"
elif [ "$code" = "422" ]; then
  resp=$(api POST /api/auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  expect "$(split "$resp")" "$(body_of "$resp")" 200
  TOKEN=$(body_of "$resp" | jget "['token']")
  echo "logged in (existing demo user)"
else
  echo "register failed ($code): $(body_of "$resp")" >&2
  exit 1
fi

# 2. Recipients: reuse by name, else create.
ensure_recipient() {
  local name="$1" id resp code
  id=$(body_of "$(api GET "/api/recipients" "$TOKEN")" | python3 -c "
import json,sys
for r in json.load(sys.stdin):
    if r.get('name') == '''$name''':
        print(r['id']); break
")
  if [ -z "$id" ]; then
    resp=$(api POST /api/recipients "$TOKEN" "{\"name\":\"$name\"}")
    expect "$(split "$resp")" "$(body_of "$resp")" 201
    id=$(body_of "$resp" | jget "['id']")
    echo "created recipient $name ($id)" >&2
  else
    echo "reusing recipient $name ($id)" >&2
  fi
  printf '%s' "$id"
}
R1=$(ensure_recipient "محمد أحمد")
R2=$(ensure_recipient "Sara Samy")

# 3. Notes: one per recipient per day (server rule) — reuse today's if present.
# Wire values match the app's NoteOptions (mood good/fair/bad, meds taken/missed/unsure).
ensure_note() { # recipientId mood pain fall meds text
  local rid="$1" mood="$2" pain="$3" fall="$4" meds="$5" text="$6" today id body resp code
  today=$(date +%F)
  id=$(body_of "$(api GET "/api/notes?recipientId=$rid&from=$today&to=$today" "$TOKEN")" | python3 -c "
import json,sys
notes = json.load(sys.stdin)
print(notes[0]['id'] if notes else '')
")
  if [ -n "$id" ]; then echo "reusing today's note ($id)" >&2; printf '%s' "$id"; return; fi
  body=$(python3 - "$rid" "$mood" "$pain" "$fall" "$meds" "$text" <<'EOF'
import json, sys
_, rid, mood, pain, fall, meds, text = sys.argv
print(json.dumps({
    "recipientId": rid, "mood": mood, "appetite": "good", "sleep": "ok",
    "mobility": "walks", "medicationTaken": meds, "pain": int(pain),
    "fall": fall == "true", "text": text,
}))
EOF
)
  resp=$(api POST /api/notes "$TOKEN" "$body")
  expect "$(split "$resp")" "$(body_of "$resp")" 201
  id=$(body_of "$resp" | jget "['id']")
  echo "created note ($id)" >&2
  printf '%s' "$id"
}
N1=$(ensure_note "$R1" "bad" 8 true taken "سقط بالقرب من الحمام في الصباح. Fell near the bathroom in the morning, needs follow-up.")
N2=$(ensure_note "$R2" "good" 2 false unsure "Ate well today. الدواء غير واضح — unsure whether the evening dose was taken.")

# 4. Addendum on the first note (skip when one already exists).
ADDENDA=$(body_of "$(api GET "/api/notes/$N1" "$TOKEN")" | python3 -c "
import json,sys
d = json.load(sys.stdin)
for k in ('addenda', 'corrections', 'versions'):
    if isinstance(d.get(k), list) and d[k]:
        print(len(d[k])); break
else:
    print(0)
")
if [ "$ADDENDA" = "0" ]; then
  resp=$(api POST "/api/notes/$N1/addenda" "$TOKEN" '{"text":"تصحيح: السقوط كان بعد الظهر وليس صباحا."}')
  expect "$(split "$resp")" "$(body_of "$resp")" 201
  echo "appended addendum"
else
  echo "reusing existing addenda ($ADDENDA)"
fi

# 5. Plan for the first recipient: reuse when one exists, else suggest + accept.
PLAN=$(body_of "$(api GET /api/plans "$TOKEN")" | python3 -c "
import json,sys
plans = json.load(sys.stdin)
print(plans[0]['id'] if plans else '')
")
if [ -z "$PLAN" ]; then
  resp=$(api POST /api/plans/suggest "$TOKEN" "{\"recipientId\":\"$R1\",\"items\":[\"Morning walk with support\",\"Review evening medication\"]}")
  expect "$(split "$resp")" "$(body_of "$resp")" 201
  PLAN=$(body_of "$resp" | jget "['id']")
  resp=$(api POST "/api/plans/$PLAN/accept" "$TOKEN")
  expect "$(split "$resp")" "$(body_of "$resp")" 200
  echo "suggested + accepted plan ($PLAN)"
else
  echo "reusing plan ($PLAN)"
fi

echo
echo "== ready =="
echo "server : $BASE"
echo "login  : $EMAIL / $PASSWORD"
