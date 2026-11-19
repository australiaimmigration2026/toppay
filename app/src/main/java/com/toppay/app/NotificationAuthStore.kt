package com.toppay.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object NotificationAuthStore {
  private const val KEY_ALIAS = "toppay_notification_refresh_token"
  private const val TOKEN_KEY = "encrypted_refresh_token"

  fun save(context: Context, token: String?) {
    val preferences = context.getSharedPreferences(ToppayNotificationListener.PREFS, Context.MODE_PRIVATE)
    if (token.isNullOrBlank()) {
      preferences.edit().remove(TOKEN_KEY).commit()
      return
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
  }

  fun read(context: Context): String? {
    val encoded = context.getSharedPreferences(ToppayNotificationListener.PREFS, Context.MODE_PRIVATE)
      .getString(TOKEN_KEY, null) ?: return null
    return try {
      val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
      String(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), Charsets.UTF_8)
    } catch (_: Exception) {
      null
    }
  }

  private fun key(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
      init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build())
      generateKey()
    }
  }
}
