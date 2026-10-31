package com.amresalehin.emreshots.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import com.example.ui.icons.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emreshots.service.editor.BrushType
import com.amresalehin.emreshots.service.editor.CalloutStyle
import com.amresalehin.emreshots.service.editor.CutoutMode
import com.amresalehin.emreshots.service.editor.DrawingStroke
import com.amresalehin.emreshots.service.editor.EditorCategory
import com.amresalehin.emreshots.service.editor.EffectType
import com.amresalehin.emreshots.service.editor.FrameStyle
import com.amresalehin.emreshots.service.editor.LensFlareStyle
import com.amresalehin.emreshots.service.editor.RetouchMode
import com.amresalehin.emreshots.service.editor.ShapeCropType
import com.amresalehin.emreshots.service.editor.ShapeKind
import com.amresalehin.emreshots.service.editor.ToolMode
import com.amresalehin.emreshots.viewmodel.PhotoEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    screenshotId: String,
    onNavigateBack: () -> Unit,
    onSaveComplete: (String) -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    LaunchedEffect(screenshotId) {
        viewModel.loadMedia(screenshotId)
    }

    val originalBitmap by viewModel.originalBitmap.collectAsStateWithLifecycle()
    val baseBitmap by viewModel.baseBitmap.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val adjustSettings by viewModel.adjustSettings.collectAsStateWithLifecycle()
    val activeEffect by viewModel.activeEffect.collectAsStateWithLifecycle()
    val activeBrush by viewModel.activeBrush.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    var isComparingOriginal by remember { mutableStateOf(false) }

    // Touch drawing stroke accumulator
    var currentStrokePoints by remember { mutableStateOf<List<PointF>>(emptyList()) }

    BackHandler {
        if (canUndo) showExitDialog = true else onNavigateBack()
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Discard Edits?") },
            text = { Text("You have unsaved edits. Are you sure you want to discard them?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(activeCategory.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (canUndo) showExitDialog = true else onNavigateBack()
                    }, modifier = Modifier.testTag("btn_editor_close")) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.testTag("btn_editor_undo")
                    ) {
                        Icon(UndoIcon, contentDescription = "Undo")
                    }
                    // Redo
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.testTag("btn_editor_redo")
                    ) {
                        Icon(RedoIcon, contentDescription = "Redo")
                    }
                    // Compare (Hold to view original)
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .testTag("btn_editor_compare")
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isComparingOriginal = true
                                        tryAwaitRelease()
                                        isComparingOriginal = false
                                    }
                                )
                            }
                    ) {
                        Icon(
                            AppIcons.Visibility,
                            contentDescription = "Compare Original",
                            tint = if (isComparingOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Reset
                    IconButton(onClick = { viewModel.resetAll() }, modifier = Modifier.testTag("btn_editor_reset")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                    // Save
                    Button(
                        onClick = {
                            viewModel.saveEditedImage { newId ->
                                onSaveComplete(newId)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("btn_editor_save"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                // Secondary Controls Tray based on category
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when (activeCategory) {
                        EditorCategory.TOOLS -> ToolsControlTray(viewModel)
                        EditorCategory.ADJUST -> AdjustControlTray(viewModel)
                        EditorCategory.EFFECTS -> EffectsControlTray(viewModel)
                        EditorCategory.RETOUCH -> RetouchControlTray(viewModel)
                        EditorCategory.CUTOUT_BG -> CutoutControlTray(viewModel)
                        EditorCategory.DRAW -> DrawControlTray(viewModel)
                        EditorCategory.TEXT -> TextControlTray(viewModel)
                        EditorCategory.STICKER -> StickerControlTray(viewModel)
                        EditorCategory.SHAPES -> ShapesControlTray(viewModel)
                        EditorCategory.FRAMES -> FramesControlTray(viewModel)
                        EditorCategory.CALLOUT -> CalloutControlTray(viewModel)
                        EditorCategory.LENS_FLARE -> LensFlareControlTray(viewModel)
                    }
                }

                // Primary 12-Category Scrollable Tab Bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    items(EditorCategory.entries) { cat ->
                        val isSelected = cat == activeCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null,
                            modifier = Modifier.testTag("tab_cat_${cat.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = if (isComparingOriginal) originalBitmap else baseBitmap

            if (displayBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeCategory, activeBrush, brushColor, brushSize) {
                            if (activeCategory == EditorCategory.DRAW) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints = listOf(PointF(offset.x, offset.y))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStrokePoints = currentStrokePoints + PointF(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        if (currentStrokePoints.size >= 2) {
                                            viewModel.addStroke(
                                                DrawingStroke(
                                                    brush = activeBrush,
                                                    points = currentStrokePoints,
                                                    color = brushColor,
                                                    size = brushSize
                                                )
                                            )
                                        }
                                        currentStrokePoints = emptyList()
                                    }
                                )
                            } else if (activeCategory == EditorCategory.RETOUCH || activeCategory == EditorCategory.TOOLS) {
                                detectTapGestures { offset ->
                                    if (activeCategory == EditorCategory.RETOUCH) {
                                        viewModel.applyRetouchAt(offset.x, offset.y)
                                    } else if (activeCategory == EditorCategory.TOOLS) {
                                        viewModel.applyMagicWand(offset.x.toInt(), offset.y.toInt())
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Editing Canvas",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isComparingOriginal) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        ) {
                            Text("Showing Original", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            // Spinner overlay when performing computations
            AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Applying...", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUB-TOOL TRAYS FOR ALL 12 CATEGORIES
// =========================================================================

@Composable
fun ToolsControlTray(viewModel: PhotoEditorViewModel) {
    var straightenAngle by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Tools & Deformations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            item {
                OutlinedButton(onClick = { viewModel.applyShapeCrop(ShapeCropType.CIRCLE) }) { Text("Circle Crop") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyShapeCrop(ShapeCropType.HEART) }) { Text("Heart Crop") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyShapeCrop(ShapeCropType.STAR) }) { Text("Star Crop") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyShapeCrop(ShapeCropType.HEXAGON) }) { Text("Hexagon Crop") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyShapeCrop(ShapeCropType.DIAMOND) }) { Text("Diamond Crop") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyStraighten(-5f) }) { Text("Straighten -5°") }
            }
            item {
                OutlinedButton(onClick = { viewModel.applyStraighten(5f) }) { Text("Straighten +5°") }
            }
            item {
                Button(onClick = { viewModel.setToolMode(ToolMode.MAGIC_WAND) }) { Text("Magic Wand") }
            }
            item {
                Button(onClick = { viewModel.setToolMode(ToolMode.LIQUIFY) }) { Text("Liquify Warp") }
            }
        }
    }
}

@Composable
fun AdjustControlTray(viewModel: PhotoEditorViewModel) {
    val settings by viewModel.adjustSettings.collectAsStateWithLifecycle()
    var activeAdjustTab by remember { mutableStateOf("Vibrance") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Vibrance", "Dehaze", "Brightness", "Contrast", "Exposure", "Saturation", "Temperature", "Tint", "Vignette", "Duotone", "Color Splash").forEach { tab ->
                val isSelected = activeAdjustTab == tab
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { activeAdjustTab = tab }
                ) {
                    Text(tab, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (activeAdjustTab) {
            "Vibrance" -> {
                Text("Vibrance: ${settings.vibrance.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.vibrance, onValueChange = { viewModel.updateAdjustSettings(settings.copy(vibrance = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = -100f..100f)
            }
            "Dehaze" -> {
                Text("Dehaze: ${settings.dehaze.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.dehaze, onValueChange = { viewModel.updateAdjustSettings(settings.copy(dehaze = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = 0f..100f)
            }
            "Brightness" -> {
                Text("Brightness: ${settings.brightness.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.brightness, onValueChange = { viewModel.updateAdjustSettings(settings.copy(brightness = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = -100f..100f)
            }
            "Contrast" -> {
                Text("Contrast: ${settings.contrast.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.contrast, onValueChange = { viewModel.updateAdjustSettings(settings.copy(contrast = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = -100f..100f)
            }
            "Saturation" -> {
                Text("Saturation: ${settings.saturation.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.saturation, onValueChange = { viewModel.updateAdjustSettings(settings.copy(saturation = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = -100f..100f)
            }
            "Vignette" -> {
                Text("Vignette: ${settings.vignette.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.vignette, onValueChange = { viewModel.updateAdjustSettings(settings.copy(vignette = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = 0f..100f)
            }
            "Duotone" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.updateAdjustSettings(settings.copy(duotoneEnabled = true, duotoneColor1 = android.graphics.Color.BLUE, duotoneColor2 = android.graphics.Color.YELLOW)); viewModel.commitAdjustments() }) { Text("Blue/Gold") }
                    Button(onClick = { viewModel.updateAdjustSettings(settings.copy(duotoneEnabled = true, duotoneColor1 = android.graphics.Color.DKGRAY, duotoneColor2 = android.graphics.Color.MAGENTA)); viewModel.commitAdjustments() }) { Text("Cyberpunk") }
                    OutlinedButton(onClick = { viewModel.updateAdjustSettings(settings.copy(duotoneEnabled = false)); viewModel.commitAdjustments() }) { Text("Disable") }
                }
            }
            "Color Splash" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.updateAdjustSettings(settings.copy(colorSplashEnabled = true, splashTargetHue = 0f)); viewModel.commitAdjustments() }) { Text("Keep Red") }
                    Button(onClick = { viewModel.updateAdjustSettings(settings.copy(colorSplashEnabled = true, splashTargetHue = 120f)); viewModel.commitAdjustments() }) { Text("Keep Green") }
                    Button(onClick = { viewModel.updateAdjustSettings(settings.copy(colorSplashEnabled = true, splashTargetHue = 210f)); viewModel.commitAdjustments() }) { Text("Keep Blue") }
                    OutlinedButton(onClick = { viewModel.updateAdjustSettings(settings.copy(colorSplashEnabled = false)); viewModel.commitAdjustments() }) { Text("Disable") }
                }
            }
            else -> {
                Text("Temperature: ${settings.temperature.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = settings.temperature, onValueChange = { viewModel.updateAdjustSettings(settings.copy(temperature = it)) }, onValueChangeFinished = { viewModel.commitAdjustments() }, valueRange = -100f..100f)
            }
        }
    }
}

@Composable
fun EffectsControlTray(viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Artistic & Stylized Effects", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(EffectType.entries.filter { it != EffectType.NONE }) { effect ->
                Button(onClick = { viewModel.commitEffect(effect) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(effect.displayName, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RetouchControlTray(viewModel: PhotoEditorViewModel) {
    val activeMode by viewModel.activeRetouchMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Tap on face/feature to apply retouch", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(RetouchMode.entries) { mode ->
                val isSelected = mode == activeMode
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setRetouchMode(mode) },
                    label = { Text(mode.displayName, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun CutoutControlTray(viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Local Subject & Background Cutout", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.applyCutout(invert = false) }) { Text("Isolate Subject") }
            Button(onClick = { viewModel.applyCutout(invert = true) }) { Text("Extract Background") }
            OutlinedButton(onClick = { viewModel.applyCutoutShadow() }) { Text("Add Subject Shadow") }
        }
    }
}

@Composable
fun DrawControlTray(viewModel: PhotoEditorViewModel) {
    val activeBrush by viewModel.activeBrush.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BrushType.entries) { b ->
                val isSelected = b == activeBrush
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setBrush(b) },
                    label = { Text(b.displayName, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Size: ${brushSize.toInt()}", fontSize = 11.sp)
            Slider(value = brushSize, onValueChange = { viewModel.setBrushSize(it) }, valueRange = 4f..80f, modifier = Modifier.weight(1f))
            // Quick colors
            listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black).forEach { c ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { viewModel.setBrushColor(c.toArgb()) }
                )
            }
        }
    }
}

@Composable
fun TextControlTray(viewModel: PhotoEditorViewModel) {
    var textInput by remember { mutableStateOf("EmreShots Art") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.addTextLayer(textInput) }) {
                Text("Add Text")
            }
            OutlinedButton(onClick = {
                viewModel.addTextLayer("Arc Curved")
                val last = viewModel.textLayers.value.lastOrNull()
                if (last != null) viewModel.updateTextLayer(last.copy(isCurved = true))
            }) {
                Text("Add Curved")
            }
            OutlinedButton(onClick = {
                viewModel.addTextLayer("Neon Glow")
                val last = viewModel.textLayers.value.lastOrNull()
                if (last != null) viewModel.updateTextLayer(last.copy(hasGlow = true, glowColor = android.graphics.Color.MAGENTA))
            }) {
                Text("Glow Text")
            }
        }
    }
}

@Composable
fun StickerControlTray(viewModel: PhotoEditorViewModel) {
    val emojis = listOf("✨", "❤️", "🔥", "📸", "⚡", "🌟", "🎉", "👑", "🚀", "🏝️", "🕶️", "💯", "🎈", "🎨", "🏆", "💎", "⭐", "🌈")

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Tap sticker to place on image", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(emojis) { emoji ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { viewModel.addSticker(emoji) }
                ) {
                    Text(emoji, fontSize = 28.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun ShapesControlTray(viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Shape Element", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(ShapeKind.entries) { kind ->
                Button(onClick = { viewModel.addShape(kind) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(kind.displayName, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FramesControlTray(viewModel: PhotoEditorViewModel) {
    val activeFrame by viewModel.frameStyle.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Border & Photographic Frames", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(FrameStyle.entries) { frame ->
                val isSelected = frame == activeFrame
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setFrameStyle(frame) },
                    label = { Text(frame.displayName, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun CalloutControlTray(viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Annotation Callout", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { viewModel.addCallout(CalloutStyle.SPEECH_BUBBLE, "Speech") }) { Text("Speech") }
            Button(onClick = { viewModel.addCallout(CalloutStyle.THOUGHT_BUBBLE, "Thought") }) { Text("Thought") }
            Button(onClick = { viewModel.addCallout(CalloutStyle.ARROW_CALLOUT, "Pointer") }) { Text("Arrow") }
            Button(onClick = { viewModel.addCallout(CalloutStyle.NUMBERED_PIN, "1") }) { Text("Numbered") }
        }
    }
}

@Composable
fun LensFlareControlTray(viewModel: PhotoEditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Optical Lens Flare", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(LensFlareStyle.entries) { flare ->
                Button(onClick = { viewModel.addLensFlare(flare) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(flare.displayName, fontSize = 11.sp)
                }
            }
        }
    }
}

private val UndoIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z").toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ).build()
}

private val RedoIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Redo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z").toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)
    ).build()
}
