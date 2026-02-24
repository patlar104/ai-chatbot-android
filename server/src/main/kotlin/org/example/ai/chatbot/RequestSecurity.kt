package org.example.ai.chatbot

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

private const val SERVER_FIREBASE_APP_NAME = "chat-server-backend"
private const val APP_CHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"

data class VerifiedRequestContext(
    val uid: String,
)

interface RequestSecurityVerifier {
    fun verify(authorizationHeader: String?, appCheckHeader: String?): VerifiedRequestContext?
}

object AllowAllRequestSecurityVerifier : RequestSecurityVerifier {
    override fun verify(authorizationHeader: String?, appCheckHeader: String?): VerifiedRequestContext? = null
}

enum class RequestSecurityMode {
    REQUIRED,
    OPTIONAL,
    DISABLED,
}

open class RequestSecurityException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UnauthorizedRequestSecurityException(
    errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RequestSecurityException(errorCode, message, cause)

class ForbiddenRequestSecurityException(
    errorCode: String,
    message: String,
    cause: Throwable? = null,
) : RequestSecurityException(errorCode, message, cause)

class FirebaseRequestSecurityVerifier private constructor(
    private val mode: RequestSecurityMode,
    private val firebaseAuth: FirebaseAuth?,
    private val appCheckVerifier: AppCheckTokenVerifier?,
) : RequestSecurityVerifier {

    override fun verify(authorizationHeader: String?, appCheckHeader: String?): VerifiedRequestContext? {
        if (mode == RequestSecurityMode.DISABLED) return null

        val idToken = extractBearerToken(authorizationHeader)
        val normalizedAppCheckToken = appCheckHeader?.trim().orEmpty().ifBlank { null }
        val hasAnyToken = !idToken.isNullOrBlank() || !normalizedAppCheckToken.isNullOrBlank()

        if (!hasAnyToken) {
            if (mode == RequestSecurityMode.REQUIRED) {
                throw UnauthorizedRequestSecurityException(
                    errorCode = "missing_credentials",
                    message = "Missing Authorization bearer token and X-Firebase-AppCheck header",
                )
            }
            return null
        }

        if (idToken.isNullOrBlank() || normalizedAppCheckToken.isNullOrBlank()) {
            throw UnauthorizedRequestSecurityException(
                errorCode = "missing_credentials",
                message = "Both Authorization bearer token and X-Firebase-AppCheck header are required",
            )
        }

        val auth = firebaseAuth ?: throw UnauthorizedRequestSecurityException(
            errorCode = "security_unavailable",
            message = "Firebase Auth verifier is unavailable",
        )
        val appCheck = appCheckVerifier ?: throw ForbiddenRequestSecurityException(
            errorCode = "security_unavailable",
            message = "Firebase App Check verifier is unavailable",
        )

        val decodedIdToken = try {
            auth.verifyIdToken(idToken, true)
        } catch (error: Exception) {
            throw UnauthorizedRequestSecurityException(
                errorCode = "invalid_id_token",
                message = "Invalid Firebase ID token",
                cause = error,
            )
        }

        try {
            appCheck.verify(normalizedAppCheckToken)
        } catch (error: RequestSecurityException) {
            throw error
        } catch (error: Exception) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "Invalid Firebase App Check token",
                cause = error,
            )
        }

        return VerifiedRequestContext(uid = decodedIdToken.uid)
    }

    companion object {
        fun fromEnvironment(logger: Logger): RequestSecurityVerifier {
            val mode = parseSecurityMode(
                rawMode = System.getenv("CHAT_SECURITY_MODE"),
                isCloudRun = !System.getenv("K_SERVICE").isNullOrBlank(),
            )

            if (mode == RequestSecurityMode.DISABLED) {
                logger.warn("Chat request security is disabled.")
                return AllowAllRequestSecurityVerifier
            }

            val firebaseProjectId = System.getenv("FIREBASE_PROJECT_ID")
                ?: System.getProperty("FIREBASE_PROJECT_ID")
            val firebaseProjectNumber = System.getenv("FIREBASE_PROJECT_NUMBER")
                ?: System.getProperty("FIREBASE_PROJECT_NUMBER")

            val firebaseApp = runCatching {
                firebaseApp(firebaseProjectId)
            }.onFailure { error ->
                if (mode == RequestSecurityMode.REQUIRED) {
                    throw IllegalStateException(
                        "CHAT_SECURITY_MODE=required but Firebase Admin initialization failed.",
                        error,
                    )
                }
                logger.warn(
                    "Firebase Admin initialization failed in optional mode; security checks are skipped.",
                    error,
                )
            }.getOrNull()

            if (firebaseApp == null) {
                return AllowAllRequestSecurityVerifier
            }

            if (firebaseProjectNumber.isNullOrBlank()) {
                if (mode == RequestSecurityMode.REQUIRED) {
                    throw IllegalStateException(
                        "CHAT_SECURITY_MODE=required requires FIREBASE_PROJECT_NUMBER for App Check verification."
                    )
                }
                logger.warn(
                    "FIREBASE_PROJECT_NUMBER is missing in optional mode; security checks are skipped."
                )
                return AllowAllRequestSecurityVerifier
            }

            logger.info("Chat request security mode: {}", mode.name.lowercase())
            return FirebaseRequestSecurityVerifier(
                mode = mode,
                firebaseAuth = FirebaseAuth.getInstance(firebaseApp),
                appCheckVerifier = FirebaseAppCheckJwtVerifier(firebaseProjectNumber),
            )
        }

        private fun parseSecurityMode(rawMode: String?, isCloudRun: Boolean): RequestSecurityMode {
            if (rawMode.isNullOrBlank()) {
                return if (isCloudRun) RequestSecurityMode.REQUIRED else RequestSecurityMode.OPTIONAL
            }
            return when (rawMode.trim().lowercase()) {
                "required" -> RequestSecurityMode.REQUIRED
                "optional" -> RequestSecurityMode.OPTIONAL
                "disabled" -> RequestSecurityMode.DISABLED
                else -> if (isCloudRun) RequestSecurityMode.REQUIRED else RequestSecurityMode.OPTIONAL
            }
        }

        private fun firebaseApp(projectId: String?): FirebaseApp {
            FirebaseApp.getApps().firstOrNull { it.name == SERVER_FIREBASE_APP_NAME }?.let { existing ->
                return existing
            }

            val optionsBuilder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
            if (!projectId.isNullOrBlank()) {
                optionsBuilder.setProjectId(projectId)
            }

            return FirebaseApp.initializeApp(optionsBuilder.build(), SERVER_FIREBASE_APP_NAME)
        }
    }
}

private interface AppCheckTokenVerifier {
    fun verify(token: String)
}

private class FirebaseAppCheckJwtVerifier(
    private val projectNumber: String,
) : AppCheckTokenVerifier {
    private val issuer = "https://firebaseappcheck.googleapis.com/$projectNumber"
    private val expectedAudience = "projects/$projectNumber"
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val cachedJwkSet = AtomicReference<CachedJwkSet?>()

    override fun verify(token: String) {
        val jwt = runCatching { SignedJWT.parse(token) }.getOrElse { error ->
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token is not a valid JWT",
                cause = error,
            )
        }

        if (jwt.header.algorithm != JWSAlgorithm.RS256) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token must use RS256",
            )
        }

        val keyId = jwt.header.keyID
        if (keyId.isNullOrBlank()) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token is missing key id",
            )
        }

        val jwkSet = resolveJwkSet()
        val rsaKey = jwkSet.getKeyByKeyId(keyId) as? RSAKey
            ?: throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "No matching App Check public key",
            )
        val verifier = RSASSAVerifier(rsaKey.toRSAPublicKey())

        val signatureValid = runCatching { jwt.verify(verifier) }.getOrDefault(false)
        if (!signatureValid) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token signature is invalid",
            )
        }

        val claims = jwt.jwtClaimsSet
        val now = Date()
        val expiresAt = claims.expirationTime
        if (expiresAt == null || expiresAt.before(now)) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token is expired",
            )
        }
        val issuedAt = claims.issueTime
        if (issuedAt != null && issuedAt.after(now)) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token issue time is in the future",
            )
        }

        if (claims.issuer != issuer) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token issuer is invalid",
            )
        }

        if (!claims.audience.contains(expectedAudience)) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token audience is invalid",
            )
        }

        if (claims.subject.isNullOrBlank()) {
            throw ForbiddenRequestSecurityException(
                errorCode = "invalid_app_check",
                message = "App Check token subject is missing",
            )
        }
    }

    private fun resolveJwkSet(): JWKSet {
        val nowMillis = System.currentTimeMillis()
        cachedJwkSet.get()?.let { cached ->
            if (cached.expiresAtMillis > nowMillis) {
                return cached.jwkSet
            }
        }

        synchronized(this) {
            cachedJwkSet.get()?.let { cached ->
                if (cached.expiresAtMillis > nowMillis) {
                    return cached.jwkSet
                }
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(APP_CHECK_JWKS_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw ForbiddenRequestSecurityException(
                    errorCode = "security_unavailable",
                    message = "Could not fetch App Check public keys",
                )
            }

            val parsedJwkSet = runCatching { JWKSet.parse(response.body()) }.getOrElse { error ->
                throw ForbiddenRequestSecurityException(
                    errorCode = "security_unavailable",
                    message = "App Check public keys response is invalid",
                    cause = error,
                )
            }
            val ttlMillis = parseCacheMaxAgeMillis(response.headers().firstValue("Cache-Control").orElse(null))
            cachedJwkSet.set(
                CachedJwkSet(
                    jwkSet = parsedJwkSet,
                    expiresAtMillis = System.currentTimeMillis() + ttlMillis,
                )
            )
            return parsedJwkSet
        }
    }

    private fun parseCacheMaxAgeMillis(cacheControl: String?): Long {
        if (cacheControl.isNullOrBlank()) return Duration.ofHours(6).toMillis()
        val maxAgeSegment = cacheControl
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("max-age=", ignoreCase = true) }
            ?: return Duration.ofHours(6).toMillis()
        val seconds = maxAgeSegment.substringAfter("max-age=", "").toLongOrNull()
            ?: return Duration.ofHours(6).toMillis()
        return Duration.ofSeconds(seconds).toMillis()
    }
}

private data class CachedJwkSet(
    val jwkSet: JWKSet,
    val expiresAtMillis: Long,
)

private fun extractBearerToken(authorizationHeader: String?): String? {
    if (authorizationHeader.isNullOrBlank()) return null
    val parts = authorizationHeader.trim().split(" ", limit = 2)
    if (parts.size != 2) return null
    if (!parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1].trim().ifBlank { null }
}
