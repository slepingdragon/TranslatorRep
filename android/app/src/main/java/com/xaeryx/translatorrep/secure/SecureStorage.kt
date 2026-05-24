package com.xaeryx.translatorrep.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Thin wrapper over [EncryptedSharedPreferences] (`androidx.security:security-crypto`) for
 * at-rest-encrypted local secrets. First used by Story 1.12 to hold the X25519 identity
 * **private** key (ADR-A2: never plaintext, never networked); reusable for later secrets.
 *
 * Values are stored as Base64 strings (EncryptedSharedPreferences is a string store). The
 * underlying [SharedPreferences] is created lazily — the AndroidKeyStore master-key setup +
 * file open should happen off the main thread (callers access this from a background scope).
 */
class SecureStorage(
    private val context: Context,
    private val fileName: String = DEFAULT_FILE_NAME,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The raw bytes stored under [key], or `null` if absent. */
    fun getBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    /** Store [bytes] under [key] (encrypted at rest). */
    fun putBytes(key: String, bytes: ByteArray) {
        prefs.edit().putString(key, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    /** Remove the value at [key]. */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "translatorrep-secure"
    }
}
