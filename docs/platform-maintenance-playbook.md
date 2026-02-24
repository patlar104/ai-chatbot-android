# Platform Maintenance Playbook

## Goal
Keep secrets out of source control and make feature integrations repeatable.

## Standard Pattern For External Services
1. Backend-only integration (never mobile API keys).
2. Secret in cloud secret manager.
3. Runtime config via env vars.
4. Health endpoint + typed request/response.
5. One deployment script per service.
6. Short runbook doc in `docs/`.

## Secret Naming Convention
- Use lowercase kebab names in Secret Manager:
  - `gemini-api-key`
  - `openai-api-key`
  - `stripe-secret-key`
- Map each service secret to an env var:
  - `GEMINI_API_KEY_SECRET=gemini-api-key`

## Env Var Convention
- Required runtime URLs:
  - `GENKIT_BASE_URL`
- Optional tuning:
  - `GENKIT_MODEL`
- Cloud project:
  - `GCP_PROJECT` or `GOOGLE_CLOUD_PROJECT`

## Feature Checklist (Use For New Integrations)
1. Add backend adapter/service class.
2. Add request validation and error mapping.
3. Add unit tests for success + bad request + upstream error.
4. Add secret retrieval path (env var + Secret Manager fallback).
5. Add deploy/update script under `ops/cloud/`.
6. Update docs with setup, run, and rollback notes.

## Current Ops Scripts
- `ops/cloud/set-secret.sh`
- `ops/cloud/grant-secret-access.sh`
- `ops/cloud/deploy-genkit.sh`
- `ops/cloud/deploy-ktor.sh`
- `ops/cloud/deploy-ktor-source.sh`

## Security Defaults
- No secrets in `.env` committed files.
- No secrets in Android code/resources.
- Least-privilege service account permissions.
- Rotate keys by adding a new secret version, then redeploy.
