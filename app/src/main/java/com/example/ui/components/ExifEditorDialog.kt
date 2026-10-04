package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ExifData

@Composable
fun ExifEditorDialog(
    initialData: ExifData,
    onDismiss: () -> Unit,
    onSave: (ExifData) -> Unit,
    onApplyAiQuick: () -> Unit
) {
    var cameraModel by remember { mutableStateOf(initialData.cameraModel ?: "") }
    var cameraMake by remember { mutableStateOf(initialData.cameraMake ?: "") }
    var software by remember { mutableStateOf(initialData.software ?: "Shots Studio") }
    var artist by remember { mutableStateOf(initialData.artist ?: "") }
    var imageDescription by remember { mutableStateOf(initialData.imageDescription ?: "") }
    var userComment by remember { mutableStateOf(initialData.userComment ?: "") }
    var dateTaken by remember { mutableStateOf(initialData.dateTaken ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit EXIF Metadata",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Values edited here are written directly into the file's EXIF binary headers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Read-only Hardware specs summary card if available
                if (initialData.iso != null || initialData.fNumber != null || initialData.exposureTime != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Camera Hardware Readings",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ISO: ${initialData.iso ?: "N/A"} | ƒ/${initialData.fNumber ?: "N/A"} | ${initialData.exposureTime ?: "N/A"}s | ${initialData.focalLength ?: "N/A"}mm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onApplyAiQuick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_ai_sync_exif")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Fill with AI Analysis")
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = imageDescription,
                    onValueChange = { imageDescription = it },
                    label = { Text("Title / Description (TAG_IMAGE_DESCRIPTION)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_description")
                )

                OutlinedTextField(
                    value = userComment,
                    onValueChange = { userComment = it },
                    label = { Text("User Comment / Notes (TAG_USER_COMMENT)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_comment")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cameraMake,
                        onValueChange = { cameraMake = it },
                        label = { Text("Make") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cameraModel,
                        onValueChange = { cameraModel = it },
                        label = { Text("Model") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = software,
                    onValueChange = { software = it },
                    label = { Text("Software / Tool (TAG_SOFTWARE)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Author (TAG_ARTIST)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dateTaken,
                    onValueChange = { dateTaken = it },
                    label = { Text("Date Taken (YYYY:MM:DD HH:MM:SS)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = initialData.copy(
                        cameraMake = cameraMake.ifBlank { null },
                        cameraModel = cameraModel.ifBlank { null },
                        software = software.ifBlank { null },
                        artist = artist.ifBlank { null },
                        imageDescription = imageDescription.ifBlank { null },
                        userComment = userComment.ifBlank { null },
                        dateTaken = dateTaken.ifBlank { null }
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_exif")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to File")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_exif")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel")
            }
        }
    )
}
