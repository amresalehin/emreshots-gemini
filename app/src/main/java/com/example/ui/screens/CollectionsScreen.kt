package com.amresalehin.emreshots.ui.screens

import com.amresalehin.emreshots.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emreshots.viewmodel.ScreenshotsViewModel

@Composable
fun CollectionsScreen(
    viewModel: ScreenshotsViewModel,
    onNavigateBack: () -> Unit,
    onOpenCollection: (String) -> Unit
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val allScreenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            collections.forEach { collection ->
                Card(
                    onClick = { onOpenCollection(collection.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(collection.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.collection_items_count, allScreenshots.count { it.collectionIds.contains(collection.id) }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteCollection(collection.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete) + " " + collection.name, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.create_collection))
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; name = "" },
            title = { Text(stringResource(R.string.create_collection)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createCollection(name.trim(), "", "folder", "#4A4641")
                        showCreate = false
                        name = ""
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.create_collection)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; name = "" }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
