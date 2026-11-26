package com.pokerlanka.pokie_talkie.fcm

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.R
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class FcmV1Client(
    private val context: Context,
    private val gson: Gson = Gson()
) {

    private val tag = "FcmV1Client"

    companion object {
        // FCM rejects data payloads over 4096 bytes; keep some margin
        private const val FCM_DATA_LIMIT_BYTES = 4000
    }

    private var projectId: String = ""
    private var clientEmail: String = ""
    private var privateKeyPem: String = ""

    private var cachedAccessToken: String? = null
    private var tokenExpiryTimeMs: Long = 0L

    init {
        loadServiceAccount()
    }

    fun isConfigured(): Boolean {
        return projectId.isNotBlank() && clientEmail.isNotBlank() && privateKeyPem.isNotBlank()
    }

    private fun loadServiceAccount() {
        try {
            val resId = context.resources.getIdentifier(
                "firebase_service_account",
                "raw",
                context.packageName
            )
            if (resId == 0) {
                Log.w(tag, "firebase_service_account.json not bundled in res/raw; FCM v1 push disabled")
                return
            }
            val inputStream = context.resources.openRawResource(resId)
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            projectId = json.optString("project_id", "")
            clientEmail = json.optString("client_email", "")
            privateKeyPem = json.optString("private_key", "")
            Log.i(tag, "Loaded FCM Service Account for project: $projectId, client: $clientEmail")
        } catch (e: Exception) {
            Log.w(tag, "Could not load firebase_service_account.json from res/raw: ${e.message}")
        }
    }

    private val tokenMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun getAccessToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedAccessToken != null && now < tokenExpiryTimeMs - 60_000L) {
            return cachedAccessToken
        }

        return tokenMutex.withLock {
            val checkAgain = cachedAccessToken
            if (checkAgain != null && System.currentTimeMillis() < tokenExpiryTimeMs - 60_000L) {
                return@withLock checkAgain
            }

            withContext(Dispatchers.IO) {
            try {
                val nowSeconds = System.currentTimeMillis() / 1000L
                val expSeconds = nowSeconds + 3600L

                // 1. JWT Header
                val headerJson = JSONObject().apply {
                    put("alg", "RS256")
                    put("typ", "JWT")
                }.toString()
                val headerEncoded = base64Url(headerJson.toByteArray(StandardCharsets.UTF_8))

                // 2. JWT Claims
                val claimsJson = JSONObject().apply {
                    put("iss", clientEmail)
                    put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                    put("aud", "https://oauth2.googleapis.com/token")
                    put("iat", nowSeconds)
                    put("exp", expSeconds)
                }.toString()
                val claimsEncoded = base64Url(claimsJson.toByteArray(StandardCharsets.UTF_8))

                val unsignedToken = "$headerEncoded.$claimsEncoded"

                // 3. Sign with RSA private key
                val cleanedKey = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim()
                val keyBytes = Base64.decode(cleanedKey, Base64.DEFAULT)
                val keySpec = PKCS8EncodedKeySpec(keyBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                val rsaPrivateKey = keyFactory.generatePrivate(keySpec)

                val signature = Signature.getInstance("SHA256withRSA")
                signature.initSign(rsaPrivateKey)
                signature.update(unsignedToken.toByteArray(StandardCharsets.UTF_8))
                val signedBytes = signature.sign()
                val signatureEncoded = base64Url(signedBytes)

                val jwtAssertion = "$unsignedToken.$signatureEncoded"

                // 4. Request OAuth2 token from Google
                val tokenUrl = URL("https://oauth2.googleapis.com/token")
                val conn = (tokenUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val postData = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwtAssertion"
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(postData) }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val respObj = JSONObject(responseStr)
                    val token = respObj.getString("access_token")
                    val expiresIn = respObj.optLong("expires_in", 3600L)
                    cachedAccessToken = token
                    tokenExpiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000L)
                    Log.d(tag, "Successfully refreshed Google OAuth2 token for FCM (expires in ${expiresIn}s)")
                    token
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(tag, "OAuth2 token request failed (HTTP $responseCode): $err")
                    null
                }
            } catch (e: Exception) {
                Log.e(tag, "Error generating OAuth2 token for FCM: ${e.message}", e)
                null
            }
        }
    }
}

    suspend fun sendPush(targetFcmToken: String, payload: SignalingPayload): Boolean {
        if (targetFcmToken.isBlank() || !isConfigured()) return false

        val accessToken = getAccessToken() ?: return false

        // Older app versions can't read compressed SDP, so only compress when the payload is over FCM's limit
        var dataObj = buildDataObject(payload)
        if (dataSizeBytes(dataObj) > FCM_DATA_LIMIT_BYTES && payload.sdp != null) {
            dataObj = buildDataObject(payload.copy(sdp = SdpCompressor.compress(payload.sdp)))
        }
        val dataSize = dataSizeBytes(dataObj)
        if (dataSize > FCM_DATA_LIMIT_BYTES) {
            Log.e(tag, "FCM payload too large ($dataSize bytes > $FCM_DATA_LIMIT_BYTES) for type=${payload.type}, not sending")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val fcmUrl = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                val conn = (fcmUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; UTF-8")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val messageObj = JSONObject().apply {
                    put("token", targetFcmToken)
                    put("data", dataObj)
                    val androidConfig = JSONObject().apply {
                        put("priority", "HIGH")
                        put("ttl", "120s")
                    }
                    put("android", androidConfig)
                }

                val rootObj = JSONObject().apply {
                    put("message", messageObj)
                }

                val postBody = rootObj.toString()
                Log.d(tag, "FCM data size: $dataSize bytes (type: ${payload.type}, callId: ${payload.callId})")

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(postBody) }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.i(tag, "FCM v1 push sent successfully (HTTP $responseCode) for type=${payload.type}")
                    true
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(tag, "FCM v1 push failed (HTTP $responseCode) for type=${payload.type}: $err")
                    false
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception sending FCM v1 push to $targetFcmToken: ${e.message}", e)
                false
            }
        }
    }

    private fun buildDataObject(payload: SignalingPayload): JSONObject {
        return JSONObject().apply {
            put("payload", gson.toJson(payload))
            put("type", payload.type.name)
            put("senderId", payload.senderId)
            put("senderName", payload.senderName)
            payload.callId?.let { put("callId", it) }
            payload.callType?.let { put("callType", it.name) }
        }
    }

    /** FCM counts the UTF-8 bytes of every data key and value towards its 4KB limit. */
    private fun dataSizeBytes(dataObj: JSONObject): Int {
        var size = 0
        for (key in dataObj.keys()) {
            size += key.toByteArray(StandardCharsets.UTF_8).size
            size += dataObj.optString(key).toByteArray(StandardCharsets.UTF_8).size
        }
        return size
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
