package com.kire.remotecontroller.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Plain SharedPreferences — EncryptedSharedPreferences caused startup crashes on some
 * devices when the master key or ciphertext was invalidated after app restarts.
 */
class DeviceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDevice(host: String, name: String, philipsUser: String?, philipsPass: String?, atvPaired: Boolean) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_NAME, name)
            .putString(KEY_PHILIPS_USER, philipsUser)
            .putString(KEY_PHILIPS_PASS, philipsPass)
            .putBoolean(KEY_ATV_PAIRED, atvPaired)
            .apply()
    }

    fun getHost(): String? = prefs.getString(KEY_HOST, null)
    fun getName(): String? = prefs.getString(KEY_NAME, null)
    fun getPhilipsUser(): String? = prefs.getString(KEY_PHILIPS_USER, null)
    fun getPhilipsPass(): String? = prefs.getString(KEY_PHILIPS_PASS, null)
    fun isAtvPaired(): Boolean = prefs.getBoolean(KEY_ATV_PAIRED, false)

    fun getXmlTvUrl(): String? {
        val stored = prefs.getString(KEY_XMLTV_URL, null) ?: return null
        if (stored.contains("epgshare", ignoreCase = true) || stored.endsWith(".gz", ignoreCase = true)) {
            prefs.edit().remove(KEY_XMLTV_URL).apply()
            return null
        }
        return stored
    }

    fun setXmlTvUrl(url: String) = prefs.edit().putString(KEY_XMLTV_URL, url).apply()

    fun clearPairing() {
        prefs.edit()
            .remove(KEY_PHILIPS_USER)
            .remove(KEY_PHILIPS_PASS)
            .putBoolean(KEY_ATV_PAIRED, false)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "DeviceStore"
        private const val PREFS_NAME = "tv_devices"
        private const val KEY_HOST = "host"
        private const val KEY_NAME = "name"
        private const val KEY_PHILIPS_USER = "philips_user"
        private const val KEY_PHILIPS_PASS = "philips_pass"
        private const val KEY_ATV_PAIRED = "atv_paired"
        private const val KEY_XMLTV_URL = "xmltv_url"

        /** One-time migration from old encrypted prefs file if present. */
        fun migrateLegacyEncryptedPrefsIfNeeded(context: Context) {
            val appContext = context.applicationContext
            val plain = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!plain.getString(KEY_HOST, null).isNullOrBlank()) return
            runCatching {
                val masterKeyAlias = androidx.security.crypto.MasterKeys
                    .getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
                val encrypted = androidx.security.crypto.EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    appContext,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                val editor = plain.edit()
                encrypted.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                    }
                }
                editor.apply()
                Log.i(TAG, "Migrated legacy encrypted prefs to plain storage")
            }.onFailure {
                Log.w(TAG, "No legacy encrypted prefs to migrate", it)
            }
        }
    }
}
