#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 <PROJECT_ID> <SECRET_NAME> [VALUE]"
  echo "If VALUE is omitted, the script reads secret value from stdin."
  exit 1
fi

PROJECT_ID="$1"
SECRET_NAME="$2"
VALUE="${3:-}"

if [[ -z "$VALUE" ]]; then
  if [[ -t 0 ]]; then
    echo "Reading from stdin expected. Example:"
    echo "  printf '%s' 'super-secret' | $0 $PROJECT_ID $SECRET_NAME"
    exit 1
  fi
  VALUE="$(cat)"
fi

if [[ -z "$VALUE" ]]; then
  echo "Secret value is empty."
  exit 1
fi

if ! gcloud secrets describe "$SECRET_NAME" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud secrets create "$SECRET_NAME" \
    --replication-policy="automatic" \
    --project "$PROJECT_ID"
fi

printf '%s' "$VALUE" | gcloud secrets versions add "$SECRET_NAME" \
  --data-file=- \
  --project "$PROJECT_ID"

echo "Secret '$SECRET_NAME' updated in project '$PROJECT_ID'."
