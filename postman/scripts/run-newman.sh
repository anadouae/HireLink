#!/usr/bin/env bash
# Exécute la collection HireLink via Newman (Linux/macOS / GitHub Actions).
# Interdit : baseUrl sur :8081 (démo). Pas de bypass CAPTCHA/OTP/MFA.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POSTMAN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COLLECTION="${POSTMAN_DIR}/HireLink.postman_collection.json"
ENV_EXAMPLE="${POSTMAN_DIR}/environments/HireLink.iso.environment.example.json"
REPORTS="${POSTMAN_DIR}/reports"
mkdir -p "${REPORTS}"

BASE_URL="${HL_API:-http://127.0.0.1:18081/api/v1}"
BASE_URL="${BASE_URL%/}"

if [[ "${BASE_URL}" =~ :8081(/|$) ]]; then
  echo "REFUS : baseUrl pointe vers la démo 8081. Utilisez un port ISO/CI." >&2
  exit 2
fi

if [[ -f "${SCRIPT_DIR}/build-collection.mjs" ]]; then
  node "${SCRIPT_DIR}/build-collection.mjs"
fi

: "${HL_CANDIDATE_PASSWORD:?définir HL_CANDIDATE_PASSWORD (seed DataSeeder)}"
: "${HL_RECRUITER_PASSWORD:?définir HL_RECRUITER_PASSWORD (seed DataSeeder)}"
: "${HL_ADMIN_PASSWORD:?définir HL_ADMIN_PASSWORD (seed DataSeeder)}"

STAMP="$(date +%Y%m%d-%H%M%S)"
JSON_REPORT="${REPORTS}/newman-${STAMP}.json"

echo "Newman → ${BASE_URL}"
echo "Collection → ${COLLECTION}"

npx --yes newman@6 run "${COLLECTION}" \
  -e "${ENV_EXAMPLE}" \
  --env-var "baseUrl=${BASE_URL}" \
  --env-var "candidateEmail=${HL_CANDIDATE_EMAIL:-candidat@hirelink.ma}" \
  --env-var "recruiterEmail=${HL_RECRUITER_EMAIL:-n.bennani@atlasdigital.ma}" \
  --env-var "adminEmail=${HL_ADMIN_EMAIL:-admin@hirelink.ma}" \
  --env-var "candidatePassword=${HL_CANDIDATE_PASSWORD}" \
  --env-var "recruiterPassword=${HL_RECRUITER_PASSWORD}" \
  --env-var "adminPassword=${HL_ADMIN_PASSWORD}" \
  --delay-request 250 \
  --timeout-request 30000 \
  --reporters cli,json \
  --reporter-json-export "${JSON_REPORT}"

echo "Exit=$? — rapport JSON : ${JSON_REPORT}"