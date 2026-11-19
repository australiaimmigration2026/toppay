package com.toppay.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationUploadWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      uploadPending()
      Result.success()
    } catch (error: Exception) {
      Log.w("NotificationUploadWork", "Upload deferred: ${error.javaClass.simpleName}: ${error.message}")
      Result.retry()
    }
  }

  private fun uploadPending() {
    val preferences = applicationContext.getSharedPreferences(ToppayNotificationListener.PREFS, Context.MODE_PRIVATE)
    val owner: String
    val pending: JSONArray
    synchronized(ToppayNotificationListener.lock) {
      owner = preferences.getString(ToppayNotificationListener.OWNER, null) ?: return
      pending = JSONArray(preferences.getString(ToppayNotificationListener.PENDING, "[]"))
    }
    if (pending.length() == 0) return

    val refreshToken = NotificationAuthStore.read(applicationContext)
      ?: throw IOException("Notification authentication unavailable")
    val apiKey = applicationContext.getString(R.string.google_api_key)
    val projectId = applicationContext.getString(R.string.project_id)
    val tokenResponse = request(
      "POST",
      "https://securetoken.googleapis.com/v1/token?key=${encode(apiKey)}",
      "grant_type=refresh_token&refresh_token=${encode(refreshToken)}",
      "application/x-www-form-urlencoded"
    )
    val refreshed = JSONObject(tokenResponse)
    if (refreshed.optString("user_id") != owner) throw IOException("Notification owner mismatch")
    val idToken = refreshed.getString("id_token")
    refreshed.optString("refresh_token").takeIf { it.isNotBlank() && it != refreshToken }?.let {
      synchronized(ToppayNotificationListener.lock) {
        if (preferences.getString(ToppayNotificationListener.OWNER, null) == owner) {
          NotificationAuthStore.save(applicationContext, it)
        }
      }
    }

    val collectionUrl = "https://firestore.googleapis.com/v1/projects/${encode(projectId)}/databases/(default)/documents/users/${encode(owner)}/notification"
    var uploaded = 0
    val uploadedIds = mutableSetOf<String>()
    for (index in 0 until pending.length()) {
      val item = pending.getJSONObject(index)
      if (item.optString("uid") != owner) continue
      if (preferences.getString(ToppayNotificationListener.OWNER, null) != owner) return
      val id = item.optString("id")
      if (id.isBlank()) continue
      val postedAt = item.optLong("postedAt")
      val postedDate = Date(postedAt)
      val fields = JSONObject().apply {
        put("packageName", stringField(item.optString("packageName")))
        put("title", stringField(item.optString("title")))
        put("text", stringField(item.optString("text")))
        put("postedAt", JSONObject().put("integerValue", postedAt.toString()))
        put("time", stringField(SimpleDateFormat("hh:mm:ss a", Locale.US).format(postedDate)))
        put("date", stringField(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(postedDate)))
      }
      request("PATCH", "$collectionUrl/${encode(id)}", JSONObject().put("fields", fields).toString(), "application/json", idToken)
      uploaded += 1
      uploadedIds.add(id)
    }

    val listed = JSONObject(request("GET", "$collectionUrl?pageSize=100&orderBy=${encode("postedAt desc")}", authToken = idToken))
    val documents = listed.optJSONArray("documents") ?: JSONArray()
    for (index in 10 until documents.length()) {
      val name = documents.getJSONObject(index).getString("name")
      val expectedPrefix = "projects/$projectId/databases/(default)/documents/users/$owner/notification/"
      if (name.startsWith(expectedPrefix)) {
        request("DELETE", "https://firestore.googleapis.com/v1/$name", authToken = idToken)
      }
    }
    synchronized(ToppayNotificationListener.lock) {
      val latest = JSONArray(preferences.getString(ToppayNotificationListener.PENDING, "[]"))
      val remaining = JSONArray()
      for (position in 0 until latest.length()) {
        val queued = latest.getJSONObject(position)
        if (queued.optString("id") !in uploadedIds) remaining.put(queued)
      }
      preferences.edit().putString(ToppayNotificationListener.PENDING, remaining.toString()).commit()
    }
    Log.i("NotificationUploadWork", "Uploaded $uploaded notifications")
  }

  private fun stringField(value: String) = JSONObject().put("stringValue", value)

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  private fun request(method: String, url: String, body: String? = null, contentType: String? = null, authToken: String? = null): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 15_000
      readTimeout = 15_000
      if (authToken != null) setRequestProperty("Authorization", "Bearer $authToken")
      if (contentType != null) setRequestProperty("Content-Type", contentType)
      if (body != null) {
        doOutput = true
        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      }
    }
    return try {
      if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode} from $method")
      connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }
}
