package com.yourname.mymoney.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.yourname.mymoney.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DriveAuthOutcome {
    data class Granted(val accessToken: String) : DriveAuthOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome()
    data class Failed(val message: String) : DriveAuthOutcome()
}

enum class AutoBackupInterval(val days: Long, val key: String) {
    OFF(0, "OFF"),
    DAILY(1, "DAILY"),
    DAYS_3(3, "DAYS_3"),
    DAYS_7(7, "DAYS_7"),
    DAYS_14(14, "DAYS_14"),
    MONTHLY(30, "MONTHLY");

    companion object {
        fun fromKey(key: String?): AutoBackupInterval =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: OFF
    }
}

class GoogleDriveBackupManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("google_drive_backup_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_BACKUP = "key_last_backup_timestamp"
        private const val KEY_BACKUP_SIZE = "key_backup_size_bytes"
        private const val KEY_LAST_ERROR = "key_last_backup_error"
        private const val KEY_AUTO_BACKUP_INTERVAL = "key_auto_backup_interval"
        private const val KEY_ACCOUNT_EMAIL = "key_drive_account_email"
        private const val KEY_APPDATA_BACKUP = "key_appdata_backup_content"
        private const val KEY_DRIVE_FILE_ID = "key_drive_file_id"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    var backupSizeBytes: Long
        get() = prefs.getLong(KEY_BACKUP_SIZE, 0L)
        set(value) = prefs.edit().putLong(KEY_BACKUP_SIZE, value).apply()

    var lastBackupError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    var autoBackupInterval: AutoBackupInterval
        get() = AutoBackupInterval.fromKey(prefs.getString(KEY_AUTO_BACKUP_INTERVAL, AutoBackupInterval.OFF.key))
        set(value) = prefs.edit().putString(KEY_AUTO_BACKUP_INTERVAL, value.key).apply()

    // Backward-compatible boolean toggle
    var isAutoBackupEnabled: Boolean
        get() = autoBackupInterval != AutoBackupInterval.OFF
        set(enabled) {
            autoBackupInterval = if (enabled) AutoBackupInterval.DAILY else AutoBackupInterval.OFF
        }

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()

    // Local mirror of the JSON last written to Drive, kept for the empty-backup guard.
    var cachedAppDataBackup: String?
        get() = prefs.getString(KEY_APPDATA_BACKUP, null)
        set(value) = prefs.edit().putString(KEY_APPDATA_BACKUP, value).apply()

    private var driveFileId: String?
        get() = prefs.getString(KEY_DRIVE_FILE_ID, null)
        set(value) = prefs.edit().putString(KEY_DRIVE_FILE_ID, value).apply()

    fun getFormattedLastBackup(): String {
        val ts = lastBackupTimestamp
        if (ts <= 0L) return "Never"
        val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    fun getFormattedBackupSize(): String {
        val bytes = backupSizeBytes
        if (bytes <= 0L) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun storedBackupHasRecords(): Boolean {
        val stored = cachedAppDataBackup
        if (stored.isNullOrBlank()) return false
        return try {
            DataBackupService.parseBackupJson(stored).hasRecords()
        } catch (e: Exception) {
            // Unreadable backup: assume it holds data so it is never overwritten by an empty one.
            true
        }
    }

    fun signOut() {
        accountEmail = null
        driveFileId = null
        cachedAppDataBackup = null
        lastBackupError = null
        autoBackupInterval = AutoBackupInterval.OFF
    }

    suspend fun signInWithGoogle(): Result<String> {
        val serverClientId = appContext.getString(R.string.google_web_client_id)
        if (serverClientId.isBlank() || serverClientId.startsWith("REPLACE_WITH")) {
            return Result.failure(
                Exception(
                    "Google sign-in is not configured: set google_web_client_id in res/values/strings.xml " +
                        "to the OAuth Web Client ID from Google Cloud Console."
                )
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = CredentialManager.create(appContext)
                .getCredential(request = request, context = appContext)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(Exception("Google sign-in returned an unexpected credential type."))
            }
            val idCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val email = emailFromIdToken(idCredential.idToken) ?: idCredential.id
            if (email.isBlank()) {
                return Result.failure(Exception("Google sign-in succeeded but returned no account email."))
            }
            accountEmail = email
            driveFileId = null
            lastBackupError = null
            Result.success(email)
        } catch (e: GetCredentialException) {
            val msg = credentialErrorMessage(e)
            lastBackupError = msg
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            val msg = e.message ?: "Google sign-in failed."
            lastBackupError = msg
            Result.failure(Exception(msg))
        }
    }

    suspend fun authorizeDrive(): DriveAuthOutcome = withContext(Dispatchers.IO) {
        try {
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
                .build()
            val result = Identity.getAuthorizationClient(appContext)
                .authorize(request)
                .await()
            toAuthOutcome(result)
        } catch (e: Exception) {
            val msg = e.message ?: "Google Drive authorization failed."
            lastBackupError = msg
            DriveAuthOutcome.Failed(msg)
        }
    }

    fun completeAuthorization(resultCode: Int, data: Intent?): DriveAuthOutcome {
        if (resultCode != Activity.RESULT_OK || data == null) {
            val msg = "Google Drive access was declined. The app needs the drive.appdata scope to back up."
            lastBackupError = msg
            return DriveAuthOutcome.Failed(msg)
        }
        return try {
            val result = Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
            toAuthOutcome(result)
        } catch (e: Exception) {
            val msg = e.message ?: "Google Drive authorization failed."
            lastBackupError = msg
            DriveAuthOutcome.Failed(msg)
        }
    }

    /**
     * Uploads the backup atomically to Google Drive appDataFolder:
     * 1. Uploads new file to appDataFolder first and verifies success.
     * 2. After confirmed upload, deletes previous backup files so exactly one file remains.
     * Never deletes the old backup if the new upload fails.
     */
    suspend fun uploadBackup(accessToken: String, backupJson: String): Result<DriveFileInfo> {
        if (accountEmail.isNullOrBlank()) {
            val msg = "Sign-in required: Please sign in with your Google account to back up to Google Drive."
            lastBackupError = msg
            return Result.failure(Exception(msg))
        }
        return try {
            // 1. Upload the new backup file first
            val newFile = DriveRestClient.uploadBackupFile(accessToken, backupJson)

            // 2. Query any existing backup files in appDataFolder and delete the older ones
            try {
                val existingFiles = DriveRestClient.listBackupFiles(accessToken)
                for (file in existingFiles) {
                    if (file.id != newFile.id) {
                        try {
                            DriveRestClient.deleteFile(accessToken, file.id)
                        } catch (_: Exception) {
                            // Non-fatal: new backup is already safe
                        }
                    }
                }
            } catch (_: Exception) {
                // Listing/deleting old files error does not compromise the new upload
            }

            // 3. Update local state
            driveFileId = newFile.id
            cachedAppDataBackup = backupJson
            lastBackupTimestamp = if (newFile.modifiedTime > 0L) newFile.modifiedTime else System.currentTimeMillis()
            backupSizeBytes = newFile.sizeBytes
            lastBackupError = null

            Result.success(newFile)
        } catch (e: Exception) {
            val msg = e.message ?: "Backup to Google Drive failed."
            lastBackupError = msg
            Result.failure(Exception(msg))
        }
    }

    suspend fun downloadBackup(accessToken: String): Result<Pair<String, DriveFileInfo>> {
        if (accountEmail.isNullOrBlank()) {
            val msg = "Sign-in required: Please sign in with your Google account to restore from Google Drive."
            lastBackupError = msg
            return Result.failure(Exception(msg))
        }
        return try {
            val files = DriveRestClient.listBackupFiles(accessToken)
            if (files.isEmpty()) {
                val msg = "No backup found in Google Drive appDataFolder. Please perform a backup first."
                return Result.failure(Exception(msg))
            }

            // Pick the latest backup file
            val latestFile = files.maxByOrNull { it.modifiedTime } ?: files.first()
            val json = DriveRestClient.downloadBackup(accessToken, latestFile.id)
            if (json.isBlank()) {
                return Result.failure(Exception("The backup file in Google Drive is empty."))
            }

            driveFileId = latestFile.id
            backupSizeBytes = latestFile.sizeBytes
            lastBackupTimestamp = latestFile.modifiedTime
            lastBackupError = null

            Result.success(json to latestFile)
        } catch (e: Exception) {
            val msg = e.message ?: "Restore from Google Drive failed."
            lastBackupError = msg
            Result.failure(Exception(msg))
        }
    }

    private fun toAuthOutcome(result: AuthorizationResult): DriveAuthOutcome {
        val pendingIntent = if (result.hasResolution()) result.pendingIntent else null
        if (pendingIntent != null) {
            return DriveAuthOutcome.NeedsResolution(pendingIntent)
        }
        val token = result.accessToken
        return if (token.isNullOrBlank()) {
            DriveAuthOutcome.Failed("Google Drive access was not granted.")
        } else {
            DriveAuthOutcome.Granted(token)
        }
    }

    private fun emailFromIdToken(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JSONObject(payload).optString("email").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun credentialErrorMessage(e: GetCredentialException): String = when (e) {
        is NoCredentialException ->
            "No Google account is available on this device. Add a Google account in system settings, then try again."
        is GetCredentialCancellationException -> "Google sign-in was cancelled."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed."
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resumeWith(Result.success(it)) }
        addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
    }
}
