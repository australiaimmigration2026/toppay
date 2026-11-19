package com.toppay.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import org.json.JSONArray

class ToppayNotificationPackage : ReactPackage {
  override fun createNativeModules(context: ReactApplicationContext) = listOf(ToppayNotificationModule(context))
  override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

class ToppayNotificationModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = "ToppayNotificationQueue"

  @ReactMethod
  fun hasAccess(promise: Promise) {
    try {
      val component = ComponentName(context, ToppayNotificationListener::class.java)
      val allowed = if (Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
      } else {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
      }
      promise.resolve(allowed)
    } catch (error: Exception) {
      promise.reject("ACCESS_CHECK_FAILED", error)
    }
  }

  @ReactMethod
  fun openSettings(promise: Promise) {
    try {
      context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      promise.resolve(null)
    } catch (_: Exception) {
      try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        promise.resolve(null)
      } catch (error: Exception) {
        promise.reject("SETTINGS_UNAVAILABLE", error)
      }
    }
  }

  @ReactMethod
  fun setOwner(uid: String?, refreshToken: String?, promise: Promise) {
    try {
      synchronized(ToppayNotificationListener.lock) {
        val preferences = context.getSharedPreferences(ToppayNotificationListener.PREFS, 0)
        if (preferences.getString(ToppayNotificationListener.OWNER, null) != uid) {
          preferences.edit().putString(ToppayNotificationListener.PENDING, "[]").commit()
        }
        NotificationAuthStore.save(context, if (uid == null) null else refreshToken)
        preferences.edit().putString(ToppayNotificationListener.OWNER, uid).commit()
      }
      promise.resolve(null)
    } catch (error: Exception) {
      promise.reject("OWNER_UPDATE_FAILED", error)
    }
  }

  @ReactMethod
  fun getPending(promise: Promise) {
    synchronized(ToppayNotificationListener.lock) {
      val raw = context.getSharedPreferences(ToppayNotificationListener.PREFS, 0)
        .getString(ToppayNotificationListener.PENDING, "[]") ?: "[]"
      promise.resolve(raw)
    }
  }

  @ReactMethod
  fun acknowledge(id: String, promise: Promise) {
    synchronized(ToppayNotificationListener.lock) {
      val preferences = context.getSharedPreferences(ToppayNotificationListener.PREFS, 0)
      val queue = JSONArray(preferences.getString(ToppayNotificationListener.PENDING, "[]"))
      val remaining = JSONArray()
      for (index in 0 until queue.length()) {
        val item = queue.getJSONObject(index)
        if (item.optString("id") != id) remaining.put(item)
      }
      preferences.edit().putString(ToppayNotificationListener.PENDING, remaining.toString()).commit()
      promise.resolve(null)
    }
  }
}
