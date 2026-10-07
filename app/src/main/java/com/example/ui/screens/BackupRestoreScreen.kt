package com.amresalehin.emreshots.ui.screens

import com.amresalehin.emreshots.R

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.service.backup.RestoreMode
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackupToUri(context, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_and_restore), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.library_backup), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(lastBackupInfo ?: stringResource(R.string.no_backup_yet), style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("emreshots-backup.json") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_json))
                        }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CloudDone, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.restore_json))
                        }
                    }
                }
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.restore_backup)) },
            text = { Text(stringResource(R.string.choose_backup_apply_mode)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            pendingRestoreUri = null
                            viewModel.restoreFromUri(uri, RestoreMode.MERGE)
                        }
                    ) { Text(stringResource(R.string.merge)) }
                    Button(
                        onClick = {
                            pendingRestoreUri = null
                            viewModel.restoreFromUri(uri, RestoreMode.REPLACE)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) { Text(stringResource(R.string.replace)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
