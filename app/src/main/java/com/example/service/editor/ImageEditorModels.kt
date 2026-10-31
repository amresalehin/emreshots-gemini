package com.amresalehin.emreshots.service.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

/**
 * Top-level categories for the Offline Photo Editor Studio.
 */
enum class EditorCategory(val displayName: String, val iconName: String) {
    TOOLS("Tools", "tune"),
    ADJUST("Adjust", "sliders"),
    EFFECTS("Effects", "auto_awesome"),
    RETOUCH("Retouch", "face"),
    CUTOUT_BG("Cutout & BG", "content_cut"),
    DRAW("Draw", "brush"),
    TEXT("Text", "title"),
    STICKER("Sticker", "mood"),
    SHAPES("Shapes", "shapes"),
    FRAMES("Frames", "crop_square"),
    CALLOUT("Callout", "chat_bubble"),
    LENS_FLARE("Lens Flare", "flare")
}

// -------------------------------------------------------------
// 1. TOOLS
// -------------------------------------------------------------
enum class ToolMode {
    NONE,
    LASSO_SELECTION,
    POLYGON_SELECTION,
    ELLIPTICAL_SELECTION,
    MAGIC_WAND,
    OBJECT_SELECTION,
    PATCH_TOOL,
    LIQUIFY,
    SMUDGE,
    MESH_WARP,
    PERSPECTIVE_CORRECTION,
    SHAPE_CROP,
    STRAIGHTEN_LEVEL,
    CLONE,
    DISPERSION,
    STRETCH,
    MOTION
}

enum class ShapeCropType(val displayName: String) {
    CIRCLE("Circle"),
    HEART("Heart"),
    STAR("Star"),
    DIAMOND("Diamond"),
    HEXAGON("Hexagon"),
    ROUNDED_RECT("Rounded Rect"),
    TRIANGLE("Triangle"),
    OVAL("Oval")
}

enum class LiquifyMode { PUSH, BLOAT, PUCKER }

// -------------------------------------------------------------
// 2. ADJUST
// -------------------------------------------------------------
data class AdjustSettings(
    val brightness: Float = 0f,       // -100..100
    val contrast: Float = 0f,         // -100..100
    val exposure: Float = 0f,         // -100..100
    val highlights: Float = 0f,       // -100..100
    val shadows: Float = 0f,          // -100..100
    val saturation: Float = 0f,       // -100..100
    val vibrance: Float = 0f,         // -100..100
    val temperature: Float = 0f,      // -100..100 (Cool to Warm)
    val tint: Float = 0f,             // -100..100 (Green to Magenta)
    val sharpness: Float = 0f,        // 0..100
    val blur: Float = 0f,             // 0..100
    val vignette: Float = 0f,         // 0..100
    val grain: Float = 0f,            // 0..100
    val dehaze: Float = 0f,           // 0..100
    // HSL individual color offsets (-100..100 for H, S, L)
    val hslReds: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val hslGreens: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val hslBlues: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val hslYellows: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val hslCyans: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val hslMagentas: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    // White Balance
    val whiteBalanceKelvin: Float = 6500f, // 2000K to 10000K
    // Color Balance (Shadows, Midtones, Highlights RGB offsets)
    val colorBalanceShadows: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val colorBalanceMidtones: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val colorBalanceHighlights: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    // Split Toning
    val splitToneHighlightHue: Float = 45f,
    val splitToneHighlightSat: Float = 0f,
    val splitToneShadowHue: Float = 210f,
    val splitToneShadowSat: Float = 0f,
    val splitToneBalance: Float = 0f, // -100..100
    // Gradient Map / Duotone
    val duotoneEnabled: Boolean = false,
    val duotoneColor1: Int = android.graphics.Color.BLUE,
    val duotoneColor2: Int = android.graphics.Color.YELLOW,
    // Color Splash / Replace
    val colorSplashEnabled: Boolean = false,
    val splashTargetHue: Float = 0f, // Red hue default
    val splashHueTolerance: Float = 30f,
    val colorReplaceEnabled: Boolean = false,
    val colorReplaceFrom: Int = android.graphics.Color.RED,
    val colorReplaceTo: Int = android.graphics.Color.BLUE,
    val colorReplaceTolerance: Float = 35f,
    // Levels
    val levelsInBlack: Int = 0,
    val levelsInWhite: Int = 255,
    val levelsGamma: Float = 1.0f
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 0f && exposure == 0f && highlights == 0f &&
                shadows == 0f && saturation == 0f && vibrance == 0f && temperature == 0f &&
                tint == 0f && sharpness == 0f && blur == 0f && vignette == 0f && grain == 0f &&
                dehaze == 0f && !duotoneEnabled && !colorSplashEnabled && !colorReplaceEnabled &&
                levelsInBlack == 0 && levelsInWhite == 255 && levelsGamma == 1.0f
}

// -------------------------------------------------------------
// 3. EFFECTS
// -------------------------------------------------------------
enum class EffectType(val displayName: String) {
    NONE("Original"),
    OIL_PAINTING("Oil Painting"),
    WATERCOLOR("Watercolor"),
    PENCIL_SKETCH("Pencil Sketch"),
    CARTOON("Cartoon"),
    HALFTONE("Halftone"),
    EMBOSS("Emboss"),
    MOSAIC("Mosaic"),
    PIXELATE("Pixelate"),
    KALEIDOSCOPE("Kaleidoscope"),
    DOUBLE_EXPOSURE("Double Exposure"),
    CHROMATIC_ABERRATION("Chromatic Aberration"),
    RGB_GLITCH("RGB Glitch"),
    VHS("VHS Vintage"),
    LIGHT_LEAK("Light Leak"),
    BOKEH("Bokeh Overlay"),
    RADIAL_BLUR("Radial Blur"),
    MOTION_BLUR("Motion Blur"),
    TEXTURE_OVERLAY("Texture Overlay"),
    PATTERN_OVERLAY("Pattern Overlay"),
    COLOR_HALATION("Color Halation")
}

// -------------------------------------------------------------
// 4. RETOUCH
// -------------------------------------------------------------
enum class RetouchMode(val displayName: String) {
    BLEMISH_FIX("Blemish Fix"),
    SMOOTHING("Skin Smoothing"),
    TEETH_WHITEN("Teeth Whitening"),
    EYE_ENHANCE("Eye Enhancement"),
    EYE_COLOUR("Eye Colour"),
    WRINKLE_REMOVE("Wrinkle Removal"),
    FACE_RESHAPE("Face Reshape"),
    NOSE_RESHAPE("Nose Reshape"),
    LIP_RESHAPE("Lip Reshape"),
    HAIR_RECOLOUR("Hair Recolour"),
    MAKEUP("Makeup"),
    SKIN_TONE("Skin Tone"),
    PATCH_HEALING("Patch Healing"),
    ADVANCED_CLONE("Clone Stamp"),
    SMUDGE_BRUSH("Smudge"),
    DODGE_BRUSH("Dodge"),
    BURN_BRUSH("Burn")
}

// -------------------------------------------------------------
// 5. CUTOUT & BG
// -------------------------------------------------------------
enum class CutoutMode(val displayName: String) {
    AI_SUBJECT("AI Subject"),
    PERSON("Person"),
    OBJECT("Object"),
    HAIR_REFINE("Hair Refine"),
    EDGE_BRUSH("Edge Brush"),
    RESTORE_BRUSH("Restore"),
    BG_ERASER("Background Eraser"),
    FOREGROUND_EXTRACT("Extract Foreground"),
    INVERT_MASK("Invert Mask"),
    FEATHER("Feather Mask"),
    SHADOW("Subject Shadow")
}

// -------------------------------------------------------------
// 6. DRAW
// -------------------------------------------------------------
enum class BrushType(val displayName: String) {
    PEN("Pen"),
    NEON("Neon"),
    MARKER("Marker"),
    DOTTED("Dotted"),
    ERASER("Eraser"),
    PENCIL("Pencil Texture"),
    SOFT_AIRBRUSH("Soft Airbrush"),
    HARD_AIRBRUSH("Hard Airbrush"),
    SPRAY_PAINT("Spray Paint"),
    TEXTURE_BRUSH("Texture Brush"),
    PATTERN_BRUSH("Pattern Brush"),
    WATERCOLOR_BRUSH("Watercolor"),
    OIL_BRUSH("Oil Brush"),
    CALLIGRAPHY("Calligraphy"),
    SMUDGE_BRUSH("Smudge Brush"),
    BLUR_BRUSH("Blur Brush"),
    PIXEL_BRUSH("Pixel Brush"),
    SHAPE_BRUSH("Shape Brush")
}

data class DrawingStroke(
    val brush: BrushType = BrushType.PEN,
    val points: List<android.graphics.PointF> = emptyList(),
    val color: Int = android.graphics.Color.RED,
    val size: Float = 16f,
    val alpha: Float = 1.0f
)

// -------------------------------------------------------------
// 7. TEXT
// -------------------------------------------------------------
enum class TextWarpStyle { NONE, ARCH, WAVE, FLAG }
enum class TextAlignMode { LEFT, CENTER, RIGHT }

data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Your Text Here",
    val x: Float = 0.5f, // Normalized 0..1
    val y: Float = 0.5f,
    val sizeSp: Float = 28f,
    val color: Int = android.graphics.Color.WHITE,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val isCurved: Boolean = false,
    val curveAngle: Float = 60f,
    val hasGradient: Boolean = false,
    val gradientColorEnd: Int = android.graphics.Color.CYAN,
    val strokeWidth: Float = 0f,
    val strokeColor: Int = android.graphics.Color.BLACK,
    val shadowRadius: Float = 4f,
    val shadowColor: Int = android.graphics.Color.BLACK,
    val hasGlow: Boolean = false,
    val glowColor: Int = android.graphics.Color.YELLOW,
    val warpStyle: TextWarpStyle = TextWarpStyle.NONE,
    val letterSpacing: Float = 0f,
    val alignment: TextAlignMode = TextAlignMode.CENTER
)

// -------------------------------------------------------------
// 8. STICKER
// -------------------------------------------------------------
data class StickerItem(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "Emoticons",
    val glyphOrUri: String = "✨",
    val isImage: Boolean = false,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val hasOutline: Boolean = false,
    val outlineColor: Int = android.graphics.Color.WHITE,
    val hasShadow: Boolean = true,
    val tintColor: Int? = null
)

// -------------------------------------------------------------
// 9. SHAPES
// -------------------------------------------------------------
enum class ShapeKind(val displayName: String) {
    TRIANGLE("Triangle"),
    POLYGON("Polygon"),
    ROUNDED_RECT("Rounded Rect"),
    LINE("Line"),
    CURVED_LINE("Curved Line"),
    FREEFORM("Freeform"),
    CIRCLE("Circle"),
    STAR("Star"),
    HEART("Heart"),
    DIAMOND("Diamond")
}

data class ShapeElement(
    val id: String = UUID.randomUUID().toString(),
    val kind: ShapeKind = ShapeKind.STAR,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 0.3f,
    val height: Float = 0.3f,
    val strokeColor: Int = android.graphics.Color.WHITE,
    val strokeWidth: Float = 4f,
    val fillColor: Int? = android.graphics.Color.TRANSPARENT,
    val isGradient: Boolean = false,
    val gradientColor2: Int = android.graphics.Color.MAGENTA,
    val hasShadow: Boolean = true,
    val isMask: Boolean = false
)

// -------------------------------------------------------------
// 10. FRAMES
// -------------------------------------------------------------
enum class FrameStyle(val displayName: String) {
    NONE("None"),
    ROUNDED("Rounded Frame"),
    DECORATIVE("Decorative"),
    CAMERA_VIEWFINDER("Camera Frame"),
    INSTANT_FILM("Instant Film"),
    GRADIENT_BORDER("Gradient Border"),
    PATTERN_BORDER("Pattern Border"),
    SHADOW_FRAME("Shadow Frame"),
    CUSTOM_MARGIN("Custom Frame")
}

// -------------------------------------------------------------
// 11. CALLOUT
// -------------------------------------------------------------
enum class CalloutStyle(val displayName: String) {
    SPEECH_BUBBLE("Speech Bubble"),
    THOUGHT_BUBBLE("Thought Bubble"),
    ARROW_CALLOUT("Arrow Callout"),
    LABEL_BADGE("Label Callout"),
    NUMBERED_PIN("Numbered Callout"),
    MULTI_TAIL("Multi-tail Bubble")
}

data class CalloutElement(
    val id: String = UUID.randomUUID().toString(),
    val style: CalloutStyle = CalloutStyle.SPEECH_BUBBLE,
    val text: String = "Look here!",
    val x: Float = 0.5f,
    val y: Float = 0.4f,
    val tailX: Float = 0.5f,
    val tailY: Float = 0.65f,
    val bubbleColor: Int = android.graphics.Color.WHITE,
    val textColor: Int = android.graphics.Color.BLACK,
    val borderColor: Int = android.graphics.Color.DKGRAY,
    val number: Int = 1
)

// -------------------------------------------------------------
// 12. LENS FLARE
// -------------------------------------------------------------
enum class LensFlareStyle(val displayName: String) {
    SUN_RAYS("Sun Rays"),
    ANAMORPHIC_STREAKS("Anamorphic Streaks"),
    LENS_GHOSTS("Lens Ghosts"),
    LIGHT_LEAK("Light Leak"),
    BOKEH_LIGHTS("Bokeh Lights"),
    PRISM_REFRACTION("Prism Refraction"),
    GLOW_BLOOM("Glow Bloom"),
    STAR_FILTER("Star Filter")
}

data class LensFlareElement(
    val id: String = UUID.randomUUID().toString(),
    val style: LensFlareStyle = LensFlareStyle.ANAMORPHIC_STREAKS,
    val centerX: Float = 0.35f,
    val centerY: Float = 0.35f,
    val intensity: Float = 0.8f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val tintColor: Int = android.graphics.Color.CYAN
)
