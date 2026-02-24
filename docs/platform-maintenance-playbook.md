# Platform Maintenance Playbook

## Goal
Keep secrets out of source control and make feature integrations repeatable.

## Command Discovery
- Use the generic discovery runbook: `docs/tool-command-discovery.md`
- Default workflow:
  1. Use tool help (`--help`) from top-level to resource-level.
  2. `list` resources before `describe`.
  3. Validate state in structured output (`json`/`yaml`) before mutating.
  4. Prefer generic discovery paths over brittle one-off commands.

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

## Cost Hygiene Checklist
1. Confirm active project/account context before any cleanup.
2. Keep Cloud Run `min-instances=0` for non-critical services.
3. Set reasonable `max-instances` per service workload profile.
4. Remove stale Artifact Registry image digests not used by live revisions.
5. Clean old source-deploy objects in Cloud Storage.
6. Enforce Cloud Storage lifecycle policies for auto-deletion.
7. Re-verify service health endpoints and IAM exposure after cleanup.

## Security Defaults
- No secrets in `.env` committed files.
- No secrets in Android code/resources.
- Least-privilege service account permissions.
- Rotate keys by adding a new secret version, then redeploy.
- Reuse one existing runtime service account per environment when possible instead of creating new identities ad hoc.
