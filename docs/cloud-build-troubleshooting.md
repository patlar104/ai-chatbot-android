# Cloud Build Troubleshooting (Run + Buildpacks)

## Official references used
- Cloud Run source deploy build env vars (service-specific): https://cloud.google.com/docs/buildpacks/set-environment-variables
- Google Cloud Buildpacks Java config (`GOOGLE_GRADLE_BUILD_ARGS`): https://cloud.google.com/docs/buildpacks/java

## Known-good backend source deploy command
```bash
gcloud run deploy aichatbot-backend \
  --project ai-chatbot-3cd89 \
  --region us-central1 \
  --source . \
  --allow-unauthenticated \
  --set-build-env-vars "GOOGLE_RUNTIME_VERSION=21,GOOGLE_GRADLE_BUILD_ARGS=:server:assemble -x test --build-cache" \
  --set-env-vars "GENKIT_BASE_URL=https://aichatbot-genkit-petuxudhua-uc.a.run.app"
```

## Fast debug commands
```bash
# 1) See latest regional builds
gcloud builds list --project ai-chatbot-3cd89 --region us-central1 --limit=10 --sort-by='~createTime'

# 2) Describe failing build
gcloud builds describe <BUILD_ID> --project ai-chatbot-3cd89 --region us-central1

# 3) Decode buildpack error payload (if present in results.buildStepOutputs[1])
gcloud builds describe <BUILD_ID> --project ai-chatbot-3cd89 --region us-central1 \
  --format='value(results.buildStepOutputs[1])' | base64 --decode

# 4) Check Cloud Run conditions
gcloud run services describe aichatbot-backend --project ai-chatbot-3cd89 --region us-central1
gcloud run services describe aichatbot-genkit --project ai-chatbot-3cd89 --region us-central1
```

## Failure patterns already seen
- `org.gradle.java.home` points to local machine path: remove/avoid machine-specific JDK paths in `gradle.properties`.
- Build running Java 25 (`25.0.2`) while project expects 21: set `GOOGLE_RUNTIME_VERSION=21`.
- Android SDK missing during server cloud build: avoid default full-project `assemble` by setting `GOOGLE_GRADLE_BUILD_ARGS=:server:assemble -x test --build-cache`.
