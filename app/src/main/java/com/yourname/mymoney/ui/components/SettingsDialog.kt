package com.yourname.mymoney.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.yourname.mymoney.data.AutoBackupInterval
import com.yourname.mymoney.data.BackupData
import com.yourname.mymoney.data.BudgetEntity
import com.yourname.mymoney.data.CategoryBudgetStatus
import com.yourname.mymoney.data.CategoryEntity
import com.yourname.mymoney.data.DataBackupService
import com.yourname.mymoney.data.LoanWithDetails
import com.yourname.mymoney.data.TransactionEntity
import androidx.compose.ui.res.stringResource
import com.yourname.mymoney.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    currentThemeMode: String, // "SYSTEM", "LIGHT", "DARK"
    currentCurrency: AppCurrency,
    transactions: List<TransactionEntity>,
    loansWithDetails: List<LoanWithDetails>,
    budgets: List<CategoryBudgetStatus>,
    allBudgets: List<BudgetEntity>,
    categories: List<CategoryEntity>,
    isOnline: Boolean = true,
    isSimulatedOffline: Boolean = false,
    lastBackupTimeFormatted: String = "Never",
    lastBackupSizeFormatted: String = "—",
    lastBackupError: String? = null,
    autoBackupInterval: AutoBackupInterval = AutoBackupInterval.OFF,
    isAutoBackupEnabled: Boolean = false,
    googleAccountEmail: String? = null,
    onThemeModeChange: (String) -> Unit,
    onCurrencyChange: (AppCurrency) -> Unit,
    onToggleSimulatedOffline: () -> Unit,
    onGoogleSignIn: (onResult: (Result<String>) -> Unit) -> Unit,
    onDisconnectGoogleAccount: () -> Unit,
    onAutoBackupIntervalChange: (AutoBackupInterval) -> Unit = {},
    onToggleAutoBackup: (Boolean) -> Unit = {},
    onDriveBackupNow: (onComplete: (Result<String>) -> Unit) -> Unit,
    onDriveRestoreRequest: (onConfirmReady: (BackupData) -> Unit, onError: (String) -> Unit) -> Unit,
    onRestoreBackupReplace: (BackupData, (Result<Unit>) -> Unit) -> Unit,
    onRestoreBackupMerge: (BackupData, (Result<Unit>) -> Unit) -> Unit,
    onExportLocalBackup: (Uri, (Result<String>) -> Unit) -> Unit,
    onImportLocalBackup: (Uri, (Result<BackupData>) -> Unit) -> Unit,
    onScanReceiptClick: () -> Unit,
    isAutoUpdateCheckEnabled: Boolean = true,
    isCheckingForUpdates: Boolean = false,
    onToggleAutoUpdateCheck: (Boolean) -> Unit = {},
    onCheckForUpdates: ((com.yourname.mymoney.util.UpdateCheckResult) -> Unit) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showRestoreInput by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreSuccess by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    // Google Drive Dialog States
    var isSigningIn by remember { mutableStateOf(false) }
    var driveStatusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingRestoreData by remember { mutableStateOf<BackupData?>(null) }
    var restoreSourceLabel by remember { mutableStateOf("Google Drive") }
    var isBackingUpNow by remember { mutableStateOf(false) }
    var isRestoringFromDrive by remember { mutableStateOf(false) }
    var isIntervalDropdownOpen by remember { mutableStateOf(false) }

    fun shareText(title: String, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        context.startActivity(shareIntent)
    }

    // SAF: write a JSON backup to a user-chosen location (system file picker)
    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportLocalBackup(uri) { result ->
                result.onFailure { err ->
                    driveStatusMessage = false to (err.message ?: context.getString(R.string.err_save_backup_failed))
                }
            }
        }
    }

    // SAF: read a JSON backup from a user-chosen file
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLocalBackup(uri) { result ->
                result.onSuccess { backup ->
                    restoreSourceLabel = "Backup File"
                    pendingRestoreData = backup
                }.onFailure { err ->
                    driveStatusMessage = false to (err.message ?: context.getString(R.string.vm_invalid_backup_file))
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. GOOGLE DRIVE BACKUP & RESTORE
                Text(
                    text = stringResource(R.string.set_gdrive_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_drive_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Scope info & Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.set_gdrive_sync),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = stringResource(R.string.set_gdrive_hidden),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Account status row
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (googleAccountEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (googleAccountEmail != null) stringResource(R.string.set_connected_account) else stringResource(R.string.set_sign_in),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = googleAccountEmail ?: "Not signed in",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (googleAccountEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (googleAccountEmail != null) {
                                    TextButton(
                                        onClick = {
                                            onDisconnectGoogleAccount()
                                            driveStatusMessage = false to context.getString(R.string.err_acct_disconnected)
                                        }
                                    ) {
                                        Text(stringResource(R.string.set_sign_out), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isSigningIn) return@Button
                                            isSigningIn = true
                                            onGoogleSignIn { result ->
                                                isSigningIn = false
                                                result.onSuccess { msg ->
                                                    driveStatusMessage = true to msg
                                                }.onFailure { err ->
                                                    driveStatusMessage = false to (err.message ?: context.getString(R.string.err_signin_failed))
                                                }
                                            }
                                        },
                                        enabled = !isSigningIn,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("google_signin_button")
                                    ) {
                                        Text(
                                            text = if (isSigningIn) stringResource(R.string.set_signing_in) else stringResource(R.string.set_sign_in_action),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Last backup info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.set_last_backup),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastBackupTimeFormatted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Backup size info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.set_backup_size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastBackupSizeFormatted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!lastBackupError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.set_last_backup_error) + ": " + lastBackupError,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Auto-backup dropdown row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.set_auto_backup),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.set_daily_online),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                val intervalLabel = when (autoBackupInterval) {
                                    AutoBackupInterval.OFF -> stringResource(R.string.set_autobackup_off)
                                    AutoBackupInterval.DAILY -> stringResource(R.string.set_autobackup_daily)
                                    AutoBackupInterval.DAYS_3 -> stringResource(R.string.set_autobackup_3days)
                                    AutoBackupInterval.DAYS_7 -> stringResource(R.string.set_autobackup_7days)
                                    AutoBackupInterval.DAYS_14 -> stringResource(R.string.set_autobackup_14days)
                                    AutoBackupInterval.MONTHLY -> stringResource(R.string.set_autobackup_monthly)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clickable { isIntervalDropdownOpen = true }
                                        .testTag("auto_backup_dropdown")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = intervalLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isIntervalDropdownOpen,
                                    onDismissRequest = { isIntervalDropdownOpen = false }
                                ) {
                                    listOf(
                                        AutoBackupInterval.OFF to stringResource(R.string.set_autobackup_off),
                                        AutoBackupInterval.DAILY to stringResource(R.string.set_autobackup_daily),
                                        AutoBackupInterval.DAYS_3 to stringResource(R.string.set_autobackup_3days),
                                        AutoBackupInterval.DAYS_7 to stringResource(R.string.set_autobackup_7days),
                                        AutoBackupInterval.DAYS_14 to stringResource(R.string.set_autobackup_14days),
                                        AutoBackupInterval.MONTHLY to stringResource(R.string.set_autobackup_monthly)
                                    ).forEach { (interval, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (interval == autoBackupInterval) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (interval == autoBackupInterval) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                isIntervalDropdownOpen = false
                                                if (interval != AutoBackupInterval.OFF && googleAccountEmail.isNullOrBlank()) {
                                                    driveStatusMessage = false to context.getString(R.string.err_signin_autobackup)
                                                } else {
                                                    onAutoBackupIntervalChange(interval)
                                                    onToggleAutoBackup(interval != AutoBackupInterval.OFF)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: Back up now & Restore from Drive
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!isOnline) {
                                        driveStatusMessage = false to context.getString(R.string.err_no_net_backup)
                                        return@Button
                                    }
                                    if (googleAccountEmail.isNullOrBlank()) {
                                        driveStatusMessage = false to context.getString(R.string.err_signin_backup)
                                        return@Button
                                    }

                                    isBackingUpNow = true
                                    onDriveBackupNow { result ->
                                        isBackingUpNow = false
                                        result.onSuccess { msg ->
                                            driveStatusMessage = true to msg
                                        }.onFailure { err ->
                                            driveStatusMessage = false to (err.message ?: context.getString(R.string.err_backup_failed))
                                        }
                                    }
                                },
                                enabled = !isBackingUpNow && !isRestoringFromDrive,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drive_backup_now_btn")
                            ) {
                                if (isBackingUpNow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.set_backing_up), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.set_backup_now), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    if (!isOnline) {
                                        driveStatusMessage = false to context.getString(R.string.err_no_net_restore)
                                        return@Button
                                    }
                                    if (googleAccountEmail.isNullOrBlank()) {
                                        driveStatusMessage = false to context.getString(R.string.err_signin_restore)
                                        return@Button
                                    }

                                    isRestoringFromDrive = true
                                    onDriveRestoreRequest(
                                        { backup ->
                                            isRestoringFromDrive = false
                                            restoreSourceLabel = "Google Drive"
                                            pendingRestoreData = backup
                                        },
                                        { err ->
                                            isRestoringFromDrive = false
                                            driveStatusMessage = false to err
                                        }
                                    )
                                },
                                enabled = !isBackingUpNow && !isRestoringFromDrive,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drive_restore_btn")
                            ) {
                                if (isRestoringFromDrive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.set_restoring), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.set_restore_drive), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Status / error banner
                        driveStatusMessage?.let { (isSuccess, message) ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = message,
                                        fontSize = 11.sp,
                                        color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. APP INSTALLATION & OFFLINE PWA
                Text(
                    text = stringResource(R.string.set_offline_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.set_offline_title), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(stringResource(R.string.set_offline_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.set_ready),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Offline Simulation Toggle Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSimulatedOffline) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSimulatedOffline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSimulatedOffline() }
                                .testTag("toggle_offline_sim_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSimulatedOffline) Icons.Default.WifiOff else Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (isSimulatedOffline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isSimulatedOffline) stringResource(R.string.set_offline_sim_on) else stringResource(R.string.set_offline_sim),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSimulatedOffline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = if (isSimulatedOffline) stringResource(R.string.set_active) else stringResource(R.string.set_test),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSimulatedOffline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. THEME MODE TOGGLE
                Text(
                    text = stringResource(R.string.set_appearance),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "SYSTEM" to (stringResource(R.string.theme_auto) to Icons.Default.SettingsBrightness),
                        "LIGHT" to (stringResource(R.string.theme_light) to Icons.Default.LightMode),
                        "DARK" to (stringResource(R.string.theme_dark) to Icons.Default.DarkMode)
                    ).forEach { (mode, pair) ->
                        val (label, icon) = pair
                        val isSelected = currentThemeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 8.dp)
                                .testTag("theme_option_$mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. CURRENCY SELECTOR
                Text(
                    text = stringResource(R.string.set_currency),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportedCurrencies.forEach { curr ->
                        val isSelected = currentCurrency.code == curr.code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onCurrencyChange(curr) }
                                .testTag("currency_chip_${curr.code}")
                        ) {
                            Text(
                                text = "${curr.symbol} ${curr.code}",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 5. LANGUAGE SELECTOR
                Text(
                    text = stringResource(R.string.set_language),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val currentLanguage = LocalContext.current.resources.configuration.locales[0].language
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("en" to "English", "bn" to "বাংলা").forEach { (tag, name) ->
                        val isSelected = currentLanguage == tag
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                }
                                .testTag("language_chip_$tag")
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // AI SETTINGS SECTION
                Text(
                    text = stringResource(R.string.set_ai_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val coroutineScope = rememberCoroutineScope()
                var apiKeyInput by remember { mutableStateOf("") }
                var isKeyVisible by remember { mutableStateOf(false) }
                var isTestingKey by remember { mutableStateOf(false) }
                var aiStatusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
                var hasSavedKey by remember { mutableStateOf(com.yourname.mymoney.data.SecureApiKeyStorage.hasApiKey(context)) }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_settings_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.set_ai_key_label),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (hasSavedKey) {
                                            stringResource(R.string.set_ai_key_saved_status)
                                        } else {
                                            stringResource(R.string.set_ai_key_not_saved_status)
                                        },
                                        fontSize = 11.sp,
                                        color = if (hasSavedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (hasSavedKey) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.set_active),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Key input field with show/hide icon
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                aiStatusMessage = null
                            },
                            label = { Text(stringResource(R.string.set_ai_key_label), fontSize = 12.sp) },
                            placeholder = { Text(stringResource(R.string.set_ai_key_placeholder), fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isKeyVisible) stringResource(R.string.cd_hide_key) else stringResource(R.string.cd_show_key),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Actions: Save Key, Test Key, Remove Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val trimmed = apiKeyInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        val ok = com.yourname.mymoney.data.SecureApiKeyStorage.saveApiKey(context, trimmed)
                                        if (ok) {
                                            hasSavedKey = true
                                            apiKeyInput = ""
                                            aiStatusMessage = true to context.getString(R.string.set_ai_key_saved)
                                        }
                                    }
                                },
                                enabled = apiKeyInput.trim().isNotEmpty() && !isTestingKey,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_key_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.set_ai_btn_save), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val keyToTest = apiKeyInput.trim().ifEmpty {
                                        com.yourname.mymoney.data.SecureApiKeyStorage.getApiKey(context) ?: ""
                                    }
                                    if (keyToTest.isBlank()) {
                                        aiStatusMessage = false to context.getString(R.string.ai_key_missing_msg)
                                        return@OutlinedButton
                                    }
                                    isTestingKey = true
                                    aiStatusMessage = null
                                    coroutineScope.launch {
                                        val testRes = com.yourname.mymoney.ai.GeminiClient.testApiKey(keyToTest)
                                        isTestingKey = false
                                        testRes.onSuccess {
                                            aiStatusMessage = true to context.getString(R.string.set_ai_test_success)
                                        }.onFailure { err ->
                                            aiStatusMessage = false to (err.message ?: context.getString(R.string.vm_unexpected_error))
                                        }
                                    }
                                },
                                enabled = !isTestingKey && (apiKeyInput.trim().isNotEmpty() || hasSavedKey),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_api_key_btn")
                            ) {
                                if (isTestingKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.set_ai_btn_test), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (hasSavedKey) {
                                IconButton(
                                    onClick = {
                                        com.yourname.mymoney.data.SecureApiKeyStorage.clearApiKey(context)
                                        hasSavedKey = false
                                        apiKeyInput = ""
                                        aiStatusMessage = true to context.getString(R.string.set_ai_key_removed)
                                    },
                                    enabled = !isTestingKey,
                                    modifier = Modifier.testTag("remove_api_key_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.set_ai_btn_remove),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Status message feedback
                        aiStatusMessage?.let { (isSuccess, msg) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Help text on how to get a free key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.set_ai_help_title),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(stringResource(R.string.set_ai_help_step1), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.set_ai_help_step2), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.set_ai_help_step3), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.set_ai_help_step4), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.set_ai_help_security),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. RECEIPT SCANNER SHORTCUT
                Text(
                    text = stringResource(R.string.set_smart_tools),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onScanReceiptClick()
                        }
                        .testTag("settings_scan_receipt_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.set_scan_gemini), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(stringResource(R.string.set_scan_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. LOCAL DATA BACKUP & EXPORT (FALLBACK)
                Text(
                    text = stringResource(R.string.set_local_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val csv = DataBackupService.exportToCsv(transactions, loansWithDetails, budgets)
                            shareText(context.getString(R.string.set_share_csv_title), csv)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.set_export_csv), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val rawLoans = loansWithDetails.map { it.loan }
                            val rawReps = loansWithDetails.flatMap { it.repayments }
                            val json = DataBackupService.exportToJson(transactions, rawLoans, rawReps, allBudgets, categories)
                            shareText(context.getString(R.string.set_share_json_title), json)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_json_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.set_backup_json), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Save / restore a JSON backup file via the system file picker (SAF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { saveBackupLauncher.launch("MyMoney-backup.json") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_backup_file_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.set_save_file), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_backup_file_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.set_restore_file), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Restore from JSON button
                Button(
                    onClick = { showRestoreInput = !showRestoreInput },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_restore_json_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showRestoreInput) stringResource(R.string.set_hide_panel) else stringResource(R.string.set_restore_json),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (showRestoreInput) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.set_paste_json), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = restoreJsonText,
                                onValueChange = {
                                    restoreJsonText = it
                                    restoreError = null
                                    restoreSuccess = false
                                },
                                placeholder = { Text(stringResource(R.string.set_paste_ph), fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("restore_json_input"),
                                maxLines = 5,
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (restoreError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(restoreError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }

                            if (restoreSuccess) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(stringResource(R.string.set_restored_ok), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val backup = DataBackupService.parseBackupJson(restoreJsonText.trim())
                                            onRestoreBackupMerge(backup) { result ->
                                                val error = result.exceptionOrNull()?.message
                                                restoreError = error
                                                restoreSuccess = error == null
                                            }
                                        } catch (e: Exception) {
                                            restoreSuccess = false
                                            restoreError = e.message ?: context.getString(R.string.err_backup_invalid)
                                        }
                                    },
                                    enabled = restoreJsonText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("execute_restore_merge_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.set_merge_data), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val backup = DataBackupService.parseBackupJson(restoreJsonText.trim())
                                            onRestoreBackupReplace(backup) { result ->
                                                val error = result.exceptionOrNull()?.message
                                                restoreError = error
                                                restoreSuccess = error == null
                                            }
                                        } catch (e: Exception) {
                                            restoreSuccess = false
                                            restoreError = e.message ?: context.getString(R.string.err_backup_invalid)
                                        }
                                    },
                                    enabled = restoreJsonText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("execute_restore_replace_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.set_replace_all), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 7. APP UPDATES
                Text(
                    text = stringResource(R.string.set_updates_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.set_auto_check_updates),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.set_auto_check_updates_sub),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoUpdateCheckEnabled,
                                onCheckedChange = { onToggleAutoUpdateCheck(it) },
                                modifier = Modifier.testTag("toggle_auto_update_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                updateStatusMessage = null
                                onCheckForUpdates { result ->
                                    val currentVer = try {
                                        com.yourname.mymoney.BuildConfig.VERSION_NAME
                                    } catch (_: Throwable) {
                                        "1.0"
                                    }
                                    when (result) {
                                        is com.yourname.mymoney.util.UpdateCheckResult.UpToDate -> {
                                            updateStatusMessage = context.getString(R.string.update_latest_version, currentVer)
                                        }
                                        is com.yourname.mymoney.util.UpdateCheckResult.NoInternet -> {
                                            updateStatusMessage = context.getString(R.string.update_no_internet)
                                        }
                                        is com.yourname.mymoney.util.UpdateCheckResult.RateLimited -> {
                                            updateStatusMessage = context.getString(R.string.update_rate_limited)
                                        }
                                        is com.yourname.mymoney.util.UpdateCheckResult.Error -> {
                                            updateStatusMessage = result.message
                                        }
                                        is com.yourname.mymoney.util.UpdateCheckResult.UpdateAvailable -> {
                                            updateStatusMessage = null
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingForUpdates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("check_for_updates_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isCheckingForUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.set_checking_updates),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.set_check_updates),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!updateStatusMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateStatusMessage ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Drive Restore Confirmation Dialog (Merge or Replace)
    pendingRestoreData?.let { backup ->
        val backupDateStr = if (backup.timestamp > 0L) {
            SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(backup.timestamp))
        } else {
            lastBackupTimeFormatted
        }
        AlertDialog(
            onDismissRequest = { pendingRestoreData = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (restoreSourceLabel == "Google Drive") {
                        stringResource(R.string.set_restore_from_gdrive)
                    } else {
                        stringResource(R.string.set_restore_from_file)
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (restoreSourceLabel == "Google Drive") {
                            stringResource(R.string.set_gdrive_found)
                        } else {
                            stringResource(R.string.set_file_contains)
                        },
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(stringResource(R.string.set_backup_date, backupDateStr), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (restoreSourceLabel == "Google Drive" && lastBackupSizeFormatted != "—") {
                                Text(stringResource(R.string.set_backup_size_label, lastBackupSizeFormatted), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.set_count_transactions, backup.transactions.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.set_count_loans, backup.loans.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.set_count_budgets, backup.budgets.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.set_restore_how),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onRestoreBackupMerge(backup) { result ->
                                pendingRestoreData = null
                                val error = result.exceptionOrNull()?.message
                                driveStatusMessage = if (error == null) {
                                    true to if (restoreSourceLabel == "Google Drive") {
                                        context.getString(R.string.set_restore_merge_success)
                                    } else {
                                        context.getString(R.string.set_restore_file_merge_success)
                                    }
                                } else {
                                    false to error
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_merge_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.set_merge_current), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onRestoreBackupReplace(backup) { result ->
                                pendingRestoreData = null
                                val error = result.exceptionOrNull()?.message
                                driveStatusMessage = if (error == null) {
                                    true to if (restoreSourceLabel == "Google Drive") {
                                        context.getString(R.string.set_restore_replace_success)
                                    } else {
                                        context.getString(R.string.set_restore_file_replace_success)
                                    }
                                } else {
                                    false to error
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_replace_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.set_replace_current), fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { pendingRestoreData = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        )
    }
}
