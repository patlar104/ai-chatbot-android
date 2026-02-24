# Firebase Config Reference

Last verified: 2026-02-24 14:49:48 EST

## Active Environment
- Mode: production Firebase project (not emulator mode)
- Firebase project ID: `ai-chatbot-3cd89`
- Firebase project number: `449116553609`
- Android package: `org.example.ai.chatbot`
- Android app ID: `1:449116553609:android:ebb87c404f514983ce2158`
- Firestore DB: `(default)` in `nam5` (Native mode)
- Storage bucket: `ai-chatbot-3cd89.firebasestorage.app` in `US`

## Local Config Files
- Firebase CLI project binding: `.firebaserc`
- Firebase deploy config: `firebase.json`
- Firestore rules: `firestore.rules`
- Storage rules: `storage.rules`
- Android Firebase app config: `composeApp/google-services.json`

## Enabled APIs (Project)
- `firebase.googleapis.com`
- `firestore.googleapis.com`
- `firebasestorage.googleapis.com`
- `fcm.googleapis.com`
- `identitytoolkit.googleapis.com`
- `securetoken.googleapis.com`

## Rules Model (Current)
- Firestore:
  - User root doc: `users/{uid}`
  - Chat docs: `users/{uid}/chats/{chatId}`
  - Message docs: `users/{uid}/chats/{chatId}/messages/{messageId}`
  - Optional history docs: `users/{uid}/history/{historyId}`
  - Health checks: `healthchecks/{uid}`
  - Policy: owner-only (`request.auth.uid == uid`), default deny for everything else
- Storage:
  - User files: `users/{uid}/...`
  - Health checks: `healthchecks/{uid}/...`
  - Policy: owner-only (`request.auth.uid == uid`), default deny for everything else

## App Runtime Integration Points
- Firebase bootstrap + auth/firestore/storage health checks:
  - `composeApp/src/androidMain/kotlin/org/example/ai/chatbot/FirebaseRuntimeSetup.kt`
- FCM token/message hooks:
  - `composeApp/src/androidMain/kotlin/org/example/ai/chatbot/AppFirebaseMessagingService.kt`
- Android startup + notification permission:
  - `composeApp/src/androidMain/kotlin/org/example/ai/chatbot/MainActivity.kt`

## Retrieve Current Firebase Info (CLI)
```bash
firebase projects:list --json
firebase apps:list ANDROID --project ai-chatbot-3cd89 --json
firebase apps:sdkconfig ANDROID 1:449116553609:android:ebb87c404f514983ce2158 --project ai-chatbot-3cd89 --out composeApp/google-services.json
firebase firestore:databases:list --project ai-chatbot-3cd89 --json
firebase firestore:locations --project ai-chatbot-3cd89 --json
gcloud storage buckets describe gs://ai-chatbot-3cd89.firebasestorage.app --project ai-chatbot-3cd89
gcloud services list --enabled --project ai-chatbot-3cd89
```

## Deploy Rules
```bash
firebase deploy --only firestore:rules,storage --project ai-chatbot-3cd89 --non-interactive
```

## Quick Live Checks
```bash
# Build and process Google Services
./gradlew :composeApp:processDebugGoogleServices :composeApp:assembleDebug

# Anonymous auth check (returns idToken/localId when enabled)
API_KEY=$(jq -r '.client[0].api_key[0].current_key' composeApp/google-services.json)
curl -sS -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
  -H 'Content-Type: application/json' \
  -d '{"returnSecureToken": true}'
```

## Notes
- `composeApp/google-services.json` contains secrets (API key and OAuth metadata). Do not share it publicly.
- If Firebase project settings change (providers, OAuth, SHA certs), refresh `google-services.json` and rebuild.
