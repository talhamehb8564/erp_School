#!/usr/bin/env bash
# Hits Phase 1 APIs on a running server. Start the app first: mvn spring-boot:run
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
info() { printf '\033[36m%s\033[0m\n' "$*"; }

json_field() {
  python3 -c "import json,sys; d=json.load(sys.stdin)
def g(o,p):
  for k in p.split('.'):
    if o is None: return None
    o = o[k] if isinstance(o, dict) else None
  return o
v=g(d, sys.argv[1])
print('' if v is None else v)" "$1"
}

fail() { red "FAIL: $1"; exit 1; }

info "Health check..."
HEALTH=$(curl -sS "$BASE/api/v1/health")
echo "$HEALTH" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "health"

info "Login ERP owner..."
OWNER=$(curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"erp.owner","password":"Owner@12345"}')
echo "$OWNER" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "owner login: $OWNER"
OWNER_TOKEN=$(echo "$OWNER" | json_field data.accessToken)
OWNER_REFRESH=$(echo "$OWNER" | json_field data.refreshToken)
[ -n "$OWNER_TOKEN" ] || fail "missing owner access token"

info "List tenants as owner..."
TENANTS=$(curl -sS "$BASE/api/v1/tenants" -H "Authorization: Bearer $OWNER_TOKEN")
echo "$TENANTS" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True; assert d['data']['totalElements'] >= 1" \
  || fail "list tenants: $TENANTS"
TENANT_ID=$(echo "$TENANTS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data']['content'][0]['id'])")

info "Login school admin..."
ADMIN=$(curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"GVS-ADM-0001","password":"ChangeMe@123"}')
echo "$ADMIN" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "admin login: $ADMIN"
ADMIN_TOKEN=$(echo "$ADMIN" | json_field data.accessToken)
ADMIN_TENANT=$(echo "$ADMIN" | json_field data.user.tenantId)

info "School admin lists own users..."
USERS=$(curl -sS "$BASE/api/v1/users" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$USERS" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "list users: $USERS"

info "Teacher must not list users..."
TEACHER=$(curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"GVS-TCH-0001","password":"ChangeMe@123"}')
TEACHER_TOKEN=$(echo "$TEACHER" | json_field data.accessToken)
CODE=$(curl -sS -o /tmp/erp_forbidden.json -w '%{http_code}' \
  "$BASE/api/v1/users" -H "Authorization: Bearer $TEACHER_TOKEN")
[ "$CODE" = "403" ] || fail "teacher user list expected 403 got $CODE"

info "Create teacher as school admin..."
CREATED=$(curl -sS -X POST "$BASE/api/v1/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Api","lastName":"Test","role":"TEACHER","email":"api.test.'$(date +%s)'@greenvalley.school"}')
echo "$CREATED" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True; assert d['data']['temporaryPassword']" \
  || fail "create user: $CREATED"
NEW_USERNAME=$(echo "$CREATED" | json_field data.user.username)
NEW_PASSWORD=$(echo "$CREATED" | json_field data.temporaryPassword)
info "Generated username $NEW_USERNAME"

info "Login with generated credentials..."
NEW_LOGIN=$(curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$NEW_USERNAME\",\"password\":\"$NEW_PASSWORD\"}")
echo "$NEW_LOGIN" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True; assert d['data']['user']['mustChangePassword'] is True" \
  || fail "generated login: $NEW_LOGIN"

info "Refresh owner token..."
REFRESHED=$(curl -sS -X POST "$BASE/api/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$OWNER_REFRESH\"}")
echo "$REFRESHED" | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "refresh: $REFRESHED"

info "Wrong password is 401..."
CODE=$(curl -sS -o /tmp/erp_401.json -w '%{http_code}' -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"erp.owner","password":"nope"}')
[ "$CODE" = "401" ] || fail "bad password expected 401 got $CODE"

info "Unauthenticated users list is 401..."
CODE=$(curl -sS -o /tmp/erp_401b.json -w '%{http_code}' "$BASE/api/v1/users")
[ "$CODE" = "401" ] || fail "anon users expected 401 got $CODE"

info "Owner can read tenant $TENANT_ID (admin tenant $ADMIN_TENANT)..."
curl -sS "$BASE/api/v1/tenants/$TENANT_ID" -H "Authorization: Bearer $OWNER_TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['success'] is True" \
  || fail "owner get tenant"

green "Phase 1 API smoke tests passed."
