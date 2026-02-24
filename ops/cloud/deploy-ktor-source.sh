#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 6 ]]; then
  echo "Usage: $0 <PROJECT_ID> <GENKIT_BASE_URL> [REGION] [SERVICE_NAME] [JAVA_VERSION] [GRADLE_BUILD_ARGS]"
  exit 1
fi

PROJECT_ID="$1"
GENKIT_BASE_URL="$2"
REGION="${3:-us-central1}"
SERVICE_NAME="${4:-aichatbot-backend}"
JAVA_VERSION="${5:-21}"
GRADLE_BUILD_ARGS="${6:-:server:assemble -x test --build-cache}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

gcloud run deploy "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --source "$REPO_ROOT" \
  --allow-unauthenticated \
  --set-build-env-vars "GOOGLE_RUNTIME_VERSION=${JAVA_VERSION},GOOGLE_GRADLE_BUILD_ARGS=${GRADLE_BUILD_ARGS}" \
  --set-env-vars "GENKIT_BASE_URL=${GENKIT_BASE_URL}"

URL="$(gcloud run services describe "$SERVICE_NAME" --project "$PROJECT_ID" --region "$REGION" --format='value(status.url)')"
echo "Ktor backend deployed: ${URL}"
