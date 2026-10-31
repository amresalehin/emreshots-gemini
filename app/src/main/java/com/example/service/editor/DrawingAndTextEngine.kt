package com.amresalehin.emreshots.service.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders vector drawing strokes, advanced typography, custom shapes, frames,
 * comic/annotation callouts, stickers, and optical lens flare effects.
 */
object DrawingAndTextEngine {

    // =========================================================================
    // 1. DRAWING BRUSHES
    // =========================================================================

    fun renderStrokes(canvas: Canvas, strokes: List<DrawingStroke>) {
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                strokeWidth = stroke.size
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                alpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
            }

            when (stroke.brush) {
                BrushType.PEN -> {
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.NEON -> {
                    // Outer neon glow
                    paint.strokeWidth = stroke.size * 2.2f
                    paint.alpha = 90
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                    // Core white beam
                    paint.strokeWidth = stroke.size * 0.65f
                    paint.color = Color.WHITE
                    paint.alpha = 255
                    canvas.drawPath(path, paint)
                }
                BrushType.MARKER -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    paint.alpha = 150
                    paint.strokeCap = Paint.Cap.SQUARE
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.DOTTED -> {
                    paint.pathEffect = DashPathEffect(floatArrayOf(stroke.size * 1.5f, stroke.size * 1.5f), 0f)
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.ERASER -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.PENCIL -> {
                    paint.strokeWidth = stroke.size * 0.7f
                    paint.alpha = 200
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.SOFT_AIRBRUSH -> {
                    paint.strokeWidth = stroke.size * 2.5f
                    paint.alpha = 40
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.SPRAY_PAINT -> {
                    paint.style = Paint.Style.FILL
                    val rand = java.util.Random(stroke.points.hashCode().toLong())
                    for (pt in stroke.points) {
                        for (i in 0 until 12) {
                            val r = rand.nextFloat() * stroke.size
                            val angle = rand.nextFloat() * 2 * PI
                            val px = (pt.x + r * cos(angle)).toFloat()
                            val py = (pt.y + r * sin(angle)).toFloat()
                            canvas.drawCircle(px, py, rand.nextFloat() * 2f + 1f, paint)
                        }
                    }
                }
                BrushType.WATERCOLOR_BRUSH -> {
                    paint.strokeWidth = stroke.size * 1.6f
                    paint.alpha = 75
                    paint.pathEffect = CornerPathEffect(10f)
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.CALLIGRAPHY -> {
                    paint.strokeCap = Paint.Cap.BUTT
                    paint.strokeWidth = stroke.size * 1.2f
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
                BrushType.PIXEL_BRUSH -> {
                    paint.style = Paint.Style.FILL
                    val step = stroke.size.coerceAtLeast(8f)
                    for (pt in stroke.points) {
                        val gx = (pt.x / step).toInt() * step
                        val gy = (pt.y / step).toInt() * step
                        canvas.drawRect(gx, gy, gx + step, gy + step, paint)
                    }
                }
                BrushType.SHAPE_BRUSH -> {
                    paint.style = Paint.Style.FILL
                    for (i in stroke.points.indices step 4) {
                        val pt = stroke.points[i]
                        canvas.drawCircle(pt.x, pt.y, stroke.size / 2f, paint)
                    }
                }
                else -> {
                    val path = buildSmoothPath(stroke.points)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    private fun buildSmoothPath(points: List<PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            path.quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    // =========================================================================
    // 2. TYPOGRAPHY ENGINE
    // =========================================================================

    fun renderTextLayers(canvas: Canvas, layers: List<TextLayer>, width: Int, height: Int) {
        for (t in layers) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = t.sizeSp * (width / 360f)
                color = t.color
                textAlign = when (t.alignment) {
                    TextAlignMode.LEFT -> Paint.Align.LEFT
                    TextAlignMode.CENTER -> Paint.Align.CENTER
                    TextAlignMode.RIGHT -> Paint.Align.RIGHT
                }
                typeface = if (t.isBold && t.isItalic) Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                else if (t.isBold) Typeface.DEFAULT_BOLD
                else if (t.isItalic) Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else Typeface.DEFAULT
                letterSpacing = t.letterSpacing
            }

            val px = t.x * width
            val py = t.y * height

            // Shadow / Glow
            if (t.hasGlow) {
                paint.setShadowLayer(t.shadowRadius * 2f, 0f, 0f, t.glowColor)
            } else if (t.shadowRadius > 0f) {
                paint.setShadowLayer(t.shadowRadius, 3f, 3f, t.shadowColor)
            }

            // Gradient
            if (t.hasGradient) {
                paint.shader = LinearGradient(
                    px - 100f, py - 50f, px + 100f, py + 50f,
                    t.color, t.gradientColorEnd,
                    Shader.TileMode.CLAMP
                )
            }

            // Curved text along circle arc
            if (t.isCurved) {
                val path = Path()
                val radius = (width * 0.35f)
                val rect = RectF(px - radius, py - radius, px + radius, py + radius)
                path.addArc(rect, 180f + (90f - t.curveAngle / 2f), t.curveAngle)
                canvas.drawTextOnPath(t.text, path, 0f, 0f, paint)
            } else {
                // Stroke outline if enabled
                if (t.strokeWidth > 0f) {
                    val strokePaint = Paint(paint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = t.strokeWidth * (width / 360f)
                        color = t.strokeColor
                        clearShadowLayer()
                    }
                    canvas.drawText(t.text, px, py, strokePaint)
                }
                canvas.drawText(t.text, px, py, paint)
            }
        }
    }

    // =========================================================================
    // 3. STICKERS & ASSETS
    // =========================================================================

    fun renderStickers(canvas: Canvas, stickers: List<StickerItem>, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (s in stickers) {
            val sx = s.x * width
            val sy = s.y * height
            val size = 64f * s.scale * (width / 360f)

            canvas.save()
            canvas.translate(sx, sy)
            canvas.rotate(s.rotation)

            if (!s.isImage) {
                paint.textSize = size
                paint.textAlign = Paint.Align.CENTER
                if (s.hasShadow) {
                    paint.setShadowLayer(8f, 3f, 3f, Color.argb(120, 0, 0, 0))
                }
                canvas.drawText(s.glyphOrUri, 0f, size * 0.35f, paint)
            }
            canvas.restore()
        }
    }

    // =========================================================================
    // 4. SHAPES
    // =========================================================================

    fun renderShapes(canvas: Canvas, shapes: List<ShapeElement>, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (s in shapes) {
            val cx = s.x * width
            val cy = s.y * height
            val sw = s.width * width
            val sh = s.height * height

            val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)

            // Fill
            s.fillColor?.let { fc ->
                paint.style = Paint.Style.FILL
                if (s.isGradient) {
                    paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, fc, s.gradientColor2, Shader.TileMode.CLAMP)
                } else {
                    paint.shader = null
                    paint.color = fc
                }
                drawShapeGeometry(canvas, s.kind, rect, paint)
            }

            // Stroke
            if (s.strokeWidth > 0f) {
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s.strokeWidth
                paint.color = s.strokeColor
                drawShapeGeometry(canvas, s.kind, rect, paint)
            }
        }
    }

    private fun drawShapeGeometry(canvas: Canvas, kind: ShapeKind, rect: RectF, paint: Paint) {
        when (kind) {
            ShapeKind.CIRCLE -> canvas.drawOval(rect, paint)
            ShapeKind.ROUNDED_RECT -> canvas.drawRoundRect(rect, rect.width() * 0.2f, rect.width() * 0.2f, paint)
            ShapeKind.LINE -> canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
            ShapeKind.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            ShapeKind.STAR -> {
                val path = Path()
                val rOuter = rect.width() / 2f
                val rInner = rOuter * 0.45f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) rOuter else rInner
                    val angle = (i * PI / 5) - PI / 2
                    val px = (rect.centerX() + r * cos(angle)).toFloat()
                    val py = (rect.centerY() + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            ShapeKind.HEART -> {
                val path = Path().apply {
                    val w = rect.width()
                    val h = rect.height()
                    moveTo(rect.left + w * 0.5f, rect.top + h * 0.85f)
                    cubicTo(rect.left + w * 0.1f, rect.top + h * 0.6f, rect.left, rect.top + h * 0.25f, rect.left + w * 0.35f, rect.top + h * 0.2f)
                    cubicTo(rect.left + w * 0.45f, rect.top + h * 0.18f, rect.left + w * 0.5f, rect.top + h * 0.3f, rect.left + w * 0.5f, rect.top + h * 0.35f)
                    cubicTo(rect.left + w * 0.5f, rect.top + h * 0.3f, rect.left + w * 0.55f, rect.top + h * 0.18f, rect.left + w * 0.65f, rect.top + h * 0.2f)
                    cubicTo(rect.left + w, rect.top + h * 0.25f, rect.left + w * 0.9f, rect.top + h * 0.6f, rect.left + w * 0.5f, rect.top + h * 0.85f)
                }
                canvas.drawPath(path, paint)
            }
            ShapeKind.DIAMOND -> {
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.centerX(), rect.bottom)
                    lineTo(rect.left, rect.centerY())
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> canvas.drawRect(rect, paint)
        }
    }

    // =========================================================================
    // 5. FRAMES
    // =========================================================================

    fun renderFrame(canvas: Canvas, style: FrameStyle, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()

        when (style) {
            FrameStyle.NONE -> Unit
            FrameStyle.ROUNDED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.04f
                paint.color = Color.WHITE
                val inset = w * 0.02f
                canvas.drawRoundRect(inset, inset, w - inset, h - inset, w * 0.08f, w * 0.08f, paint)
            }
            FrameStyle.INSTANT_FILM -> {
                // Classic Polaroid border with wide bottom chin
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                val topBorder = h * 0.04f
                val sideBorder = w * 0.04f
                val bottomBorder = h * 0.16f
                // Top
                canvas.drawRect(0f, 0f, w, topBorder, paint)
                // Left
                canvas.drawRect(0f, 0f, sideBorder, h, paint)
                // Right
                canvas.drawRect(w - sideBorder, 0f, w, h, paint)
                // Bottom
                canvas.drawRect(0f, h - bottomBorder, w, h, paint)
            }
            FrameStyle.CAMERA_VIEWFINDER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.argb(200, 255, 255, 255)
                val m = w * 0.06f
                val len = w * 0.08f
                // 4 corners
                // TL
                canvas.drawLine(m, m, m + len, m, paint)
                canvas.drawLine(m, m, m, m + len, paint)
                // TR
                canvas.drawLine(w - m, m, w - m - len, m, paint)
                canvas.drawLine(w - m, m, w - m, m + len, paint)
                // BL
                canvas.drawLine(m, h - m, m + len, h - m, paint)
                canvas.drawLine(m, h - m, m, h - m - len, paint)
                // BR
                canvas.drawLine(w - m, h - m, w - m - len, h - m, paint)
                canvas.drawLine(w - m, h - m, w - m, h - m - len, paint)
                // Center crosshair
                val cx = w / 2f
                val cy = h / 2f
                canvas.drawLine(cx - 15f, cy, cx + 15f, cy, paint)
                canvas.drawLine(cx, cy - 15f, cx, cy + 15f, paint)
            }
            FrameStyle.GRADIENT_BORDER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.04f
                paint.shader = LinearGradient(0f, 0f, w, h, Color.MAGENTA, Color.CYAN, Shader.TileMode.CLAMP)
                val inset = w * 0.02f
                canvas.drawRect(inset, inset, w - inset, h - inset, paint)
            }
            FrameStyle.SHADOW_FRAME -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = Color.WHITE
                paint.setShadowLayer(20f, 0f, 8f, Color.argb(160, 0, 0, 0))
                val inset = w * 0.05f
                canvas.drawRect(inset, inset, w - inset, h - inset, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.03f
                paint.color = Color.WHITE
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }
    }

    // =========================================================================
    // 6. CALLOUTS & SPEECH BUBBLES
    // =========================================================================

    fun renderCallouts(canvas: Canvas, callouts: List<CalloutElement>, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f * (width / 360f)
            textAlign = Paint.Align.CENTER
        }

        for (c in callouts) {
            val bx = c.x * width
            val by = c.y * height
            val tx = c.tailX * width
            val ty = c.tailY * height

            val bw = width * 0.42f
            val bh = height * 0.12f
            val rect = RectF(bx - bw / 2f, by - bh / 2f, bx + bw / 2f, by + bh / 2f)

            paint.style = Paint.Style.FILL
            paint.color = c.bubbleColor

            when (c.style) {
                CalloutStyle.SPEECH_BUBBLE -> {
                    // Rounded bubble + triangle tail
                    val path = Path().apply {
                        addRoundRect(rect, 16f, 16f, Path.Direction.CW)
                        moveTo(bx - 15f, rect.bottom)
                        lineTo(tx, ty)
                        lineTo(bx + 15f, rect.bottom)
                        close()
                    }
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = c.borderColor
                    canvas.drawPath(path, paint)
                }
                CalloutStyle.THOUGHT_BUBBLE -> {
                    canvas.drawOval(rect, paint)
                    // Little circles leading to tail
                    canvas.drawCircle(bx + (tx - bx) * 0.5f, by + (ty - by) * 0.5f, 10f, paint)
                    canvas.drawCircle(bx + (tx - bx) * 0.8f, by + (ty - by) * 0.8f, 6f, paint)
                }
                CalloutStyle.ARROW_CALLOUT -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f
                    paint.color = c.borderColor
                    canvas.drawLine(bx, by, tx, ty, paint)
                    canvas.drawCircle(tx, ty, 6f, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = c.bubbleColor
                    canvas.drawRoundRect(rect, 10f, 10f, paint)
                }
                CalloutStyle.NUMBERED_PIN -> {
                    val radius = 22f * (width / 360f)
                    paint.color = c.borderColor
                    canvas.drawCircle(tx, ty, radius, paint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = radius * 1.2f
                    canvas.drawText("${c.number}", tx, ty + radius * 0.4f, textPaint)
                    return
                }
                else -> {
                    canvas.drawRoundRect(rect, 12f, 12f, paint)
                }
            }

            textPaint.color = c.textColor
            canvas.drawText(c.text, bx, by + 8f, textPaint)
        }
    }

    // =========================================================================
    // 7. LENS FLARES
    // =========================================================================

    fun renderLensFlares(canvas: Canvas, flares: List<LensFlareElement>, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (f in flares) {
            val fx = f.centerX * width
            val fy = f.centerY * height
            val r = width * 0.3f * f.scale

            canvas.save()
            canvas.translate(fx, fy)
            canvas.rotate(f.rotation)

            when (f.style) {
                LensFlareStyle.SUN_RAYS -> {
                    paint.shader = RadialGradient(
                        0f, 0f, r,
                        intArrayOf(Color.argb((f.intensity * 230).toInt(), 255, 255, 230), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(0f, 0f, r, paint)
                    // Radiating sun spikes
                    paint.shader = null
                    paint.color = Color.argb((f.intensity * 120).toInt(), 255, 250, 200)
                    paint.strokeWidth = 3f
                    for (i in 0 until 18) {
                        val angle = (i * PI / 9)
                        val len = r * (0.8f + (i % 2) * 0.4f)
                        canvas.drawLine(0f, 0f, (cos(angle) * len).toFloat(), (sin(angle) * len).toFloat(), paint)
                    }
                }
                LensFlareStyle.ANAMORPHIC_STREAKS -> {
                    // Cinematic cyan horizontal streak
                    paint.shader = LinearGradient(
                        -width * 0.8f, 0f, width * 0.8f, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.argb((f.intensity * 200).toInt(), 50, 200, 255), Color.WHITE, Color.argb((f.intensity * 200).toInt(), 50, 200, 255), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(-width * 0.8f, -6f, width * 0.8f, 6f, paint)
                    paint.shader = RadialGradient(0f, 0f, 30f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawCircle(0f, 0f, 30f, paint)
                }
                LensFlareStyle.LENS_GHOSTS -> {
                    // Optical ghosts along opposite vector
                    paint.shader = null
                    val colors = intArrayOf(Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.GREEN)
                    for (i in 1..4) {
                        val dist = (i * 90f)
                        paint.color = Color.argb((f.intensity * 70).toInt(), Color.red(colors[i - 1]), Color.green(colors[i - 1]), Color.blue(colors[i - 1]))
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(dist, dist * 0.5f, 25f + i * 10f, paint)
                    }
                }
                LensFlareStyle.STAR_FILTER -> {
                    paint.color = Color.argb((f.intensity * 230).toInt(), 255, 255, 255)
                    paint.strokeWidth = 2.5f
                    for (i in 0 until 4) {
                        val angle = (i * PI / 4)
                        val len = r * 0.85f
                        canvas.drawLine((-cos(angle) * len).toFloat(), (-sin(angle) * len).toFloat(), (cos(angle) * len).toFloat(), (sin(angle) * len).toFloat(), paint)
                    }
                    canvas.drawCircle(0f, 0f, 12f, paint)
                }
                else -> {
                    paint.shader = RadialGradient(
                        0f, 0f, r,
                        intArrayOf(Color.argb((f.intensity * 200).toInt(), 255, 220, 150), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(0f, 0f, r, paint)
                }
            }

            canvas.restore()
        }
    }
}
