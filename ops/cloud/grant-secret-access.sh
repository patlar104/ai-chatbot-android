#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <PROJECT_ID> <SECRET_NAME> <SERVICE_ACCOUNT_EMAIL>"
  exit 1
fi

PROJECT_ID="$1"
SECRET_NAME="$2"
SERVICE_ACCOUNT_EMAIL="$3"

gcloud secrets add-iam-policy-binding "$SECRET_NAME" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/secretmanager.secretAccessor" \
  --project "$PROJECT_ID"

echo "Granted secret accessor on '$SECRET_NAME' to '$SERVICE_ACCOUNT_EMAIL'."
