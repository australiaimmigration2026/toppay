package com.toppay.app

import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ToppayNotificationListener : NotificationListenerService() {
  override fun onListenerConnected() {
    super.onListenerConnected()
    activeNotifications
      ?.sortedBy { it.postTime }
      ?.takeLast(10)
      ?.forEach(::recordNotification)
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    requestRebind(ComponentName(this, ToppayNotificationListener::class.java))
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn != null) recordNotification(sbn)
  }

  private fun shouldCaptureNotification(packageName: String): Boolean {
    return packageName != this.packageName
  }

  private fun extractNotificationText(extras: android.os.Bundle): Pair<String, String> {
    val title = extras.getCharSequence("android.title")?.toString()?.trim()?.take(200) ?: ""
    val text = extras.getCharSequence("android.text")?.toString()?.trim()?.take(1000)
      ?: extras.getCharSequence("android.bigText")?.toString()?.trim()?.take(1000)
      ?: ""
    return title to text
  }

  private fun recordNotification(sbn: StatusBarNotification) {
    if (!shouldCaptureNotification(sbn.packageName)) return

    val extras = sbn.notification.extras
    val (title, text) = extractNotificationText(extras)
    val normalizedText = text.trim()

    val notificationId = UUID.nameUUIDFromBytes("${sbn.key}:${sbn.postTime}".toByteArray()).toString()
    val item = JSONObject().apply {
      put("id", notificationId)
      put("packageName", sbn.packageName)
      put("title", title)
      put("text", normalizedText)
      put("postedAt", sbn.postTime)
    }

    synchronized(lock) {
      val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
      val owner = preferences.getString(OWNER, null) ?: return
      item.put("uid", owner)
      val pending = JSONArray(preferences.getString(PENDING, "[]"))
      val latest = JSONArray()
      for (index in 0 until pending.length()) {
        val existing = pending.getJSONObject(index)
        if (existing.optString("id") == notificationId) continue
        latest.put(existing)
      }
      latest.put(item)
      val capped = JSONArray()
      for (index in maxOf(0, latest.length() - 10) until latest.length()) {
        capped.put(latest.get(index))
      }
      preferences.edit().putString(PENDING, capped.toString()).commit()
    }

    val work = OneTimeWorkRequestBuilder<NotificationUploadWork>()
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      work.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }
    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
      "TOPPAY_NOTIFICATION_UPLOAD",
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      work.build()
    )
  }

  companion object {
    const val PREFS = "toppay_notification_listener"
    const val PENDING = "pending"
    const val OWNER = "owner"
    val lock = Any()
  }
}
