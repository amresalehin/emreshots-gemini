package com.amresalehin.emreshots.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emreshots.data.local.AppDatabase
import com.amresalehin.emreshots.data.model.ScreenshotItem
import com.amresalehin.emreshots.data.repository.ScreenshotRepository
import com.amresalehin.emreshots.service.editor.AdjustSettings
import com.amresalehin.emreshots.service.editor.BitmapEffectsProcessor
import com.amresalehin.emreshots.service.editor.BrushType
import com.amresalehin.emreshots.service.editor.CalloutElement
import com.amresalehin.emreshots.service.editor.CalloutStyle
import com.amresalehin.emreshots.service.editor.CutoutMode
import com.amresalehin.emreshots.service.editor.DrawingAndTextEngine
import com.amresalehin.emreshots.service.editor.DrawingStroke
import com.amresalehin.emreshots.service.editor.EditorCategory
import com.amresalehin.emreshots.service.editor.EffectType
import com.amresalehin.emreshots.service.editor.FrameStyle
import com.amresalehin.emreshots.service.editor.LensFlareElement
import com.amresalehin.emreshots.service.editor.LensFlareStyle
import com.amresalehin.emreshots.service.editor.LiquifyMode
import com.amresalehin.emreshots.service.editor.RetouchMode
import com.amresalehin.emreshots.service.editor.ShapeCropType
import com.amresalehin.emreshots.service.editor.ShapeElement
import com.amresalehin.emreshots.service.editor.ShapeKind
import com.amresalehin.emreshots.service.editor.StickerItem
import com.amresalehin.emreshots.service.editor.TextAlignMode
import com.amresalehin.emreshots.service.editor.TextLayer
import com.amresalehin.emreshots.service.editor.ToolMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class EditorStateSnapshot(
    val bitmap: Bitmap,
    val adjustSettings: AdjustSettings,
    val activeEffect: EffectType,
    val strokes: List<DrawingStroke>,
    val textLayers: List<TextLayer>,
    val stickers: List<StickerItem>,
    val shapes: List<ShapeElement>,
    val frameStyle: FrameStyle,
    val callouts: List<CalloutElement>,
    val lensFlares: List<LensFlareElement>
)

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository(AppDatabase.getInstance(application).screenshotDao())

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _baseBitmap = MutableStateFlow<Bitmap?>(null)
    val baseBitmap: StateFlow<Bitmap?> = _baseBitmap.asStateFlow()

    private val _activeCategory = MutableStateFlow(EditorCategory.TOOLS)
    val activeCategory: StateFlow<EditorCategory> = _activeCategory.asStateFlow()

    private val _activeToolMode = MutableStateFlow(ToolMode.NONE)
    val activeToolMode: StateFlow<ToolMode> = _activeToolMode.asStateFlow()

    private val _adjustSettings = MutableStateFlow(AdjustSettings())
    val adjustSettings: StateFlow<AdjustSettings> = _adjustSettings.asStateFlow()

    private val _activeEffect = MutableStateFlow(EffectType.NONE)
    val activeEffect: StateFlow<EffectType> = _activeEffect.asStateFlow()

    private val _activeRetouchMode = MutableStateFlow(RetouchMode.SMOOTHING)
    val activeRetouchMode: StateFlow<RetouchMode> = _activeRetouchMode.asStateFlow()

    private val _activeCutoutMode = MutableStateFlow(CutoutMode.AI_SUBJECT)
    val activeCutoutMode: StateFlow<CutoutMode> = _activeCutoutMode.asStateFlow()

    // Drawing state
    private val _activeBrush = MutableStateFlow(BrushType.PEN)
    val activeBrush: StateFlow<BrushType> = _activeBrush.asStateFlow()
    private val _brushColor = MutableStateFlow(Color.RED)
    val brushColor: StateFlow<Int> = _brushColor.asStateFlow()
    private val _brushSize = MutableStateFlow(18f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()
    private val _strokes = MutableStateFlow<List<DrawingStroke>>(emptyList())
    val strokes: StateFlow<List<DrawingStroke>> = _strokes.asStateFlow()

    // Layers
    private val _textLayers = MutableStateFlow<List<TextLayer>>(emptyList())
    val textLayers: StateFlow<List<TextLayer>> = _textLayers.asStateFlow()

    private val _stickers = MutableStateFlow<List<StickerItem>>(emptyList())
    val stickers: StateFlow<List<StickerItem>> = _stickers.asStateFlow()

    private val _shapes = MutableStateFlow<List<ShapeElement>>(emptyList())
    val shapes: StateFlow<List<ShapeElement>> = _shapes.asStateFlow()

    private val _frameStyle = MutableStateFlow(FrameStyle.NONE)
    val frameStyle: StateFlow<FrameStyle> = _frameStyle.asStateFlow()

    private val _callouts = MutableStateFlow<List<CalloutElement>>(emptyList())
    val callouts: StateFlow<List<CalloutElement>> = _callouts.asStateFlow()

    private val _lensFlares = MutableStateFlow<List<LensFlareElement>>(emptyList())
    val lensFlares: StateFlow<List<LensFlareElement>> = _lensFlares.asStateFlow()

    // History stack for Undo / Redo
    private val undoStack = mutableListOf<EditorStateSnapshot>()
    private val redoStack = mutableListOf<EditorStateSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentScreenshot: ScreenshotItem? = null

    fun loadMedia(screenshotId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val item = repository.getScreenshotSync(screenshotId)
            currentScreenshot = item
            if (item != null) {
                val bmp = loadBitmap(item)
                if (bmp != null) {
                    _originalBitmap.value = bmp
                    _baseBitmap.value = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    undoStack.clear()
                    redoStack.clear()
                    updateHistoryState()
                }
            }
            _isProcessing.value = false
        }
    }

    private fun loadBitmap(item: ScreenshotItem): Bitmap? {
        val file = File(item.filePath)
        if (file.exists() && file.canRead()) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        if (!item.uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(item.uriString)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun selectCategory(cat: EditorCategory) {
        _activeCategory.value = cat
    }

    fun setToolMode(mode: ToolMode) {
        _activeToolMode.value = mode
    }

    // --- History / Snapshot ---
    private fun pushSnapshot() {
        val currentBmp = _baseBitmap.value ?: return
        val snap = EditorStateSnapshot(
            bitmap = currentBmp.copy(Bitmap.Config.ARGB_8888, true),
            adjustSettings = _adjustSettings.value,
            activeEffect = _activeEffect.value,
            strokes = _strokes.value,
            textLayers = _textLayers.value,
            stickers = _stickers.value,
            shapes = _shapes.value,
            frameStyle = _frameStyle.value,
            callouts = _callouts.value,
            lensFlares = _lensFlares.value
        )
        undoStack.add(snap)
        if (undoStack.size > 15) undoStack.removeAt(0)
        redoStack.clear()
        updateHistoryState()
    }

    private fun updateHistoryState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentBmp = _baseBitmap.value ?: return
        val currentSnap = EditorStateSnapshot(
            bitmap = currentBmp.copy(Bitmap.Config.ARGB_8888, true),
            adjustSettings = _adjustSettings.value,
            activeEffect = _activeEffect.value,
            strokes = _strokes.value,
            textLayers = _textLayers.value,
            stickers = _stickers.value,
            shapes = _shapes.value,
            frameStyle = _frameStyle.value,
            callouts = _callouts.value,
            lensFlares = _lensFlares.value
        )
        redoStack.add(currentSnap)

        val previous = undoStack.removeAt(undoStack.lastIndex)
        restoreSnapshot(previous)
        updateHistoryState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentBmp = _baseBitmap.value ?: return
        val currentSnap = EditorStateSnapshot(
            bitmap = currentBmp.copy(Bitmap.Config.ARGB_8888, true),
            adjustSettings = _adjustSettings.value,
            activeEffect = _activeEffect.value,
            strokes = _strokes.value,
            textLayers = _textLayers.value,
            stickers = _stickers.value,
            shapes = _shapes.value,
            frameStyle = _frameStyle.value,
            callouts = _callouts.value,
            lensFlares = _lensFlares.value
        )
        undoStack.add(currentSnap)

        val next = redoStack.removeAt(redoStack.lastIndex)
        restoreSnapshot(next)
        updateHistoryState()
    }

    private fun restoreSnapshot(snap: EditorStateSnapshot) {
        _baseBitmap.value = snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        _adjustSettings.value = snap.adjustSettings
        _activeEffect.value = snap.activeEffect
        _strokes.value = snap.strokes
        _textLayers.value = snap.textLayers
        _stickers.value = snap.stickers
        _shapes.value = snap.shapes
        _frameStyle.value = snap.frameStyle
        _callouts.value = snap.callouts
        _lensFlares.value = snap.lensFlares
    }

    fun resetAll() {
        pushSnapshot()
        _originalBitmap.value?.let {
            _baseBitmap.value = it.copy(Bitmap.Config.ARGB_8888, true)
        }
        _adjustSettings.value = AdjustSettings()
        _activeEffect.value = EffectType.NONE
        _strokes.value = emptyList()
        _textLayers.value = emptyList()
        _stickers.value = emptyList()
        _shapes.value = emptyList()
        _frameStyle.value = FrameStyle.NONE
        _callouts.value = emptyList()
        _lensFlares.value = emptyList()
        _statusMessage.value = "Reset to original"
    }

    // --- Adjustments ---
    fun updateAdjustSettings(newSettings: AdjustSettings) {
        _adjustSettings.value = newSettings
    }

    fun commitAdjustments() {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val adjusted = BitmapEffectsProcessor.applyAdjustments(src, _adjustSettings.value)
            _baseBitmap.value = adjusted
            _adjustSettings.value = AdjustSettings()
            _isProcessing.value = false
            _statusMessage.value = "Adjustments applied"
        }
    }

    // --- Effects ---
    fun selectEffect(effect: EffectType) {
        _activeEffect.value = effect
    }

    fun commitEffect(effect: EffectType) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val res = BitmapEffectsProcessor.applyEffect(src, effect)
            _baseBitmap.value = res
            _activeEffect.value = EffectType.NONE
            _isProcessing.value = false
            _statusMessage.value = "${effect.displayName} applied"
        }
    }

    // --- Tools: Crop / Straighten / Liquify / Magic Wand ---
    fun applyShapeCrop(cropType: ShapeCropType) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val cropped = BitmapEffectsProcessor.cropToShape(src, cropType)
            _baseBitmap.value = cropped
            _isProcessing.value = false
            _statusMessage.value = "Cropped to ${cropType.displayName}"
        }
    }

    fun applyStraighten(angleDegrees: Float) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val straightened = BitmapEffectsProcessor.straighten(src, angleDegrees)
            _baseBitmap.value = straightened
            _isProcessing.value = false
            _statusMessage.value = "Straightened by ${angleDegrees.toInt()}°"
        }
    }

    fun applyMagicWand(x: Int, y: Int, tolerance: Float = 32f) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val selectedMask = BitmapEffectsProcessor.magicWandSelect(src, x, y, tolerance)
            _isProcessing.value = false
            _statusMessage.value = "Selected region with Magic Wand"
        }
    }

    // --- Retouch Tools ---
    fun setRetouchMode(mode: RetouchMode) {
        _activeRetouchMode.value = mode
    }

    fun applyRetouchAt(x: Float, y: Float, radius: Float = 40f) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            val result = when (_activeRetouchMode.value) {
                RetouchMode.TEETH_WHITEN -> BitmapEffectsProcessor.applyTeethWhitening(src, x, y, radius)
                RetouchMode.EYE_ENHANCE -> BitmapEffectsProcessor.applyEyeEnhance(src, x, y, radius)
                RetouchMode.EYE_COLOUR -> BitmapEffectsProcessor.applyEyeColor(src, x, y, radius, Color.CYAN)
                RetouchMode.SMOOTHING, RetouchMode.WRINKLE_REMOVE, RetouchMode.BLEMISH_FIX -> BitmapEffectsProcessor.applySkinSmoothing(src, x, y, radius)
                RetouchMode.FACE_RESHAPE -> BitmapEffectsProcessor.applyReshapeWarp(src, x, y, radius * 1.5f, 0.25f)
                RetouchMode.NOSE_RESHAPE -> BitmapEffectsProcessor.applyReshapeWarp(src, x, y, radius, -0.3f)
                RetouchMode.LIP_RESHAPE -> BitmapEffectsProcessor.applyReshapeWarp(src, x, y, radius, 0.35f)
                else -> src
            }
            _baseBitmap.value = result
            _statusMessage.value = "${_activeRetouchMode.value.displayName} applied"
        }
    }

    // --- Cutout & BG ---
    fun setCutoutMode(mode: CutoutMode) {
        _activeCutoutMode.value = mode
    }

    fun applyCutout(invert: Boolean = false) {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val cutout = BitmapEffectsProcessor.extractForegroundMask(src, invert)
            _baseBitmap.value = cutout
            _isProcessing.value = false
            _statusMessage.value = if (invert) "Background extracted" else "Subject isolated"
        }
    }

    fun applyCutoutShadow() {
        val src = _baseBitmap.value ?: return
        pushSnapshot()
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val withShadow = BitmapEffectsProcessor.applySubjectShadow(src)
            _baseBitmap.value = withShadow
            _isProcessing.value = false
            _statusMessage.value = "Subject shadow added"
        }
    }

    // --- Draw Brushes ---
    fun setBrush(brush: BrushType) {
        _activeBrush.value = brush
    }

    fun setBrushColor(color: Int) {
        _brushColor.value = color
    }

    fun setBrushSize(size: Float) {
        _brushSize.value = size
    }

    fun addStroke(stroke: DrawingStroke) {
        pushSnapshot()
        _strokes.value = _strokes.value + stroke
    }

    fun clearStrokes() {
        if (_strokes.value.isNotEmpty()) {
            pushSnapshot()
            _strokes.value = emptyList()
        }
    }

    // --- Text Layers ---
    fun addTextLayer(text: String = "Double tap to edit") {
        pushSnapshot()
        val layer = TextLayer(text = text, sizeSp = 24f)
        _textLayers.value = _textLayers.value + layer
    }

    fun updateTextLayer(layer: TextLayer) {
        _textLayers.value = _textLayers.value.map { if (it.id == layer.id) layer else it }
    }

    fun removeTextLayer(id: String) {
        pushSnapshot()
        _textLayers.value = _textLayers.value.filterNot { it.id == id }
    }

    // --- Stickers ---
    fun addSticker(glyph: String, isImage: Boolean = false) {
        pushSnapshot()
        val s = StickerItem(glyphOrUri = glyph, isImage = isImage)
        _stickers.value = _stickers.value + s
    }

    fun updateSticker(sticker: StickerItem) {
        _stickers.value = _stickers.value.map { if (it.id == sticker.id) sticker else it }
    }

    fun removeSticker(id: String) {
        pushSnapshot()
        _stickers.value = _stickers.value.filterNot { it.id == id }
    }

    // --- Shapes ---
    fun addShape(kind: ShapeKind) {
        pushSnapshot()
        val s = ShapeElement(kind = kind)
        _shapes.value = _shapes.value + s
    }

    fun updateShape(shape: ShapeElement) {
        _shapes.value = _shapes.value.map { if (it.id == shape.id) shape else it }
    }

    fun removeShape(id: String) {
        pushSnapshot()
        _shapes.value = _shapes.value.filterNot { it.id == id }
    }

    // --- Frames ---
    fun setFrameStyle(style: FrameStyle) {
        pushSnapshot()
        _frameStyle.value = style
    }

    // --- Callouts ---
    fun addCallout(style: CalloutStyle, text: String = "Note") {
        pushSnapshot()
        val c = CalloutElement(style = style, text = text)
        _callouts.value = _callouts.value + c
    }

    fun updateCallout(callout: CalloutElement) {
        _callouts.value = _callouts.value.map { if (it.id == callout.id) callout else it }
    }

    fun removeCallout(id: String) {
        pushSnapshot()
        _callouts.value = _callouts.value.filterNot { it.id == id }
    }

    // --- Lens Flares ---
    fun addLensFlare(style: LensFlareStyle) {
        pushSnapshot()
        val f = LensFlareElement(style = style)
        _lensFlares.value = _lensFlares.value + f
    }

    fun updateLensFlare(flare: LensFlareElement) {
        _lensFlares.value = _lensFlares.value.map { if (it.id == flare.id) flare else it }
    }

    fun removeLensFlare(id: String) {
        pushSnapshot()
        _lensFlares.value = _lensFlares.value.filterNot { it.id == id }
    }

    // --- Render Final Composite & Save ---
    suspend fun renderFinalBitmap(): Bitmap = withContext(Dispatchers.Default) {
        val base = _baseBitmap.value ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val adjusted = if (!_adjustSettings.value.isDefault) {
            BitmapEffectsProcessor.applyAdjustments(base, _adjustSettings.value)
        } else base

        val effectApplied = if (_activeEffect.value != EffectType.NONE) {
            BitmapEffectsProcessor.applyEffect(adjusted, _activeEffect.value)
        } else adjusted

        val composite = effectApplied.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(composite)
        val w = composite.width
        val h = composite.height

        // 1. Shapes
        DrawingAndTextEngine.renderShapes(canvas, _shapes.value, w, h)
        // 2. Vector Strokes
        DrawingAndTextEngine.renderStrokes(canvas, _strokes.value)
        // 3. Stickers
        DrawingAndTextEngine.renderStickers(canvas, _stickers.value, w, h)
        // 4. Callouts
        DrawingAndTextEngine.renderCallouts(canvas, _callouts.value, w, h)
        // 5. Text Layers
        DrawingAndTextEngine.renderTextLayers(canvas, _textLayers.value, w, h)
        // 6. Lens Flares
        DrawingAndTextEngine.renderLensFlares(canvas, _lensFlares.value, w, h)
        // 7. Frame border
        DrawingAndTextEngine.renderFrame(canvas, _frameStyle.value, w, h)

        composite
    }

    fun saveEditedImage(onSuccess: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val finalBitmap = renderFinalBitmap()
            val context = getApplication<Application>()
            val editsDir = File(context.filesDir, "edited_shots").apply { mkdirs() }
            val newFile = File(editsDir, "edited_${System.currentTimeMillis()}.jpg")

            FileOutputStream(newFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            val existing = currentScreenshot
            val newId = UUID.randomUUID().toString()
            val newItem = ScreenshotItem(
                id = newId,
                filePath = newFile.absolutePath,
                title = if (existing != null) "Edited - ${existing.title}" else "Edited Image",
                description = existing?.description ?: "Photo edited with offline studio tools",
                tags = (existing?.tags ?: emptyList()) + "Edited",
                addedOn = System.currentTimeMillis(),
                fileSize = newFile.length(),
                width = finalBitmap.width,
                height = finalBitmap.height,
                mediaType = "PHOTO"
            )

            repository.insert(newItem)
            _isProcessing.value = false
            _statusMessage.value = "Image saved successfully!"
            withContext(Dispatchers.Main) {
                onSuccess(newId)
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
