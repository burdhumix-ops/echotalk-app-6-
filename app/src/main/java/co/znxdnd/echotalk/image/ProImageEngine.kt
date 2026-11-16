package co.znxdnd.echotalk.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    NOIR("Mono Noir"),
    WARM_FILM("Warm Film"),
    CYBER_COOL("Cyber Cool"),
    MATTE_PASTEL("Pastel"),
    GOLDEN_HOUR("Golden"),
    CRISP_STUDIO("Crisp")
}

enum class AspectPreset(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    CARD_3_4("3:4", 3f / 4f),
    WIDE_16_9("16:9", 16f / 9f)
}

enum class EditorBrushStyle(val label: String) {
    PEN("Ink Pen"),
    NEON("Neon Glow"),
    HIGHLIGHTER("Highlighter"),
    REDACT_MOSAIC("Mosaic Blur")
}

enum class TextOverlayStyle(val label: String) {
    CLEAN("Clean"),
    PILL("Pill"),
    NEON("Neon")
}

data class ColorAdjustments(
    val exposure: Float = 0f,        // -1f..1f
    val contrast: Float = 1f,        // 0.5f..1.8f
    val saturation: Float = 1f,      // 0f..2f
    val warmth: Float = 0f,          // -1f..1f
    val tint: Float = 0f,            // -1f..1f
    val clarity: Float = 0f,         // 0f..1f
    val vignette: Float = 0f,        // 0f..1f
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL,
    val filterIntensity: Float = 1f  // 0f..1f
) {
    val isIdentity: Boolean
        get() = abs(exposure) < 0.01f &&
            abs(contrast - 1f) < 0.01f &&
            abs(saturation - 1f) < 0.01f &&
            abs(warmth) < 0.01f &&
            abs(tint) < 0.01f &&
            clarity < 0.01f &&
            vignette < 0.01f &&
            filterPreset == FilterPreset.ORIGINAL
}

data class CropTransformState(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val aspectPreset: AspectPreset = AspectPreset.FREE
) {
    val hasCrop: Boolean
        get() = left > 0.005f || top > 0.005f || right < 0.995f || bottom < 0.995f
}

data class EditorStroke(
    val normalizedPoints: List<Offset>,
    val color: Color,
    val strokeWidthFraction: Float,
    val style: EditorBrushStyle = EditorBrushStyle.PEN,
    val alpha: Float = 1f
)

data class EditorTextOverlay(
    val id: String,
    val text: String,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val scale: Float = 1f,
    val color: Color = Color.White,
    val style: TextOverlayStyle = TextOverlayStyle.PILL
)

data class ImageHistogram(
    val lumaBins: List<Float>,
    val redBins: List<Float>,
    val greenBins: List<Float>,
    val blueBins: List<Float>
)

/**
 * ProImageEngine: Production-grade non-destructive image processing, 4x5 color grading matrix math,
 * Laplacian clarity sharpening, real pixel mosaic/box-blur redaction, RGB histogram computation,
 * auto-enhance tone analysis, smooth Bezier curve fitting, and high-res bitmap export.
 */
object ProImageEngine {

    private val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun presetMatrix(preset: FilterPreset): FloatArray {
        return when (preset) {
            FilterPreset.ORIGINAL -> IDENTITY_MATRIX.copyOf()
            FilterPreset.VIVID -> {
                val cm = ColorMatrix()
                cm.setSaturation(1.38f)
                val contrast = contrastMatrix(1.12f, exposureOffset = 4f)
                multiplyMatrices(contrast, cm.array)
            }
            FilterPreset.NOIR -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val contrast = contrastMatrix(1.28f, exposureOffset = -4f)
                multiplyMatrices(contrast, cm.array)
            }
            FilterPreset.WARM_FILM -> floatArrayOf(
                1.08f, 0.04f, 0.00f, 0f, 10f,
                0.02f, 1.01f, 0.00f, 0f, 4f,
                0.00f, 0.00f, 0.88f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.CYBER_COOL -> floatArrayOf(
                0.90f, 0.00f, 0.08f, 0f, -6f,
                0.02f, 1.02f, 0.06f, 0f, 2f,
                0.06f, 0.06f, 1.14f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.MATTE_PASTEL -> {
                val sat = ColorMatrix().apply { setSaturation(0.84f) }.array
                val matte = floatArrayOf(
                    0.92f, 0f, 0f, 0f, 18f,
                    0f, 0.92f, 0f, 0f, 16f,
                    0f, 0f, 0.94f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
                multiplyMatrices(matte, sat)
            }
            FilterPreset.GOLDEN_HOUR -> floatArrayOf(
                1.14f, 0.05f, 0.00f, 0f, 14f,
                0.03f, 1.04f, 0.00f, 0f, 6f,
                0.00f, 0.00f, 0.82f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.CRISP_STUDIO -> {
                val sat = ColorMatrix().apply { setSaturation(1.12f) }.array
                val c = contrastMatrix(1.18f, exposureOffset = 2f)
                multiplyMatrices(c, sat)
            }
        }
    }

    /**
     * Composes full 4x5 RGBA ColorMatrix from filter preset, intensity, exposure, contrast,
     * saturation (BT.709), temperature warmth, and green/magenta tint.
     */
    fun buildColorMatrix(adj: ColorAdjustments): FloatArray {
        val presetMat = interpolateMatrix(
            IDENTITY_MATRIX,
            presetMatrix(adj.filterPreset),
            adj.filterIntensity.coerceIn(0f, 1f)
        )

        val satCm = ColorMatrix().apply { setSaturation(adj.saturation.coerceIn(0f, 2.2f)) }
        val exposureShift = adj.exposure.coerceIn(-1f, 1f) * 65f
        val contrastMat = contrastMatrix(adj.contrast.coerceIn(0.4f, 2f), exposureShift)

        // Kelvin warmth & tint matrix
        val w = adj.warmth.coerceIn(-1f, 1f)
        val t = adj.tint.coerceIn(-1f, 1f)
        val wbMat = floatArrayOf(
            1f + (w * 0.16f), 0f, 0f, 0f, w * 14f,
            0f, 1f + (t * 0.14f), 0f, 0f, t * 12f,
            0f, 0f, 1f - (w * 0.16f), 0f, -w * 14f,
            0f, 0f, 0f, 1f, 0f
        )

        var combined = multiplyMatrices(satCm.array, presetMat)
        combined = multiplyMatrices(wbMat, combined)
        combined = multiplyMatrices(contrastMat, combined)
        return combined
    }

    private fun contrastMatrix(contrast: Float, exposureOffset: Float = 0f): FloatArray {
        val translate = ((1f - contrast) * 128f) + exposureOffset
        return floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    }

    private fun interpolateMatrix(a: FloatArray, b: FloatArray, t: Float): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 20) {
            out[i] = a[i] + (b[i] - a[i]) * t
        }
        return out
    }

    private fun multiplyMatrices(m1: FloatArray, m2: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            val rowIdx = row * 5
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += m1[rowIdx + k] * m2[k * 5 + col]
                }
                if (col == 4) sum += m1[rowIdx + 4]
                out[rowIdx + col] = sum
            }
        }
        return out
    }

    /**
     * Enforces centered aspect ratio constraints on a normalized [0f..1f] crop rectangle.
     */
    fun enforceAspectRatio(
        state: CropTransformState,
        preset: AspectPreset,
        imageAspectRatio: Float = 1f
    ): CropTransformState {
        if (preset == AspectPreset.FREE || preset.ratio == null) {
            return state.copy(aspectPreset = AspectPreset.FREE)
        }
        val safeImageRatio = imageAspectRatio.coerceIn(0.25f, 4f)
        val normRatio = (preset.ratio / safeImageRatio).coerceIn(0.2f, 5f)
        val centerX = ((state.left + state.right) * 0.5f).coerceIn(0.2f, 0.8f)
        val centerY = ((state.top + state.bottom) * 0.5f).coerceIn(0.2f, 0.8f)

        var halfW = ((state.right - state.left) * 0.5f).coerceIn(0.15f, 0.5f)
        var halfH = halfW / normRatio
        if (halfH > 0.5f) {
            halfH = 0.5f
            halfW = (halfH * normRatio).coerceAtMost(0.5f)
        }
        val l = (centerX - halfW).coerceIn(0f, 1f - halfW * 2f)
        val t = (centerY - halfH).coerceIn(0f, 1f - halfH * 2f)
        return state.copy(
            left = l,
            top = t,
            right = (l + halfW * 2f).coerceIn(0.1f, 1f),
            bottom = (t + halfH * 2f).coerceIn(0.1f, 1f),
            aspectPreset = preset
        )
    }

    /**
     * Computes 32-bin normalized RGB & Luma histogram from sampled bitmap pixels.
     */
    fun computeHistogram(bitmap: Bitmap, bins: Int = 32): ImageHistogram {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0 || bins <= 0) {
            val empty = List(bins.coerceAtLeast(1)) { 0f }
            return ImageHistogram(empty, empty, empty, empty)
        }

        val stepX = max(1, w / 96)
        val stepY = max(1, h / 96)
        val lumaCounts = IntArray(bins)
        val rCounts = IntArray(bins)
        val gCounts = IntArray(bins)
        val bCounts = IntArray(bins)
        var sampleCount = 0

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val px = bitmap.getPixel(x, y)
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt().coerceIn(0, 255)

                lumaCounts[(luma * bins / 256).coerceIn(0, bins - 1)]++
                rCounts[(r * bins / 256).coerceIn(0, bins - 1)]++
                gCounts[(g * bins / 256).coerceIn(0, bins - 1)]++
                bCounts[(b * bins / 256).coerceIn(0, bins - 1)]++
                sampleCount++
            }
        }

        fun normalize(arr: IntArray): List<Float> {
            val peak = arr.maxOrNull()?.coerceAtLeast(1) ?: 1
            return arr.map { (it.toFloat() / peak.toFloat()).coerceIn(0f, 1f) }
        }

        return ImageHistogram(
            lumaBins = normalize(lumaCounts),
            redBins = normalize(rCounts),
            greenBins = normalize(gCounts),
            blueBins = normalize(bCounts)
        )
    }

    /**
     * Auto-Enhance tone analyzer: stretches flat dynamic range using 5th/95th percentile
     * luminance bounds and adds subtle studio clarity & vibrancy without clipping highlights.
     */
    fun computeAutoEnhance(bitmap: Bitmap): ColorAdjustments {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return ColorAdjustments(contrast = 1.12f, saturation = 1.15f, clarity = 0.25f)

        val lumaHist = IntArray(256)
        var total = 0
        var sumR = 0L
        var sumB = 0L
        val stepX = max(1, w / 80)
        val stepY = max(1, h / 80)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val px = bitmap.getPixel(x, y)
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt().coerceIn(0, 255)
                lumaHist[luma]++
                sumR += r
                sumB += b
                total++
            }
        }
        if (total == 0) return ColorAdjustments()

        var acc = 0
        var p5 = 0
        var p95 = 255
        var foundLow = false
        for (i in 0..255) {
            acc += lumaHist[i]
            if (!foundLow && acc >= total * 0.05) {
                p5 = i
                foundLow = true
            }
            if (acc >= total * 0.95) {
                p95 = i
                break
            }
        }
        val spread = (p95 - p5).coerceAtLeast(32)
        val midPoint = (p5 + p95) / 2f
        val targetExposure = ((128f - midPoint) / 255f).coerceIn(-0.28f, 0.28f)
        val targetContrast = (210f / spread.toFloat()).coerceIn(1.04f, 1.32f)

        val avgR = sumR.toFloat() / total
        val avgB = sumB.toFloat() / total
        val warmthNudge = ((avgB - avgR) / 255f * 0.35f).coerceIn(-0.18f, 0.18f)

        return ColorAdjustments(
            exposure = targetExposure,
            contrast = targetContrast,
            saturation = 1.14f,
            warmth = warmthNudge,
            clarity = 0.28f,
            vignette = 0.12f
        )
    }

    /**
     * Unsharp-Mask 3x3 sharpening / local clarity kernel blended by [strength] in [0f..1f].
     */
    fun applyClarityKernel(source: Bitmap, strength: Float): Bitmap {
        val alpha = strength.coerceIn(0f, 1f)
        if (alpha <= 0.02f) return source

        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return source

        val srcPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        srcPixels.copyInto(outPixels)

        val k = alpha * 0.65f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val c = srcPixels[row + x]
                val u = srcPixels[row - w + x]
                val d = srcPixels[row + w + x]
                val l = srcPixels[row + x - 1]
                val r = srcPixels[row + x + 1]

                val ca = (c ushr 24) and 0xFF
                val cr = (c ushr 16) and 0xFF
                val cg = (c ushr 8) and 0xFF
                val cb = c and 0xFF

                val lapR = 4 * cr - ((u ushr 16) and 0xFF) - ((d ushr 16) and 0xFF) - ((l ushr 16) and 0xFF) - ((r ushr 16) and 0xFF)
                val lapG = 4 * cg - ((u ushr 8) and 0xFF) - ((d ushr 8) and 0xFF) - ((l ushr 8) and 0xFF) - ((r ushr 8) and 0xFF)
                val lapB = 4 * cb - (u and 0xFF) - (d and 0xFF) - (l and 0xFF) - (r and 0xFF)

                val nr = (cr + (lapR * k)).roundToInt().coerceIn(0, 255)
                val ng = (cg + (lapG * k)).roundToInt().coerceIn(0, 255)
                val nb = (cb + (lapB * k)).roundToInt().coerceIn(0, 255)
                outPixels[row + x] = (ca shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return outBitmap
    }

    /**
     * Real Mosaic Pixelation redaction restricted strictly to REDACT_MOSAIC brush stroke envelopes.
     */
    fun applyRedactMosaic(
        target: Bitmap,
        strokes: List<EditorStroke>,
        blockSizePx: Int = 18
    ) {
        val redactStrokes = strokes.filter { it.style == EditorBrushStyle.REDACT_MOSAIC && it.normalizedPoints.isNotEmpty() }
        if (redactStrokes.isEmpty()) return

        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) return

        val minDim = min(w, h).toFloat()
        val bs = blockSizePx.coerceIn(6, 48)
        val copy = target.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(w * h)
        target.getPixels(pixels, 0, w, 0, 0, w, h)
        val origPixels = IntArray(w * h)
        copy.getPixels(origPixels, 0, w, 0, 0, w, h)
        copy.recycle()

        for (stroke in redactStrokes) {
            val radius = (stroke.strokeWidthFraction * minDim * 0.55f).coerceAtLeast(10f)
            val radiusSq = radius * radius
            for (pt in stroke.normalizedPoints) {
                val cx = (pt.x * w).roundToInt()
                val cy = (pt.y * h).roundToInt()
                val rInt = radius.roundToInt()
                val startY = ((cy - rInt).coerceAtLeast(0) / bs) * bs
                val endY = (cy + rInt).coerceAtMost(h - 1)
                val startX = ((cx - rInt).coerceAtLeast(0) / bs) * bs
                val endX = (cx + rInt).coerceAtMost(w - 1)

                var by = startY
                while (by <= endY) {
                    var bx = startX
                    while (bx <= endX) {
                        val blockMidX = min(w - 1, bx + bs / 2)
                        val blockMidY = min(h - 1, by + bs / 2)
                        val dx = (blockMidX - cx).toFloat()
                        val dy = (blockMidY - cy).toFloat()
                        if (dx * dx + dy * dy <= radiusSq) {
                            val blockColor = origPixels[blockMidY * w + blockMidX]
                            val maxPy = min(h, by + bs)
                            val maxPx = min(w, bx + bs)
                            for (py in by until maxPy) {
                                val rowOff = py * w
                                for (px in bx until maxPx) {
                                    pixels[rowOff + px] = blockColor
                                }
                            }
                        }
                        bx += bs
                    }
                    by += bs
                }
            }
        }
        target.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Smooth Midpoint Quadratic Bezier Android Path builder for crisp stroke rendering.
     */
    fun buildSmoothAndroidPath(points: List<Offset>, width: Float, height: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        val x0 = points[0].x * width
        val y0 = points[0].y * height
        path.moveTo(x0, y0)
        if (points.size == 1) {
            path.lineTo(x0 + 0.5f, y0 + 0.5f)
            return path
        }
        for (i in 1 until points.size) {
            val prevX = points[i - 1].x * width
            val prevY = points[i - 1].y * height
            val curX = points[i].x * width
            val curY = points[i].y * height
            val midX = (prevX + curX) * 0.5f
            val midY = (prevY + curY) * 0.5f
            path.quadTo(prevX, prevY, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * width, last.y * height)
        return path
    }

    /**
     * Smooth Midpoint Quadratic Bezier Jetpack Compose Path builder for 60fps UI drawing.
     */
    fun buildSmoothComposePath(points: List<Offset>): androidx.compose.ui.graphics.Path {
        val path = androidx.compose.ui.graphics.Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            path.lineTo(points[0].x + 0.5f, points[0].y + 0.5f)
            return path
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val midX = (prev.x + cur.x) * 0.5f
            val midY = (prev.y + cur.y) * 0.5f
            path.quadraticTo(prev.x, prev.y, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x, last.y)
        return path
    }

    /**
     * Full non-destructive composite render pipeline:
     * Transform (Rotate/Flip/Crop) -> 4x5 Color Matrix -> Clarity Kernel -> Optical Vignette ->
     * Mosaic Redaction -> Smooth Bezier & Neon Strokes -> Positioned Text Layers.
     */
    fun renderFinalBitmap(
        source: Bitmap,
        adjustments: ColorAdjustments,
        transform: CropTransformState,
        strokes: List<EditorStroke>,
        textOverlays: List<EditorTextOverlay>,
        maxOutputDim: Int = 1280
    ): Bitmap {
        // 1. Rotate / Flip source first
        val androidMatrix = android.graphics.Matrix()
        if (transform.rotationDegrees != 0) {
            androidMatrix.postRotate(transform.rotationDegrees.toFloat())
        }
        val scaleX = if (transform.flipHorizontal) -1f else 1f
        val scaleY = if (transform.flipVertical) -1f else 1f
        if (scaleX < 0f || scaleY < 0f) {
            androidMatrix.postScale(scaleX, scaleY)
        }
        val oriented = if (!androidMatrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, androidMatrix, true)
        } else {
            source
        }

        // 2. Normalized crop extraction
        val cropL = (transform.left * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
        val cropT = (transform.top * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
        val cropR = (transform.right * oriented.width).roundToInt().coerceIn(cropL + 1, oriented.width)
        val cropB = (transform.bottom * oriented.height).roundToInt().coerceIn(cropT + 1, oriented.height)
        val cropW = (cropR - cropL).coerceAtLeast(1)
        val cropH = (cropB - cropT).coerceAtLeast(1)

        val longest = max(cropW, cropH)
        val outScale = if (longest > maxOutputDim) maxOutputDim.toFloat() / longest.toFloat() else 1f
        val outW = (cropW * outScale).roundToInt().coerceAtLeast(1)
        val outH = (cropH * outScale).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 3. Draw cropped + color graded base
        val gradedPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (!adjustments.isIdentity) {
            gradedPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix(buildColorMatrix(adjustments)))
        }
        canvas.drawBitmap(
            oriented,
            Rect(cropL, cropT, cropR, cropB),
            Rect(0, 0, outW, outH),
            gradedPaint
        )
        if (oriented !== source) oriented.recycle()

        // 4. Optional clarity unsharp pass
        val sharpened = if (adjustments.clarity > 0.02f) {
            applyClarityKernel(output, adjustments.clarity).also {
                if (it !== output) output.recycle()
            }
        } else {
            output
        }
        val finalBitmap = if (sharpened.isMutable) {
            sharpened
        } else {
            sharpened.copy(Bitmap.Config.ARGB_8888, true).also { sharpened.recycle() }
        }
        val finalCanvas = Canvas(finalBitmap)

        // 5. Optical vignette falloff
        if (adjustments.vignette > 0.01f) {
            val radius = max(outW, outH) * 0.72f
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    outW * 0.5f,
                    outH * 0.5f,
                    radius,
                    intArrayOf(0x00000000, ((adjustments.vignette.coerceIn(0f, 1f) * 195).toInt() shl 24)),
                    floatArrayOf(0.45f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            finalCanvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), vigPaint)
        }

        // 6. Real pixel mosaic redaction
        applyRedactMosaic(finalBitmap, strokes, blockSizePx = max(12, min(outW, outH) / 48))

        // 7. Smooth non-mosaic vector strokes (Pen / Neon Glow / Highlighter)
        val minDim = min(outW, outH).toFloat()
        for (stroke in strokes) {
            if (stroke.style == EditorBrushStyle.REDACT_MOSAIC || stroke.normalizedPoints.isEmpty()) continue
            val path = buildSmoothAndroidPath(stroke.normalizedPoints, outW.toFloat(), outH.toFloat())
            val widthPx = (stroke.strokeWidthFraction * minDim).coerceAtLeast(2f)
            val argb = stroke.color.toArgb()

            if (stroke.style == EditorBrushStyle.NEON) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = argb
                    alpha = 85
                    style = Paint.Style.STROKE
                    strokeWidth = widthPx * 2.4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                finalCanvas.drawPath(path, glowPaint)
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = widthPx * 0.65f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                finalCanvas.drawPath(path, corePaint)
            } else {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = argb
                    alpha = if (stroke.style == EditorBrushStyle.HIGHLIGHTER) 115 else (stroke.alpha * 255).roundToInt().coerceIn(0, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = widthPx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                finalCanvas.drawPath(path, strokePaint)
            }
        }

        // 8. Text overlays
        for (overlay in textOverlays) {
            if (overlay.text.isBlank()) continue
            val textSizePx = (minDim * 0.055f * overlay.scale).coerceIn(18f, minDim * 0.25f)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlay.color.toArgb()
                this.textSize = textSizePx
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                if (overlay.style == TextOverlayStyle.NEON) {
                    setShadowLayer(textSizePx * 0.45f, 0f, 0f, overlay.color.toArgb())
                } else {
                    setShadowLayer(4f, 0f, 2f, 0x88000000.toInt())
                }
            }
            val cx = overlay.xFraction.coerceIn(0.05f, 0.95f) * outW
            val cy = overlay.yFraction.coerceIn(0.05f, 0.95f) * outH
            val textWidth = textPaint.measureText(overlay.text)
            val fm = textPaint.fontMetrics
            val baselineY = cy - (fm.ascent + fm.descent) * 0.5f

            if (overlay.style == TextOverlayStyle.PILL) {
                val padX = textSizePx * 0.55f
                val padY = textSizePx * 0.30f
                val pillRect = RectF(
                    cx - textWidth * 0.5f - padX,
                    cy - (fm.descent - fm.ascent) * 0.5f - padY,
                    cx + textWidth * 0.5f + padX,
                    cy + (fm.descent - fm.ascent) * 0.5f + padY
                )
                val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCC0F172A.toInt()
                    style = Paint.Style.FILL
                }
                finalCanvas.drawRoundRect(pillRect, textSizePx * 0.45f, textSizePx * 0.45f, pillPaint)
            }
            finalCanvas.drawText(overlay.text, cx, baselineY, textPaint)
        }

        return finalBitmap
    }

    suspend fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "echotalk_edited_"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outFile = File(context.cacheDir, "$prefix${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
            }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a rendered studio Bitmap cleanly to Android Pictures/EchoTalk using
     * scoped MediaStore on Android 10+ (zero broad storage permissions required).
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String = "EchoTalk_${System.currentTimeMillis()}.jpg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/EchoTalk")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create gallery entry"))
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Pictures/EchoTalk/$displayName")
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val outFile = File(dir, displayName)
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                }
                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveMediaUrlToGallery(
        context: Context,
        mediaUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap: Bitmap = when {
                mediaUrl.startsWith("/") -> BitmapFactory.decodeFile(mediaUrl)
                mediaUrl.startsWith("file://") -> Uri.parse(mediaUrl).path?.let { BitmapFactory.decodeFile(it) }
                mediaUrl.startsWith("content://") -> context.contentResolver.openInputStream(Uri.parse(mediaUrl))?.use {
                    BitmapFactory.decodeStream(it)
                }
                mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://") -> URL(mediaUrl).openStream().use {
                    BitmapFactory.decodeStream(it)
                }
                else -> null
            } ?: return@withContext Result.failure(IllegalArgumentException("Image source unavailable"))
            try {
                saveBitmapToGallery(context, bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
