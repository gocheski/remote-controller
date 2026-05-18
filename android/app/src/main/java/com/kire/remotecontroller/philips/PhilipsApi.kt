package com.kire.remotecontroller.philips

import android.util.Base64
import com.burgstaller.okhttp.digest.Credentials as DigestCredentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class PhilipsApi(
    private val host: String,
    private var deviceId: String? = null,
    private var authKey: String? = null,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val unauthenticatedClient: OkHttpClient = createBaseClient()

    val isPaired: Boolean get() = !deviceId.isNullOrBlank() && !authKey.isNullOrBlank()

    fun setCredentials(deviceId: String, authKey: String) {
        this.deviceId = deviceId
        this.authKey = authKey
    }

    suspend fun pairRequest(): PairingChallenge = withContext(Dispatchers.IO) {
        val id = deviceId ?: createDeviceId()
        deviceId = id
        val body = JSONObject()
            .put("scope", JSONArray(listOf("read", "write", "control")))
            .put("device", deviceJson(id))
        val response = post(unauthenticatedClient, "6/pair/request", body)
        val json = JSONObject(response)
        if (json.optString("error_id", "SUCCESS") != "SUCCESS") {
            error("Pair request failed: ${json.optString("error_text", json.toString())}")
        }
        PairingChallenge(
            deviceId = id,
            timestamp = json.getInt("timestamp"),
            authKey = json.getString("auth_key"),
        )
    }

    suspend fun pairGrant(pin: String, challenge: PairingChallenge): String = withContext(Dispatchers.IO) {
        val trimmedPin = pin.trim()
        val signature = createSignature(challenge.timestamp, trimmedPin)
        val auth = JSONObject()
            .put("auth_AppId", "1")
            .put("pin", trimmedPin)
            .put("auth_timestamp", challenge.timestamp)
            .put("auth_signature", signature)
        val body = JSONObject()
            .put("auth", auth)
            .put("device", deviceJson(challenge.deviceId))
        val digestClient = createDigestClient(challenge.deviceId, challenge.authKey)
        val response = post(digestClient, "6/pair/grant", body)
        val json = JSONObject(response)
        if (json.optString("error_id", "SUCCESS") != "SUCCESS") {
            error(
                "Pairing failed: ${json.optString("error_text", json.optString("detail", json.toString()))}",
            )
        }
        authKey = challenge.authKey
        challenge.authKey
    }

    suspend fun sendKey(key: String) = withContext(Dispatchers.IO) {
        post(requireDigestClient(), "6/input/key", JSONObject().put("key", key))
    }

    suspend fun getCurrentTv(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(get(requireDigestClient(), "6/activities/tv"))
    }

    suspend fun getSystem(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(get(requireDigestClient(), "6/system"))
    }

    suspend fun launchYouTube() = withContext(Dispatchers.IO) {
        val body = JSONObject(
            """
            {
              "id": "youtube",
              "order": 0,
              "intent": {
                "action": "android.intent.action.MAIN",
                "component": {
                  "packageName": "com.google.android.youtube.tv",
                  "className": "com.google.android.apps.youtube.tv.activity.ShellActivity"
                }
              },
              "label": "YouTube"
            }
            """.trimIndent(),
        )
        post(requireDigestClient(), "6/activities/launch", body)
    }

    suspend fun toggleAmbilight() = sendKey("AmbilightOnOff")

    suspend fun ambilightOn() = post(requireDigestClient(), "6/ambilight/power", JSONObject().put("power", "On"))

    suspend fun ambilightOff() = post(requireDigestClient(), "6/ambilight/power", JSONObject().put("power", "Off"))

    suspend fun listChannelsRaw(): String = get(requireDigestClient(), "6/channeldb/tv/channelLists/all")

    suspend fun probePath(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { get(requireDigestClient(), "6/$path") }.getOrNull()
    }

    private fun deviceJson(id: String): JSONObject =
        JSONObject()
            .put("device_name", "Philips Remote")
            .put("device_os", "Android")
            .put("app_name", "Philips Remote")
            .put("type", "native")
            .put("app_id", APP_ID)
            .put("id", id)

    private fun createSignature(timestamp: Int, pin: String): String {
        val secret = Base64.decode(SECRET_KEY_B64, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val data = timestamp.toString().toByteArray(Charsets.UTF_8) + pin.toByteArray(Charsets.UTF_8)
        val hex = mac.doFinal(data).joinToString("") { "%02x".format(it) }
        return Base64.encodeToString(hex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun requireDigestClient(): OkHttpClient {
        val user = deviceId ?: error("Not paired")
        val pass = authKey ?: error("Not paired")
        return createDigestClient(user, pass)
    }

    private fun createDigestClient(user: String, pass: String): OkHttpClient {
        val digestAuth = DigestAuthenticator(DigestCredentials(user, pass))
        return createBaseClient().newBuilder()
            .authenticator(digestAuth)
            .build()
    }

    private fun get(client: OkHttpClient, path: String): String {
        val request = Request.Builder()
            .url("https://$host:1926/$path")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("GET $path failed: ${response.code} $text")
            }
            return text
        }
    }

    private fun post(client: OkHttpClient, path: String, body: JSONObject): String {
        val request = Request.Builder()
            .url("https://$host:1926/$path")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("POST $path failed: ${response.code} $text")
            }
            return text
        }
    }

    data class PairingChallenge(
        val deviceId: String,
        val timestamp: Int,
        val authKey: String,
    )

    companion object {
        private const val APP_ID = "app.id"
        private const val SECRET_KEY_B64 =
            "ZmVay1EQVFOaZhwQ4Kv81ypLAZNczV9sG4KkseXWn1NEk6cXmPKO/MCa9sryslvLCFMnNe4Z4CPXzToowvhHvA=="
        private const val DEVICE_ID_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        private fun createDeviceId(): String {
            val random = SecureRandom()
            return buildString(16) {
                repeat(16) {
                    append(DEVICE_ID_CHARS[random.nextInt(DEVICE_ID_CHARS.length)])
                }
            }
        }

        private fun createBaseClient(): OkHttpClient {
            val trustAll = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                },
            )
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAll, SecureRandom())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
