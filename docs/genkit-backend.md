# Genkit Backend Setup

## What is implemented
- Genkit sidecar service in `server/genkit` using Firebase Genkit + Google GenAI plugin
- Ktor backend delegates `POST /chat` to the Genkit sidecar by default
- Endpoint: `POST /chat`
- Endpoint: `GET /health`
- Genkit base URL loaded from backend environment (`GENKIT_BASE_URL`) or JVM property

## Required secret
- `GEMINI_API_KEY` (Google AI Studio API key)
- Preferred for cloud: store in Google Secret Manager as `gemini-api-key` and set `GEMINI_API_KEY_SECRET=gemini-api-key`.

## Run locally
```bash
# terminal 1: Genkit sidecar
cd server/genkit
npm install
export GEMINI_API_KEY="your_key_here"
npm run dev

# terminal 2: Ktor backend
cd ../..
export GENKIT_BASE_URL="http://127.0.0.1:3400"
./gradlew :server:run
```

## Quick checks
```bash
# Ktor health
curl -s http://localhost:8080/health

# Genkit health
curl -s http://localhost:3400/health

# Chat through Ktor -> Genkit
curl -s -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Write a one-line hello","userId":"demo-user","sessionId":"demo-session"}'
```

## Response shape
Success:
```json
{
  "reply": "....",
  "model": "gemini-2.5-flash",
  "sessionId": "demo-session",
  "userId": "demo-user"
}
```

Error:
```json
{
  "error": {
    "code": "bad_request | upstream_failure",
    "message": "...."
  }
}
```

## Notes
- The mobile app should call the Ktor backend endpoint; do not ship Gemini API key inside the app.
- `POST /chat` expects a JSON body with at least `message`.
- If Genkit runs on another host/port, set `GENKIT_BASE_URL` on the Ktor server.

## Cloud (recommended)
```bash
# 1) store secret in Secret Manager
printf '%s' 'your_key_here' | ./ops/cloud/set-secret.sh ai-chatbot-3cd89 gemini-api-key

# 2) deploy Genkit service (reads secret from Secret Manager)
./ops/cloud/deploy-genkit.sh ai-chatbot-3cd89

# 3) deploy Ktor backend from source (recommended for this repo)
# uses Cloud Buildpacks vars:
# - GOOGLE_RUNTIME_VERSION=21
# - GOOGLE_GRADLE_BUILD_ARGS=":server:assemble -x test --build-cache"
./ops/cloud/deploy-ktor-source.sh ai-chatbot-3cd89 <GenkitServiceURL>

# 4) (optional) deploy Ktor backend from prebuilt image
./ops/cloud/deploy-ktor.sh ai-chatbot-3cd89 <KtorImageURI> <GenkitServiceURL>
```

Service account secret access (if needed):
```bash
./ops/cloud/grant-secret-access.sh ai-chatbot-3cd89 gemini-api-key <service-account-email>
```

## Build failure quick debug
```bash
# latest builds in region
gcloud builds list --project ai-chatbot-3cd89 --region us-central1 --limit=5 --sort-by='~createTime'

# inspect one build
gcloud builds describe <BUILD_ID> --project ai-chatbot-3cd89 --region us-central1

# service readiness/conditions
gcloud run services describe aichatbot-backend --project ai-chatbot-3cd89 --region us-central1
gcloud run services describe aichatbot-genkit --project ai-chatbot-3cd89 --region us-central1
```
