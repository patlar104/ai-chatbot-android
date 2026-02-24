#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 || $# -gt 5 ]]; then
  echo "Usage: $0 <PROJECT_ID> <IMAGE_URI> <GENKIT_BASE_URL> [REGION] [SERVICE_NAME]"
  exit 1
fi

PROJECT_ID="$1"
IMAGE_URI="$2"
GENKIT_BASE_URL="$3"
REGION="${4:-us-central1}"
SERVICE_NAME="${5:-aichatbot-backend}"

gcloud run deploy "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --image "$IMAGE_URI" \
  --allow-unauthenticated \
  --set-env-vars "GENKIT_BASE_URL=${GENKIT_BASE_URL}"

URL="$(gcloud run services describe "$SERVICE_NAME" --project "$PROJECT_ID" --region "$REGION" --format='value(status.url)')"
echo "Ktor backend deployed: ${URL}"
