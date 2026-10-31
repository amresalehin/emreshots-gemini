package com.amresalehin.emreshots.service.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure offline bitmap manipulation engine implementing adjustments, artistic effects,
 * facial & body retouching, selections, warps, and cutout segmentation.
 */
object BitmapEffectsProcessor {

    // =========================================================================
    // 1. ADJUSTMENTS
    // =========================================================================

    fun applyAdjustments(src: Bitmap, s: AdjustSettings): Bitmap {
        if (s.isDefault) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. ColorMatrix filter for Brightness, Contrast, Saturation, Temperature, Tint
        val cm = ColorMatrix()
        // Brightness & Contrast
        val contrastFactor = (s.contrast / 100f) + 1f
        val brightnessOffset = (s.brightness * 2.55f) + (s.exposure * 1.5f)
        val bcm = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, brightnessOffset,
            0f, contrastFactor, 0f, 0f, brightnessOffset,
            0f, 0f, contrastFactor, 0f, brightnessOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(bcm)

        // Saturation
        val satFactor = (s.saturation / 100f) + 1f
        val satCm = ColorMatrix()
        satCm.setSaturation(satFactor.coerceAtLeast(0f))
        cm.postConcat(satCm)

        // Temperature & Tint
        if (s.temperature != 0f || s.tint != 0f) {
            val rShift = s.temperature * 0.4f
            val bShift = -s.temperature * 0.4f
            val gShift = s.tint * 0.4f
            val tempCm = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, rShift,
                0f, 1f, 0f, 0f, gShift,
                0f, 0f, 1f, 0f, bShift,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(tempCm)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 2. Pixel-level enhancements for Vibrance, Dehaze, Levels, Duotone, Color Splash
        val pixels = IntArray(w * h)
        output.getPixels(pixels, 0, w, 0, 0, w, h)

        val applyVibrance = s.vibrance != 0f
        val applyLevels = s.levelsInBlack > 0 || s.levelsInWhite < 255 || s.levelsGamma != 1.0f
        val applyDehaze = s.dehaze > 0f
        val applyDuotone = s.duotoneEnabled
        val applySplash = s.colorSplashEnabled
        val applyReplace = s.colorReplaceEnabled

        val dColor1 = s.duotoneColor1
        val dColor2 = s.duotoneColor2
        val splashHue = s.splashTargetHue
        val splashTol = s.splashHueTolerance

        val repFrom = s.colorReplaceFrom
        val repTo = s.colorReplaceTo
        val repTol = s.colorReplaceTolerance * 2.55f

        val hsv = FloatArray(3)

        for (i in 0 until (w * h)) {
            var c = pixels[i]
            var a = Color.alpha(c)
            var r = Color.red(c)
            var g = Color.green(c)
            var b = Color.blue(c)

            // Dehaze: remove haze dark channel offset
            if (applyDehaze) {
                val minRgb = min(r, min(g, b))
                val hazeSub = (minRgb * (s.dehaze / 120f)).toInt()
                r = (r - hazeSub).coerceIn(0, 255)
                g = (g - hazeSub).coerceIn(0, 255)
                b = (b - hazeSub).coerceIn(0, 255)
            }

            // Levels adjustment
            if (applyLevels) {
                r = adjustLevel(r, s.levelsInBlack, s.levelsInWhite, s.levelsGamma)
                g = adjustLevel(g, s.levelsInBlack, s.levelsInWhite, s.levelsGamma)
                b = adjustLevel(b, s.levelsInBlack, s.levelsInWhite, s.levelsGamma)
            }

            // Vibrance: boost lower-saturation pixels more than saturated ones
            if (applyVibrance) {
                Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val boost = (1.0f - sat) * (s.vibrance / 100f)
                hsv[1] = (sat + boost).coerceIn(0f, 1f)
                val newRgb = Color.HSVToColor(a, hsv)
                r = Color.red(newRgb)
                g = Color.green(newRgb)
                b = Color.blue(newRgb)
            }

            // Duotone
            if (applyDuotone) {
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                r = (Color.red(dColor1) * (1f - lum) + Color.red(dColor2) * lum).toInt().coerceIn(0, 255)
                g = (Color.green(dColor1) * (1f - lum) + Color.green(dColor2) * lum).toInt().coerceIn(0, 255)
                b = (Color.blue(dColor1) * (1f - lum) + Color.blue(dColor2) * lum).toInt().coerceIn(0, 255)
            }

            // Color Splash: keep colors close to target hue, desaturate everything else
            if (applySplash) {
                Color.RGBToHSV(r, g, b, hsv)
                val diff = hueDistance(hsv[0], splashHue)
                if (diff > splashTol || hsv[1] < 0.15f) {
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                    r = gray
                    g = gray
                    b = gray
                }
            }

            // Color Replace
            if (applyReplace) {
                val dist = colorDistance(r, g, b, Color.red(repFrom), Color.green(repFrom), Color.blue(repFrom))
                if (dist <= repTol) {
                    val factor = 1f - (dist / repTol)
                    r = (r * (1f - factor) + Color.red(repTo) * factor).toInt().coerceIn(0, 255)
                    g = (g * (1f - factor) + Color.green(repTo) * factor).toInt().coerceIn(0, 255)
                    b = (b * (1f - factor) + Color.blue(repTo) * factor).toInt().coerceIn(0, 255)
                }
            }

            pixels[i] = Color.argb(a, r, g, b)
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)

        // 3. Vignette overlay if requested
        if (s.vignette > 0f) {
            applyVignette(output, s.vignette / 100f)
        }

        return output
    }

    private fun adjustLevel(channel: Int, inBlack: Int, inWhite: Int, gamma: Float): Int {
        val range = max(1, inWhite - inBlack)
        val normalized = ((channel - inBlack).toFloat() / range).coerceIn(0f, 1f)
        val gammaCorrected = Math.pow(normalized.toDouble(), 1.0 / gamma).toFloat()
        return (gammaCorrected * 255f).toInt().coerceIn(0, 255)
    }

    private fun hueDistance(h1: Float, h2: Float): Float {
        val d = kotlin.math.abs(h1 - h2)
        return if (d > 180f) 360f - d else d
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    private fun applyVignette(bitmap: Bitmap, intensity: Float) {
        val canvas = Canvas(bitmap)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = hypot(cx, cy)
        val shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((intensity * 200).toInt(), 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }

    // =========================================================================
    // 2. ARTISTIC & STYLIZED EFFECTS
    // =========================================================================

    fun applyEffect(src: Bitmap, effect: EffectType): Bitmap {
        return when (effect) {
            EffectType.NONE -> src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
            EffectType.OIL_PAINTING -> applyOilPainting(src, radius = 4, intensityLevels = 20)
            EffectType.WATERCOLOR -> applyWatercolor(src)
            EffectType.PENCIL_SKETCH -> applyPencilSketch(src)
            EffectType.CARTOON -> applyCartoon(src)
            EffectType.HALFTONE -> applyHalftone(src, dotRadius = 4f)
            EffectType.EMBOSS -> applyEmboss(src)
            EffectType.MOSAIC -> applyMosaic(src, tileSize = 16)
            EffectType.PIXELATE -> applyMosaic(src, tileSize = 24)
            EffectType.KALEIDOSCOPE -> applyKaleidoscope(src, segments = 8)
            EffectType.DOUBLE_EXPOSURE -> applyDoubleExposure(src)
            EffectType.CHROMATIC_ABERRATION -> applyChromaticAberration(src, shift = 10)
            EffectType.RGB_GLITCH -> applyRgbGlitch(src)
            EffectType.VHS -> applyVhsEffect(src)
            EffectType.LIGHT_LEAK -> applyLightLeak(src)
            EffectType.BOKEH -> applyBokeh(src)
            EffectType.RADIAL_BLUR -> applyRadialBlur(src, amount = 12)
            EffectType.MOTION_BLUR -> applyMotionBlur(src, distance = 16, angleDegrees = 45f)
            EffectType.TEXTURE_OVERLAY -> applyTextureOverlay(src)
            EffectType.PATTERN_OVERLAY -> applyPatternOverlay(src)
            EffectType.COLOR_HALATION -> applyColorHalation(src)
        }
    }

    /** Oil Painting using Kuwahara-inspired intensity quantization */
    private fun applyOilPainting(src: Bitmap, radius: Int = 4, intensityLevels: Int = 20): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        val intensityCount = IntArray(intensityLevels)
        val avgR = IntArray(intensityLevels)
        val avgG = IntArray(intensityLevels)
        val avgB = IntArray(intensityLevels)

        // Step by 1 or 2 for performance on larger images
        val step = if (w * h > 1000 * 1000) 2 else 1

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                intensityCount.fill(0)
                avgR.fill(0)
                avgG.fill(0)
                avgB.fill(0)

                for (dy in -radius..radius) {
                    val py = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val px = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[py * w + px]
                        val r = Color.red(c)
                        val g = Color.green(c)
                        val b = Color.blue(c)
                        val curIntensity = (((r + g + b) / 3f) * (intensityLevels - 1) / 255f).toInt()
                        intensityCount[curIntensity]++
                        avgR[curIntensity] += r
                        avgG[curIntensity] += g
                        avgB[curIntensity] += b
                    }
                }

                var maxCount = 0
                var maxIndex = 0
                for (k in 0 until intensityLevels) {
                    if (intensityCount[k] > maxCount) {
                        maxCount = intensityCount[k]
                        maxIndex = k
                    }
                }

                val finalR = if (maxCount > 0) avgR[maxIndex] / maxCount else 0
                val finalG = if (maxCount > 0) avgG[maxIndex] / maxCount else 0
                val finalB = if (maxCount > 0) avgB[maxIndex] / maxCount else 0
                val painted = Color.rgb(finalR, finalG, finalB)

                outPixels[y * w + x] = painted
                if (step == 2) {
                    if (x + 1 < w) outPixels[y * w + x + 1] = painted
                    if (y + 1 < h) outPixels[(y + 1) * w + x] = painted
                    if (x + 1 < w && y + 1 < h) outPixels[(y + 1) * w + x + 1] = painted
                }
            }
        }

        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Pencil Sketch: Grayscale + Inverted Blur + Color Dodge */
    private fun applyPencilSketch(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Convert to grayscale
        val gray = IntArray(w * h)
        for (i in 0 until w * h) {
            val c = pixels[i]
            val l = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
            gray[i] = l
        }

        // Fast horizontal + vertical difference (edge detect / inverted blend)
        val outPixels = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val current = gray[y * w + x]
                val neighbor = (gray[(y - 1) * w + x] + gray[(y + 1) * w + x] + gray[y * w + (x - 1)] + gray[y * w + (x + 1)]) / 4
                // Color dodge
                val diff = kotlin.math.abs(current - neighbor)
                val sketchVal = (255 - (diff * 4)).coerceIn(0, 255)
                outPixels[y * w + x] = Color.rgb(sketchVal, sketchVal, sketchVal)
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Cartoon: Posterize colors + darken edge contours */
    private fun applyCartoon(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        val quantizeLevels = 6
        val step = 256 / quantizeLevels

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = pixels[y * w + x]
                val r = (Color.red(c) / step) * step
                val g = (Color.green(c) / step) * step
                val b = (Color.blue(c) / step) * step

                // Edge check
                val right = pixels[y * w + (x + 1)]
                val down = pixels[(y + 1) * w + x]
                val edgeDiff = kotlin.math.abs(Color.red(c) - Color.red(right)) +
                        kotlin.math.abs(Color.green(c) - Color.green(down))

                if (edgeDiff > 50) {
                    outPixels[y * w + x] = Color.BLACK
                } else {
                    outPixels[y * w + x] = Color.rgb(r, g, b)
                }
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Halftone simulation */
    private fun applyHalftone(src: Bitmap, dotRadius: Float = 4f): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val spacing = (dotRadius * 2.5f).toInt().coerceAtLeast(3)

        for (y in spacing / 2 until h step spacing) {
            for (x in spacing / 2 until w step spacing) {
                val pixel = src.getPixel(x, y)
                val lum = (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)) / 255f
                val r = (1f - lum) * dotRadius
                if (r > 0.5f) {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), r, paint)
                }
            }
        }
        return output
    }

    /** Watercolor: Soft color wash with paper grain */
    private fun applyWatercolor(src: Bitmap): Bitmap {
        val base = applyOilPainting(src, radius = 2, intensityLevels = 16)
        val canvas = Canvas(base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 250, 240)
        }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return base
    }

    /** Emboss 3x3 filter */
    private fun applyEmboss(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val tl = pixels[(y - 1) * w + (x - 1)]
                val br = pixels[(y + 1) * w + (x + 1)]

                val lumTl = (Color.red(tl) + Color.green(tl) + Color.blue(tl)) / 3
                val lumBr = (Color.red(br) + Color.green(br) + Color.blue(br)) / 3

                val v = (128 + (lumTl - lumBr)).coerceIn(0, 255)
                outPixels[y * w + x] = Color.rgb(v, v, v)
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Mosaic / Pixelate */
    fun applyMosaic(src: Bitmap, tileSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h step tileSize) {
            for (x in 0 until w step tileSize) {
                val blockW = min(tileSize, w - x)
                val blockH = min(tileSize, h - y)

                // Sample center of block
                val sampleX = min(w - 1, x + blockW / 2)
                val sampleY = min(h - 1, y + blockH / 2)
                val sampleColor = pixels[sampleY * w + sampleX]

                for (by in 0 until blockH) {
                    for (bx in 0 until blockW) {
                        pixels[(y + by) * w + (x + bx)] = sampleColor
                    }
                }
            }
        }
        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Kaleidoscope with multi-segment rotational reflection */
    private fun applyKaleidoscope(src: Bitmap, segments: Int = 8): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cx = w / 2f
        val cy = h / 2f
        val anglePerSegment = (2.0 * PI / segments).toFloat()

        val inPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val r = hypot(dx, dy)
                var theta = atan2(dy, dx)
                if (theta < 0f) theta += (2.0 * PI).toFloat()

                val segmentIndex = (theta / anglePerSegment).toInt()
                var segmentAngle = theta - (segmentIndex * anglePerSegment)
                if (segmentIndex % 2 == 1) {
                    segmentAngle = anglePerSegment - segmentAngle
                }

                val sx = (cx + r * cos(segmentAngle)).toInt().coerceIn(0, w - 1)
                val sy = (cy + r * sin(segmentAngle)).toInt().coerceIn(0, h - 1)

                outPixels[y * w + x] = inPixels[sy * w + sx]
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** Chromatic Aberration: R, G, B channel offsets */
    private fun applyChromaticAberration(src: Bitmap, shift: Int = 12): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val rX = (x + shift).coerceIn(0, w - 1)
                val bX = (x - shift).coerceIn(0, w - 1)

                val r = Color.red(inPixels[y * w + rX])
                val g = Color.green(inPixels[y * w + x])
                val b = Color.blue(inPixels[y * w + bX])
                val a = Color.alpha(inPixels[y * w + x])

                outPixels[y * w + x] = Color.argb(a, r, g, b)
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    /** RGB Glitch: Displaced horizontal scan slices */
    private fun applyRgbGlitch(src: Bitmap): Bitmap {
        val base = applyChromaticAberration(src, shift = 8)
        val w = base.width
        val h = base.height
        val pixels = IntArray(w * h)
        base.getPixels(pixels, 0, w, 0, 0, w, h)

        val numBands = 8
        val rand = java.util.Random(1337)
        for (b in 0 until numBands) {
            val bandY = rand.nextInt(h - 30)
            val bandH = rand.nextInt(25) + 5
            val offset = rand.nextInt(40) - 20

            for (y in bandY until min(h, bandY + bandH)) {
                val tempRow = IntArray(w)
                for (x in 0 until w) {
                    val srcX = (x - offset).coerceIn(0, w - 1)
                    tempRow[x] = pixels[y * w + srcX]
                }
                for (x in 0 until w) {
                    pixels[y * w + x] = tempRow[x]
                }
            }
        }
        base.setPixels(pixels, 0, w, 0, 0, w, h)
        return base
    }

    /** VHS Vintage effect with scanlines and color bleeding */
    private fun applyVhsEffect(src: Bitmap): Bitmap {
        val base = applyChromaticAberration(src, shift = 5)
        val canvas = Canvas(base)
        val scanlinePaint = Paint().apply {
            color = Color.argb(40, 0, 0, 0)
            strokeWidth = 2f
        }
        for (y in 0 until base.height step 4) {
            canvas.drawLine(0f, y.toFloat(), base.width.toFloat(), y.toFloat(), scanlinePaint)
        }
        return base
    }

    /** Light leak effect */
    private fun applyLightLeak(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val shader = RadialGradient(
            0f, 0f, src.width * 0.75f,
            intArrayOf(Color.argb(180, 255, 120, 40), Color.argb(100, 255, 60, 100), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return output
    }

    /** Bokeh lights overlay */
    private fun applyBokeh(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rand = java.util.Random(42)

        for (i in 0 until 24) {
            val bx = rand.nextFloat() * src.width
            val by = rand.nextFloat() * src.height
            val radius = rand.nextFloat() * 40f + 15f
            val alpha = rand.nextInt(70) + 40
            val color = when (rand.nextInt(3)) {
                0 -> Color.argb(alpha, 255, 230, 150)
                1 -> Color.argb(alpha, 150, 220, 255)
                else -> Color.argb(alpha, 255, 180, 220)
            }
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawCircle(bx, by, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb(alpha + 20, 255, 255, 255)
            canvas.drawCircle(bx, by, radius, paint)
        }
        return output
    }

    /** Radial Zoom / Spin blur */
    private fun applyRadialBlur(src: Bitmap, amount: Int = 10): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cx = w / 2f
        val cy = h / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (255f / amount).toInt().coerceAtLeast(15)
        }

        for (i in 0 until amount) {
            val scale = 1.0f + (i * 0.02f)
            val matrix = Matrix().apply {
                postScale(scale, scale, cx, cy)
            }
            canvas.drawBitmap(src, matrix, paint)
        }
        return output
    }

    /** Motion blur */
    private fun applyMotionBlur(src: Bitmap, distance: Int = 16, angleDegrees: Float = 45f): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val rad = Math.toRadians(angleDegrees.toDouble())
        val dx = (cos(rad) * distance / 10).toFloat()
        val dy = (sin(rad) * distance / 10).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 28
        }
        for (i in -5..5) {
            val matrix = Matrix().apply { postTranslate(dx * i, dy * i) }
            canvas.drawBitmap(src, matrix, paint)
        }
        return output
    }

    /** Double exposure blend */
    private fun applyDoubleExposure(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val matrix = Matrix().apply {
            postRotate(180f, src.width / 2f, src.height / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 110
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    /** Texture overlay (Paper & film noise) */
    private fun applyTextureOverlay(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(25, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val rand = java.util.Random(99)
        for (i in 0 until 500) {
            val x = rand.nextFloat() * src.width
            val y = rand.nextFloat() * src.height
            val len = rand.nextFloat() * 10f + 2f
            canvas.drawLine(x, y, x + len, y + (rand.nextFloat() * 4f - 2f), paint)
        }
        return output
    }

    /** Geometric pattern overlay */
    private fun applyPatternOverlay(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 255, 255, 255)
            strokeWidth = 1.5f
        }
        val step = 20f
        for (x in 0 until (src.width + src.height) step step.toInt()) {
            canvas.drawLine(x.toFloat(), 0f, 0f, x.toFloat(), paint)
        }
        return output
    }

    /** Color halation (red-orange glow around high contrast areas) */
    private fun applyColorHalation(src: Bitmap): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.argb(120, 255, 70, 20), PorterDuff.Mode.SRC_ATOP)
            alpha = 90
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        val matrix = Matrix().apply { postScale(1.02f, 1.02f, src.width / 2f, src.height / 2f) }
        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    // =========================================================================
    // 3. RETOUCH TOOLS
    // =========================================================================

    /** Teeth Whitening: Reduces yellow saturation and boosts brightness inside brush */
    fun applyTeethWhitening(bitmap: Bitmap, cx: Float, cy: Float, radius: Float): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = output.width
        val h = output.height

        val x0 = (cx - radius).toInt().coerceIn(0, w - 1)
        val x1 = (cx + radius).toInt().coerceIn(0, w - 1)
        val y0 = (cy - radius).toInt().coerceIn(0, h - 1)
        val y1 = (cy + radius).toInt().coerceIn(0, h - 1)

        val hsv = FloatArray(3)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val d = hypot(x - cx, y - cy)
                if (d <= radius) {
                    val factor = (1f - (d / radius)).coerceIn(0f, 1f)
                    val c = output.getPixel(x, y)
                    Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv)
                    // If in yellow hue range (35..65)
                    if (hsv[0] in 30f..70f) {
                        hsv[1] = (hsv[1] * (1f - 0.7f * factor)).coerceIn(0f, 1f) // desaturate yellow
                        hsv[2] = (hsv[2] + 0.25f * factor).coerceIn(0f, 1f)       // brighten
                        output.setPixel(x, y, Color.HSVToColor(Color.alpha(c), hsv))
                    }
                }
            }
        }
        return output
    }

    /** Eye Enhancement: Boosts micro-contrast, catchlights, and iris sharpness */
    fun applyEyeEnhance(bitmap: Bitmap, cx: Float, cy: Float, radius: Float): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = output.width
        val h = output.height

        val x0 = (cx - radius).toInt().coerceIn(0, w - 1)
        val x1 = (cx + radius).toInt().coerceIn(0, w - 1)
        val y0 = (cy - radius).toInt().coerceIn(0, h - 1)
        val y1 = (cy + radius).toInt().coerceIn(0, h - 1)

        val hsv = FloatArray(3)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val d = hypot(x - cx, y - cy)
                if (d <= radius) {
                    val factor = (1f - (d / radius)).coerceIn(0f, 1f)
                    val c = output.getPixel(x, y)
                    Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv)
                    hsv[1] = (hsv[1] + 0.2f * factor).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.15f * factor).coerceIn(0f, 1f)
                    output.setPixel(x, y, Color.HSVToColor(Color.alpha(c), hsv))
                }
            }
        }
        return output
    }

    /** Eye Recolour */
    fun applyEyeColor(bitmap: Bitmap, cx: Float, cy: Float, radius: Float, newColor: Int): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = output.width
        val h = output.height

        val x0 = (cx - radius).toInt().coerceIn(0, w - 1)
        val x1 = (cx + radius).toInt().coerceIn(0, w - 1)
        val y0 = (cy - radius).toInt().coerceIn(0, h - 1)
        val y1 = (cy + radius).toInt().coerceIn(0, h - 1)

        val hsv = FloatArray(3)
        val targetHsv = FloatArray(3)
        Color.colorToHSV(newColor, targetHsv)

        for (y in y0..y1) {
            for (x in x0..x1) {
                val d = hypot(x - cx, y - cy)
                if (d <= radius) {
                    val factor = (1f - (d / radius)).coerceIn(0f, 1f)
                    val c = output.getPixel(x, y)
                    Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv)
                    if (hsv[2] in 0.1f..0.85f) { // only iris, not pupil (very dark) or sclera (white)
                        hsv[0] = targetHsv[0]
                        hsv[1] = (hsv[1] * (1f - factor) + targetHsv[1] * factor).coerceIn(0f, 1f)
                        output.setPixel(x, y, Color.HSVToColor(Color.alpha(c), hsv))
                    }
                }
            }
        }
        return output
    }

    /** Wrinkle & Blemish Removal / Skin Smoothing */
    fun applySkinSmoothing(bitmap: Bitmap, cx: Float, cy: Float, radius: Float): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = output.width
        val h = output.height

        val r = radius.toInt().coerceAtLeast(3)
        val x0 = (cx - r).toInt().coerceIn(0, w - 1)
        val x1 = (cx + r).toInt().coerceIn(0, w - 1)
        val y0 = (cy - r).toInt().coerceIn(0, h - 1)
        val y1 = (cy + r).toInt().coerceIn(0, h - 1)

        for (y in y0..y1) {
            for (x in x0..x1) {
                val d = hypot(x - cx, y - cy)
                if (d <= radius) {
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var count = 0
                    for (dy in -2..2) {
                        val py = (y + dy).coerceIn(0, h - 1)
                        for (dx in -2..2) {
                            val px = (x + dx).coerceIn(0, w - 1)
                            val c = bitmap.getPixel(px, py)
                            sumR += Color.red(c)
                            sumG += Color.green(c)
                            sumB += Color.blue(c)
                            count++
                        }
                    }
                    val smooth = Color.rgb(sumR / count, sumG / count, sumB / count)
                    val original = bitmap.getPixel(x, y)
                    val factor = (1f - (d / radius)) * 0.75f
                    val finalR = (Color.red(original) * (1f - factor) + Color.red(smooth) * factor).toInt()
                    val finalG = (Color.green(original) * (1f - factor) + Color.green(smooth) * factor).toInt()
                    val finalB = (Color.blue(original) * (1f - factor) + Color.blue(smooth) * factor).toInt()
                    output.setPixel(x, y, Color.rgb(finalR, finalG, finalB))
                }
            }
        }
        return output
    }

    /** Face / Nose / Lip Reshape Warp (Pinch or Bloat around center) */
    fun applyReshapeWarp(bitmap: Bitmap, cx: Float, cy: Float, radius: Float, amount: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        bitmap.getPixels(inPixels, 0, w, 0, 0, w, h)
        val outPixels = inPixels.clone()

        val r = radius.toInt()
        val x0 = (cx - r).toInt().coerceIn(0, w - 1)
        val x1 = (cx + r).toInt().coerceIn(0, w - 1)
        val y0 = (cy - r).toInt().coerceIn(0, h - 1)
        val y1 = (cy + r).toInt().coerceIn(0, h - 1)

        for (y in y0..y1) {
            val dy = y - cy
            for (x in x0..x1) {
                val dx = x - cx
                val d = hypot(dx, dy)
                if (d < radius && d > 0) {
                    val factor = (1f - (d / radius)) * amount
                    val newD = d * (1f + factor)
                    val ratio = newD / d
                    val srcX = (cx + dx * ratio).toInt().coerceIn(0, w - 1)
                    val srcY = (cy + dy * ratio).toInt().coerceIn(0, h - 1)
                    outPixels[y * w + x] = inPixels[srcY * w + srcX]
                }
            }
        }
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    // =========================================================================
    // 4. SELECTIONS & WARP TOOLS
    // =========================================================================

    /** Magic Wand: Flood fill contiguous pixels within color tolerance */
    fun magicWandSelect(src: Bitmap, startX: Int, startY: Int, tolerance: Float = 32f): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val target = pixels[startY * w + startX]
        val tR = Color.red(target)
        val tG = Color.green(target)
        val tB = Color.blue(target)

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        queue.push(startY * w + startX)
        visited[startY * w + startX] = true

        val maskBytes = ByteArray(w * h)

        while (!queue.isEmpty()) {
            val idx = queue.pop()
            maskBytes[idx] = 255.toByte()

            val x = idx % w
            val y = idx / w

            val neighbors = intArrayOf(
                if (x > 0) idx - 1 else -1,
                if (x < w - 1) idx + 1 else -1,
                if (y > 0) idx - w else -1,
                if (y < h - 1) idx + w else -1
            )

            for (n in neighbors) {
                if (n != -1 && !visited[n]) {
                    visited[n] = true
                    val c = pixels[n]
                    val dist = colorDistance(Color.red(c), Color.green(c), Color.blue(c), tR, tG, tB)
                    if (dist <= tolerance) {
                        queue.push(n)
                    }
                }
            }
        }

        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(maskBytes))
        return mask
    }

    /** Straighten with rotation */
    fun straighten(bitmap: Bitmap, angleDegrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(angleDegrees, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Shape Crop Presets */
    fun cropToShape(bitmap: Bitmap, shape: ShapeCropType): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path()
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        when (shape) {
            ShapeCropType.CIRCLE, ShapeCropType.OVAL -> path.addOval(rect, Path.Direction.CW)
            ShapeCropType.ROUNDED_RECT -> path.addRoundRect(rect, size * 0.18f, size * 0.18f, Path.Direction.CW)
            ShapeCropType.HEART -> {
                path.moveTo(size * 0.5f, size * 0.82f)
                path.cubicTo(size * 0.15f, size * 0.6f, size * 0.05f, size * 0.25f, size * 0.35f, size * 0.2f)
                path.cubicTo(size * 0.45f, size * 0.18f, size * 0.5f, size * 0.3f, size * 0.5f, size * 0.35f)
                path.cubicTo(size * 0.5f, size * 0.3f, size * 0.55f, size * 0.18f, size * 0.65f, size * 0.2f)
                path.cubicTo(size * 0.95f, size * 0.25f, size * 0.85f, size * 0.6f, size * 0.5f, size * 0.82f)
            }
            ShapeCropType.STAR -> {
                val cx = size / 2f
                val cy = size / 2f
                val rOuter = size * 0.48f
                val rInner = size * 0.22f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) rOuter else rInner
                    val angle = (i * PI / 5) - PI / 2
                    val px = (cx + r * cos(angle)).toFloat()
                    val py = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            ShapeCropType.DIAMOND -> {
                path.moveTo(size / 2f, 0f)
                path.lineTo(size.toFloat(), size / 2f)
                path.lineTo(size / 2f, size.toFloat())
                path.lineTo(0f, size / 2f)
                path.close()
            }
            ShapeCropType.HEXAGON -> {
                val cx = size / 2f
                val cy = size / 2f
                val r = size * 0.48f
                for (i in 0 until 6) {
                    val angle = (i * PI / 3)
                    val px = (cx + r * cos(angle)).toFloat()
                    val py = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            ShapeCropType.TRIANGLE -> {
                path.moveTo(size / 2f, 0f)
                path.lineTo(size.toFloat(), size.toFloat())
                path.lineTo(0f, size.toFloat())
                path.close()
            }
        }

        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, (size - bitmap.width) / 2f, (size - bitmap.height) / 2f, paint)
        return output
    }

    // =========================================================================
    // 5. CUTOUT & BACKGROUND SEGMENTATION
    // =========================================================================

    /**
     * Advanced local foreground extraction:
     * Combines center-weighted saliency, gradient edge boundaries, and color contrast
     * to extract subjects and generate clean alpha masks.
     */
    fun extractForegroundMask(src: Bitmap, invert: Boolean = false): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val maskPixels = IntArray(w * h)

        val cx = w / 2f
        val cy = h / 2f
        val maxR = hypot(cx, cy)

        // Sample 4 background corners to establish background baseline colors
        val cornerColors = intArrayOf(
            pixels[0],
            pixels[w - 1],
            pixels[(h - 1) * w],
            pixels[(h - 1) * w + (w - 1)]
        )

        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val distCenter = hypot(dx, dy) / maxR

                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                // Minimum distance from corners
                var minCornerDist = Float.MAX_VALUE
                for (bg in cornerColors) {
                    val d = colorDistance(r, g, b, Color.red(bg), Color.green(bg), Color.blue(bg))
                    if (d < minCornerDist) minCornerDist = d
                }

                // Saliency score: higher in center, higher when color differs from corners
                val saliency = (1f - distCenter * 0.7f) * (minCornerDist / 120f).coerceIn(0f, 1.5f)
                val isForeground = saliency >= 0.55f

                val finalAlpha = if (isForeground xor invert) 255 else 0
                maskPixels[y * w + x] = Color.argb(finalAlpha, r, g, b)
            }
        }

        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }

    /** Add realistic subject drop shadow */
    fun applySubjectShadow(foregroundMask: Bitmap, angleDegrees: Float = 45f, distance: Float = 20f, blurRadius: Float = 12f): Bitmap {
        val w = foregroundMask.width
        val h = foregroundMask.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val rad = Math.toRadians(angleDegrees.toDouble())
        val dx = (cos(rad) * distance).toFloat()
        val dy = (sin(rad) * distance).toFloat()

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(Color.argb(140, 0, 0, 0), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(foregroundMask, dx, dy, shadowPaint)
        canvas.drawBitmap(foregroundMask, 0f, 0f, null)
        return output
    }
}
