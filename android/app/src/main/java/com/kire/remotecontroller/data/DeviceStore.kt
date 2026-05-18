package com.kire.remotecontroller.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class DeviceStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

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

    companion object {
        private const val TAG = "DeviceStore"
        private const val PREFS_NAME = "tv_devices"
        private const val KEY_HOST = "host"
        private const val KEY_NAME = "name"
        private const val KEY_PHILIPS_USER = "philips_user"
        private const val KEY_PHILIPS_PASS = "philips_pass"
        private const val KEY_ATV_PAIRED = "atv_paired"
        private const val KEY_XMLTV_URL = "xmltv_url"

        private fun createPrefs(context: Context): SharedPreferences {
            return runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse { error ->
                Log.w(TAG, "Encrypted prefs unavailable, using plain prefs", error)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
