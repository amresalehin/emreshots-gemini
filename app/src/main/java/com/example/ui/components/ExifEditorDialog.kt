package com.amresalehin.emreshots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emreshots.data.model.ExifData
import com.amresalehin.emreshots.data.model.ScreenshotItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExifEditorDialog(
    initialData: ExifData,
    screenshot: ScreenshotItem? = null,
    onDismiss: () -> Unit,
    onSave: (ExifData) -> Unit,
    onApplyAiQuick: (() -> Unit)? = null
) {
    var cameraModel by remember { mutableStateOf(initialData.cameraModel ?: "") }
    var cameraMake by remember { mutableStateOf(initialData.cameraMake ?: "") }
    var software by remember { mutableStateOf(initialData.software ?: "EmreShots") }
    var artist by remember { mutableStateOf(initialData.artist ?: "") }
    var imageDescription by remember { mutableStateOf(initialData.imageDescription ?: "") }
    var userComment by remember { mutableStateOf(initialData.userComment ?: "") }
    var dateTaken by remember { mutableStateOf(initialData.dateTaken ?: "") }
    var latitudeInput by remember { mutableStateOf(initialData.latitude?.toString() ?: "") }
    var longitudeInput by remember { mutableStateOf(initialData.longitude?.toString() ?: "") }

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
                    text = "Modifies binary EXIF headers directly. Supports JPEG, PNG, and WebP media formats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Read-only Hardware specs summary card if available
                if (initialData.iso != null || initialData.fNumber != null || initialData.exposureTime != null || initialData.imageWidth != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Camera Hardware Readings",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val dimText = if (initialData.imageWidth != null && initialData.imageLength != null) "${initialData.imageWidth}x${initialData.imageLength} • " else ""
                            Text(
                                text = "${dimText}ISO: ${initialData.iso ?: "N/A"} | ƒ/${initialData.fNumber ?: "N/A"} | ${initialData.exposureTime ?: "N/A"}s | ${initialData.focalLength ?: "N/A"}mm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (screenshot != null) {
                            if (screenshot.title.isNotBlank()) {
                                imageDescription = screenshot.title
                            }
                            val tagKeywords = screenshot.tags.joinToString(", ")
                            val model = screenshot.aiModelUsed ?: "Shots AI"
                            userComment = "EmreShots AI [$model] | Tags: $tagKeywords | ${screenshot.description}"
                            software = "EmreShots AI ($model)"
                            if (dateTaken.isBlank()) {
                                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                                dateTaken = sdf.format(Date(screenshot.addedOn))
                            }
                        }
                        onApplyAiQuick?.invoke()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_ai_sync_exif")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.auto_fill_with_ai_analysis))
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = imageDescription,
                    onValueChange = { imageDescription = it },
                    label = { Text(stringResource(R.string.title_description_exif)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_description")
                )

                OutlinedTextField(
                    value = userComment,
                    onValueChange = { userComment = it },
                    label = { Text(stringResource(R.string.user_comment_notes_exif)) },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_comment")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cameraMake,
                        onValueChange = { cameraMake = it },
                        label = { Text(stringResource(R.string.make)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_exif_make")
                    )
                    OutlinedTextField(
                        value = cameraModel,
                        onValueChange = { cameraModel = it },
                        label = { Text(stringResource(R.string.model)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_exif_model")
                    )
                }

                OutlinedTextField(
                    value = software,
                    onValueChange = { software = it },
                    label = { Text(stringResource(R.string.software_tool_exif)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_software")
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.artist_author_exif)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_artist")
                )

                OutlinedTextField(
                    value = dateTaken,
                    onValueChange = { dateTaken = it },
                    label = { Text(stringResource(R.string.date_taken_exif)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_exif_date")
                )

                HorizontalDivider()

                // GPS Location Editing & Privacy Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GPS Coordinates",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (latitudeInput.isNotBlank() || longitudeInput.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    latitudeInput = ""
                                    longitudeInput = ""
                                },
                                modifier = Modifier.testTag("btn_clear_gps")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.clear_gps), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudeInput,
                            onValueChange = { latitudeInput = it },
                            label = { Text(stringResource(R.string.latitude_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_exif_latitude")
                        )
                        OutlinedTextField(
                            value = longitudeInput,
                            onValueChange = { longitudeInput = it },
                            label = { Text(stringResource(R.string.longitude_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_exif_longitude")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latitudeInput.trim().toDoubleOrNull()?.coerceIn(-90.0, 90.0)
                    val lon = longitudeInput.trim().toDoubleOrNull()?.coerceIn(-180.0, 180.0)
                    val hasGps = lat != null && lon != null

                    val updated = initialData.copy(
                        cameraMake = cameraMake.trim().ifBlank { null },
                        cameraModel = cameraModel.trim().ifBlank { null },
                        software = software.trim().ifBlank { null },
                        artist = artist.trim().ifBlank { null },
                        imageDescription = imageDescription.trim().ifBlank { null },
                        userComment = userComment.trim().ifBlank { null },
                        dateTaken = dateTaken.trim().ifBlank { null },
                        latitude = if (hasGps) lat else null,
                        longitude = if (hasGps) lon else null
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_exif")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.save_to_file))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_exif")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
