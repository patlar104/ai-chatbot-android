#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${1:-}"
if [[ -z "$PROJECT_ID" ]]; then
  echo "Usage: $0 <PROJECT_ID> [REGION] [SERVICE_NAME] [SECRET_NAME] [MODEL]"
  exit 1
fi

REGION="${2:-us-central1}"
SERVICE_NAME="${3:-aichatbot-genkit}"
SECRET_NAME="${4:-gemini-api-key}"
MODEL="${5:-gemini-2.5-flash}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SOURCE_DIR="${REPO_ROOT}/server/genkit"

if ! gcloud secrets describe "$SECRET_NAME" --project "$PROJECT_ID" >/dev/null 2>&1; then
  echo "Secret '$SECRET_NAME' not found in project '$PROJECT_ID'."
  echo "Create it first with ops/cloud/set-secret.sh."
  exit 1
fi

gcloud run deploy "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --source "$SOURCE_DIR" \
  --allow-unauthenticated \
  --set-env-vars "GCP_PROJECT=${PROJECT_ID},GENKIT_MODEL=${MODEL},GEMINI_API_KEY_SECRET=${SECRET_NAME}"

URL="$(gcloud run services describe "$SERVICE_NAME" --project "$PROJECT_ID" --region "$REGION" --format='value(status.url)')"
echo "Genkit deployed: ${URL}"
