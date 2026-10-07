#!/usr/bin/env bash
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
PRODUCT_ID="${1:?usage: change-evidence.sh PRODUCT_ID}"

curl -sS --fail-with-body "$PORTAL_URL/api/evidence/products/$PRODUCT_ID" | jq .
