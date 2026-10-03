package com.yourname.mymoney.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class DriveApiException(val statusCode: Int, message: String) : Exception(message)

data class DriveFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: Long
)

object DriveRestClient {

    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    const val BACKUP_FILE_NAME = "mymoney_backup.json"
    private const val BOUNDARY = "mymoney_drive_backup_boundary"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun listBackupFiles(accessToken: String): List<DriveFileInfo> = withContext(Dispatchers.IO) {
        val url = FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$BACKUP_FILE_NAME'")
            .addQueryParameter("fields", "files(id,name,size,modifiedTime)")
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, errorMessage(response.code, body))
            }
            val files = JSONObject(body ?: "{}").optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<DriveFileInfo>()
            for (i in 0 until files.length()) {
                val fileObj = files.optJSONObject(i) ?: continue
                val id = fileObj.optString("id")
                if (id.isNotBlank()) {
                    val size = fileObj.optLong("size", fileObj.optString("size", "0").toLongOrNull() ?: 0L)
                    val modified = parseRfc3339(fileObj.optString("modifiedTime"))
                    result.add(
                        DriveFileInfo(
                            id = id,
                            name = fileObj.optString("name", BACKUP_FILE_NAME),
                            sizeBytes = size,
                            modifiedTime = modified
                        )
                    )
                }
            }
            result
        }
    }

    suspend fun uploadBackupFile(accessToken: String, json: String): DriveFileInfo = withContext(Dispatchers.IO) {
        val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put("appDataFolder"))

        val bodyText = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json).append("\r\n")
            append("--$BOUNDARY--\r\n")
        }

        val url = "$UPLOAD_URL?uploadType=multipart&fields=id,name,size,modifiedTime"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(bodyText.toRequestBody("multipart/related; boundary=$BOUNDARY".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, errorMessage(response.code, body))
            }
            val jsonResponse = JSONObject(body ?: "{}")
            val id = jsonResponse.optString("id")
            if (id.isBlank()) {
                throw DriveApiException(response.code, "Google Drive accepted the upload but returned no file id.")
            }
            val sizeRaw = jsonResponse.optLong("size", jsonResponse.optString("size", "0").toLongOrNull() ?: 0L)
            val computedSize = if (sizeRaw > 0L) sizeRaw else json.toByteArray(Charsets.UTF_8).size.toLong()
            val modified = parseRfc3339(jsonResponse.optString("modifiedTime"))

            DriveFileInfo(
                id = id,
                name = jsonResponse.optString("name", BACKUP_FILE_NAME),
                sizeBytes = computedSize,
                modifiedTime = modified
            )
        }
    }

    suspend fun deleteFile(accessToken: String, fileId: String) = withContext(Dispatchers.IO) {
        val url = "$FILES_URL/$fileId".toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                val body = response.body?.string()
                throw DriveApiException(response.code, errorMessage(response.code, body))
            }
        }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val url = "$FILES_URL/$fileId?alt=media".toHttpUrl()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    throw DriveApiException(response.code, errorMessage(response.code, body))
                }
                body ?: ""
            }
        }

    private fun parseRfc3339(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun errorMessage(statusCode: Int, body: String?): String {
        val apiMessage = try {
            val message = JSONObject(body ?: "").optJSONObject("error")?.optString("message")
            if (message.isNullOrBlank()) null else message
        } catch (e: Exception) {
            null
        }
        if (apiMessage != null) return "$apiMessage (HTTP $statusCode)"
        return when (statusCode) {
            401 -> "Google Drive access expired or was revoked (HTTP 401). Please sign in again."
            403 -> "Google Drive denied the request (HTTP 403). The app only requests the drive.appdata scope."
            404 -> "The backup file was not found in Google Drive (HTTP 404)."
            else -> "Google Drive request failed with HTTP $statusCode."
        }
    }
}
