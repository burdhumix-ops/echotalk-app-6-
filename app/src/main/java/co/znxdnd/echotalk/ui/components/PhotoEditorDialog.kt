package co.znxdnd.echotalk.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.image.*
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

enum class StudioEditorTab(val label: String) {
    FILTERS("Filters"),
    ADJUST("Adjust"),
    CROP("Crop"),
    DRAW("Draw"),
    TEXT("Text")
}

private enum class AdjustParam(val label: String, val min: Float, val max: Float, val default: Float) {
    EXPOSURE("Exposure", -0.8f, 0.8f, 0f),
    CONTRAST("Contrast", 0.6f, 1.6f, 1f),
    SATURATION("Saturation", 0f, 1.9f, 1f),
    WARMTH("Warmth", -0.8f, 0.8f, 0f),
    TINT("Tint", -0.8f, 0.8f, 0f),
    CLARITY("Clarity", 0f, 1f, 0f),
    VIGNETTE("Vignette", 0f, 1f, 0f)
}

private data class EditorSnapshot(
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val transform: CropTransformState = CropTransformState(),
    val strokes: List<EditorStroke> = emptyList(),
    val textOverlays: List<EditorTextOverlay> = emptyList()
)

private val STUDIO_COLORS = listOf(
    Color(0xFFFFFFFF),
    Color(0xFF38BDF8),
    Color(0xFF6366F1),
    Color(0xFF34D399),
    Color(0xFFFBBF24),
    Color(0xFFFB7185),
    Color(0xFFC084FC),
    Color(0xFF0F172A)
)

private fun loadEditorBitmap(context: Context, uri: Uri?): Bitmap {
    if (uri != null) {
        try {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                val downloaded = java.net.URL(uri.toString()).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (downloaded != null) {
                    return if (downloaded.config == Bitmap.Config.ARGB_8888) downloaded else downloaded.copy(Bitmap.Config.ARGB_8888, false)
                }
            } else if (scheme == null || scheme == "file") {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                        if (decoded != null) {
                            return if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (decoded != null) {
                    return if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } catch (_: Throwable) {}
    }

    val rawRes = BitmapFactory.decodeResource(context.resources, R.drawable.ic_echotalk_logo_1792796066052)
    if (rawRes != null) {
        return if (rawRes.config == Bitmap.Config.ARGB_8888) rawRes else rawRes.copy(Bitmap.Config.ARGB_8888, false)
    }

    val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_echotalk_logo_1792796066052)
    if (drawable != null) {
        drawable.setBounds(0, 0, 720, 720)
        drawable.draw(canvas)
    } else {
        canvas.drawColor(android.graphics.Color.rgb(15, 23, 42))
    }
    return bmp
}

private fun buildOrientedPreviewBitmap(source: Bitmap, transform: CropTransformState, applyCrop: Boolean): Bitmap {
    val matrix = Matrix()
    if (transform.rotationDegrees != 0) matrix.postRotate(transform.rotationDegrees.toFloat())
    val sx = if (transform.flipHorizontal) -1f else 1f
    val sy = if (transform.flipVertical) -1f else 1f
    if (sx < 0f || sy < 0f) matrix.postScale(sx, sy)
    val oriented = if (!matrix.isIdentity) {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
        source
    }
    if (!applyCrop || !transform.hasCrop) return oriented
    val l = (transform.left * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
    val t = (transform.top * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
    val r = (transform.right * oriented.width).roundToInt().coerceIn(l + 1, oriented.width)
    val b = (transform.bottom * oriented.height).roundToInt().coerceIn(t + 1, oriented.height)
    return Bitmap.createBitmap(oriented, l, t, (r - l).coerceAtLeast(1), (b - t).coerceAtLeast(1))
}

private fun computeFittedRect(container: IntSize, imgWidth: Int, imgHeight: Int): Rect {
    if (container.width <= 0 || container.height <= 0 || imgWidth <= 0 || imgHeight <= 0) {
        return Rect.Zero
    }
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val scale = min(cw / imgWidth.toFloat(), ch / imgHeight.toFloat())
    val rw = imgWidth * scale
    val rh = imgHeight * scale
    val left = (cw - rw) * 0.5f
    val top = (ch - rh) * 0.5f
    return Rect(left, top, left + rw, top + rh)
}

@Composable
fun PhotoEditorDialog(
    onSend: (isViewOnce: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialImageUri: Uri? = null,
    onSendRendered: ((isViewOnce: Boolean, renderedFilePath: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var selectedUri by remember(initialImageUri) { mutableStateOf(initialImageUri) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            haptic.performPixelClick(view)
            selectedUri = uri
        }
    }

    LaunchedEffect(selectedUri) {
        sourceBitmap = withContext(Dispatchers.IO) { loadEditorBitmap(context, selectedUri) }
    }

    var activeTab by remember { mutableStateOf(StudioEditorTab.FILTERS) }
    var activeAdjustParam by remember { mutableStateOf(AdjustParam.EXPOSURE) }
    var activeBrushStyle by remember { mutableStateOf(EditorBrushStyle.PEN) }
    var brushColor by remember { mutableStateOf(STUDIO_COLORS[1]) }
    var brushSizePx by remember { mutableFloatStateOf(10f) }

    var state by remember { mutableStateOf(EditorSnapshot()) }
    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    fun pushState(next: EditorSnapshot, recordHistory: Boolean = true) {
        if (recordHistory && next != state) {
            undoStack.add(state)
            if (undoStack.size > 25) undoStack.removeAt(0)
            redoStack.clear()
        }
        state = next
    }

    var activeStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedTextId by remember { mutableStateOf<String?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var editingTextId by remember { mutableStateOf<String?>(null) }
    var showHistogram by remember { mutableStateOf(true) }
    var isComparing by remember { mutableStateOf(false) }
    var isViewOnce by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val previewBitmap = remember(sourceBitmap, state.transform, activeTab) {
        sourceBitmap?.let {
            buildOrientedPreviewBitmap(it, state.transform, applyCrop = activeTab != StudioEditorTab.CROP)
        }
    }
    val histogramProxy = remember(previewBitmap) {
        previewBitmap?.let { bmp ->
            val maxSide = 96f
            val scale = min(maxSide / bmp.width.coerceAtLeast(1), maxSide / bmp.height.coerceAtLeast(1)).coerceAtMost(1f)
            val w = (bmp.width * scale).roundToInt().coerceAtLeast(16)
            val h = (bmp.height * scale).roundToInt().coerceAtLeast(16)
            Bitmap.createScaledBitmap(bmp, w, h, true)
        }
    }
    val histogram = remember(histogramProxy, state.adjustments, showHistogram, isComparing) {
        if (!showHistogram) null else histogramProxy?.let { proxy ->
            if (isComparing) {
                ProImageEngine.computeHistogram(proxy)
            } else {
                val graded = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
                val c = AndroidCanvas(graded)
                val p = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(ProImageEngine.buildColorMatrix(state.adjustments))
                }
                c.drawBitmap(proxy, 0f, 0f, p)
                val hist = ProImageEngine.computeHistogram(graded)
                graded.recycle()
                hist
            }
        }
    }
    val fittedRect = remember(canvasSize, previewBitmap) {
        val bmp = previewBitmap
        if (bmp == null) Rect.Zero else computeFittedRect(canvasSize, bmp.width, bmp.height)
    }

    val liveColorMatrix = remember(state.adjustments) {
        ColorMatrix(ProImageEngine.buildColorMatrix(state.adjustments))
    }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF060913)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row 1: Close + Studio Identity + Pick Photo + Pinned View-Once Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { if (!isExporting) onDismiss() },
                            modifier = Modifier.testTag("photo_editor_close")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "PRO STUDIO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            val bmpInfo = previewBitmap?.let { "${it.width}×${it.height}" } ?: "Loading..."
                            val rotTag = if (state.transform.rotationDegrees != 0) " • ${state.transform.rotationDegrees}°" else ""
                            Text(
                                text = "$bmpInfo$rotTag",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                pickPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("photo_editor_pick_photo")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Photo", tint = Color(0xFF38BDF8))
                        }
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performMessengerFlameTap(view)
                                    isViewOnce = !isViewOnce
                                }
                                .testTag("photo_editor_view_once"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isViewOnce) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🔥", fontSize = 11.sp)
                                Text(
                                    text = if (isViewOnce) "Once" else "Keep",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isViewOnce) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Utility Row 2: Undo, Redo, Auto-Enhance, Histogram, Hold-to-Compare
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    redoStack.add(state)
                                    state = undoStack.removeAt(undoStack.size - 1)
                                    haptic.performPixelTick(view)
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.testTag("photo_editor_undo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack.add(state)
                                    state = redoStack.removeAt(redoStack.size - 1)
                                    haptic.performPixelTick(view)
                                }
                            },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.testTag("photo_editor_redo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                        }
                        IconButton(
                            onClick = {
                                val bmp = previewBitmap ?: return@IconButton
                                haptic.performPixelConfirm(view)
                                val autoAdj = ProImageEngine.computeAutoEnhance(bmp)
                                pushState(state.copy(adjustments = autoAdj))
                            },
                            modifier = Modifier.testTag("photo_editor_auto_enhance")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Enhance", tint = Color(0xFFFBBF24))
                        }
                        IconButton(
                            onClick = {
                                haptic.performPixelTick(view)
                                showHistogram = !showHistogram
                            },
                            modifier = Modifier.testTag("photo_editor_histogram_toggle")
                        ) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Histogram",
                                tint = if (showHistogram) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.55f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isComparing = true
                                            haptic.performPixelLowTick(view)
                                            tryAwaitRelease()
                                            isComparing = false
                                        }
                                    )
                                }
                                .testTag("photo_editor_compare"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Compare,
                                contentDescription = "Hold to Compare",
                                tint = if (isComparing) Color(0xFF34D399) else Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                val src = sourceBitmap ?: return@IconButton
                                if (isExporting) return@IconButton
                                isExporting = true
                                scope.launch {
                                    val savedResult = withContext(Dispatchers.Default) {
                                        val finalBmp = ProImageEngine.renderFinalBitmap(
                                            source = src,
                                            adjustments = state.adjustments,
                                            transform = state.transform,
                                            strokes = state.strokes,
                                            textOverlays = state.textOverlays
                                        )
                                        val res = ProImageEngine.saveBitmapToGallery(context, finalBmp)
                                        finalBmp.recycle()
                                        res
                                    }
                                    isExporting = false
                                    savedResult.onSuccess {
                                        haptic.performPixelConfirm(view)
                                        android.widget.Toast.makeText(context, "Saved to Gallery!", android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        android.widget.Toast.makeText(context, err.message ?: "Save failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isExporting && sourceBitmap != null,
                            modifier = Modifier.testTag("photo_editor_save_gallery")
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Save Copy to Gallery",
                                tint = Color(0xFF34D399)
                            )
                        }
                    }
                }

                // Studio Darkroom Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0B1120))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .onSizeChanged { canvasSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Photo Studio Canvas",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (isComparing) null else ColorFilter.colorMatrix(liveColorMatrix)
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }

                    // Live Vignette & Bezier Brush / Mosaic Canvas scoped to fitted image rect
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (activeTab == StudioEditorTab.DRAW && fittedRect.width > 4f) {
                                    Modifier.pointerInput(activeBrushStyle, brushColor, brushSizePx, fittedRect) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                if (fittedRect.contains(pos)) {
                                                    haptic.performTelegramPhotoBrushStroke(view)
                                                    val norm = Offset(
                                                        ((pos.x - fittedRect.left) / fittedRect.width).coerceIn(0f, 1f),
                                                        ((pos.y - fittedRect.top) / fittedRect.height).coerceIn(0f, 1f)
                                                    )
                                                    activeStrokePoints = listOf(norm)
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                if (activeStrokePoints.isNotEmpty()) {
                                                    val p = change.position
                                                    val norm = Offset(
                                                        ((p.x - fittedRect.left) / fittedRect.width).coerceIn(0f, 1f),
                                                        ((p.y - fittedRect.top) / fittedRect.height).coerceIn(0f, 1f)
                                                    )
                                                    activeStrokePoints = activeStrokePoints + norm
                                                }
                                            },
                                            onDragEnd = {
                                                if (activeStrokePoints.isNotEmpty()) {
                                                    val minSpan = min(fittedRect.width, fittedRect.height).coerceAtLeast(1f)
                                                    val stroke = EditorStroke(
                                                        normalizedPoints = activeStrokePoints,
                                                        color = brushColor,
                                                        strokeWidthFraction = (brushSizePx / minSpan).coerceIn(0.005f, 0.14f),
                                                        style = activeBrushStyle
                                                    )
                                                    pushState(state.copy(strokes = state.strokes + stroke))
                                                    activeStrokePoints = emptyList()
                                                }
                                            },
                                            onDragCancel = { activeStrokePoints = emptyList() }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        if (fittedRect.width <= 4f || isComparing) return@Canvas

                        // Vignette optical shading
                        val vig = state.adjustments.vignette
                        if (vig > 0.01f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = (vig * 0.76f).coerceIn(0f, 0.85f))),
                                    center = fittedRect.center,
                                    radius = maxOf(fittedRect.width, fittedRect.height) * 0.68f
                                ),
                                topLeft = fittedRect.topLeft,
                                size = fittedRect.size
                            )
                        }

                        val minDim = min(fittedRect.width, fittedRect.height)
                        fun drawEditorStroke(stroke: EditorStroke) {
                            val absPts = stroke.normalizedPoints.map { n ->
                                Offset(fittedRect.left + n.x * fittedRect.width, fittedRect.top + n.y * fittedRect.height)
                            }
                            if (absPts.isEmpty()) return
                            val path = ProImageEngine.buildSmoothComposePath(absPts)
                            val w = (stroke.strokeWidthFraction * minDim).coerceAtLeast(2.5f)
                            when (stroke.style) {
                                EditorBrushStyle.NEON -> {
                                    drawPath(path, stroke.color.copy(alpha = 0.38f), style = Stroke(w * 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    drawPath(path, Color.White, style = Stroke(w * 0.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                                EditorBrushStyle.HIGHLIGHTER -> {
                                    drawPath(path, stroke.color.copy(alpha = 0.42f), style = Stroke(w * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                                EditorBrushStyle.REDACT_MOSAIC -> {
                                    drawPath(path, Color(0xBB1E293B), style = Stroke(w * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    drawPath(path, Color(0x4438BDF8), style = Stroke(w * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                                EditorBrushStyle.PEN -> {
                                    drawPath(path, stroke.color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }

                        state.strokes.forEach { drawEditorStroke(it) }

                        if (activeStrokePoints.isNotEmpty()) {
                            drawEditorStroke(
                                EditorStroke(
                                    normalizedPoints = activeStrokePoints,
                                    color = brushColor,
                                    strokeWidthFraction = (brushSizePx / minDim.coerceAtLeast(1f)),
                                    style = activeBrushStyle
                                )
                            )
                        }
                    }

                    // Text Overlays
                    if (!isComparing && activeTab != StudioEditorTab.CROP && fittedRect.width > 4f) {
                        state.textOverlays.forEach { overlay ->
                            DraggableTextOverlayItem(
                                overlay = overlay,
                                imageRect = fittedRect,
                                isSelected = activeTab == StudioEditorTab.TEXT && selectedTextId == overlay.id,
                                onSelect = {
                                    selectedTextId = overlay.id
                                    activeTab = StudioEditorTab.TEXT
                                },
                                onMove = { nx, ny ->
                                    val updated = state.textOverlays.map {
                                        if (it.id == overlay.id) it.copy(xFraction = nx, yFraction = ny) else it
                                    }
                                    pushState(state.copy(textOverlays = updated), recordHistory = false)
                                },
                                onMoveEnd = { pushState(state, recordHistory = true) },
                                onDelete = {
                                    haptic.performTelegramMediaDelete(view)
                                    val filtered = state.textOverlays.filterNot { it.id == overlay.id }
                                    pushState(state.copy(textOverlays = filtered))
                                    selectedTextId = filtered.lastOrNull()?.id
                                }
                            )
                        }
                    }

                    // Crop Interactive Frame
                    if (activeTab == StudioEditorTab.CROP && fittedRect.width > 4f) {
                        CropOverlayBox(
                            imageRect = fittedRect,
                            cropState = state.transform,
                            onCropChange = { updated -> pushState(state.copy(transform = updated), recordHistory = false) },
                            onCropCommit = { pushState(state, recordHistory = true) }
                        )
                    }

                    // Histogram HUD
                    if (showHistogram && histogram != null && activeTab != StudioEditorTab.CROP) {
                        RgbHistogramHud(
                            histogram = histogram,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .testTag("photo_editor_histogram_hud")
                        )
                    }

                    // Before/After Compare Badge or Clarity Badge
                    if (isComparing) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xCC34D399)
                        ) {
                            Text(
                                text = "ORIGINAL BEFORE",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (state.adjustments.clarity > 0.05f && activeTab != StudioEditorTab.CROP) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xAA0F172A),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "CLARITY ${(state.adjustments.clarity * 100).roundToInt()}%",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Active Tool Contextual Control Panel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (activeTab) {
                            StudioEditorTab.FILTERS -> {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(FilterPreset.entries) { preset ->
                                        val selected = state.adjustments.filterPreset == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                haptic.performTelegramPhotoColorSelect(view)
                                                pushState(state.copy(adjustments = state.adjustments.copy(filterPreset = preset)))
                                            },
                                            label = { Text(preset.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF38BDF8),
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }
                                if (state.adjustments.filterPreset != FilterPreset.ORIGINAL) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Strength", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                        Slider(
                                            value = state.adjustments.filterIntensity,
                                            onValueChange = { v ->
                                                pushState(state.copy(adjustments = state.adjustments.copy(filterIntensity = v)), recordHistory = false)
                                            },
                                            onValueChangeFinished = { pushState(state, recordHistory = true) },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("${(state.adjustments.filterIntensity * 100).roundToInt()}%", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            StudioEditorTab.ADJUST -> {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(AdjustParam.entries) { p ->
                                        val selected = activeAdjustParam == p
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                haptic.performPixelTick(view)
                                                activeAdjustParam = p
                                            },
                                            label = { Text(p.label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF6366F1),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                labelColor = Color.White.copy(alpha = 0.85f)
                                            )
                                        )
                                    }
                                }
                                val currentVal = when (activeAdjustParam) {
                                    AdjustParam.EXPOSURE -> state.adjustments.exposure
                                    AdjustParam.CONTRAST -> state.adjustments.contrast
                                    AdjustParam.SATURATION -> state.adjustments.saturation
                                    AdjustParam.WARMTH -> state.adjustments.warmth
                                    AdjustParam.TINT -> state.adjustments.tint
                                    AdjustParam.CLARITY -> state.adjustments.clarity
                                    AdjustParam.VIGNETTE -> state.adjustments.vignette
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(activeAdjustParam.label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
                                    Slider(
                                        value = currentVal,
                                        onValueChange = { v ->
                                            val updatedAdj = when (activeAdjustParam) {
                                                AdjustParam.EXPOSURE -> state.adjustments.copy(exposure = v)
                                                AdjustParam.CONTRAST -> state.adjustments.copy(contrast = v)
                                                AdjustParam.SATURATION -> state.adjustments.copy(saturation = v)
                                                AdjustParam.WARMTH -> state.adjustments.copy(warmth = v)
                                                AdjustParam.TINT -> state.adjustments.copy(tint = v)
                                                AdjustParam.CLARITY -> state.adjustments.copy(clarity = v)
                                                AdjustParam.VIGNETTE -> state.adjustments.copy(vignette = v)
                                            }
                                            pushState(state.copy(adjustments = updatedAdj), recordHistory = false)
                                        },
                                        onValueChangeFinished = { pushState(state, recordHistory = true) },
                                        valueRange = activeAdjustParam.min..activeAdjustParam.max,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format("%.2f", currentVal),
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            StudioEditorTab.CROP -> {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(AspectPreset.entries) { preset ->
                                        val imgAspect = previewBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f
                                        FilterChip(
                                            selected = state.transform.aspectPreset == preset,
                                            onClick = {
                                                haptic.performPixelClick(view)
                                                pushState(
                                                    state.copy(
                                                        transform = ProImageEngine.enforceAspectRatio(state.transform, preset, imgAspect)
                                                    )
                                                )
                                            },
                                            label = { Text(preset.label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF38BDF8),
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performPixelClick(view)
                                            val nextRot = (state.transform.rotationDegrees + 90) % 360
                                            pushState(state.copy(transform = state.transform.copy(rotationDegrees = nextRot)))
                                        },
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("90°", color = Color.White, fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performPixelClick(view)
                                            pushState(state.copy(transform = state.transform.copy(flipHorizontal = !state.transform.flipHorizontal)))
                                        },
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Flip H", color = Color.White, fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            haptic.performPixelTick(view)
                                            pushState(state.copy(transform = CropTransformState()))
                                        }
                                    ) {
                                        Text("Reset Crop", color = Color(0xFFFB7185), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            StudioEditorTab.DRAW -> {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(EditorBrushStyle.entries) { style ->
                                        FilterChip(
                                            selected = activeBrushStyle == style,
                                            onClick = {
                                                haptic.performTelegramPhotoToolSwitch(view)
                                                activeBrushStyle = style
                                            },
                                            label = { Text(style.label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (style == EditorBrushStyle.REDACT_MOSAIC) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(STUDIO_COLORS) { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .clickable {
                                                        haptic.performTelegramPhotoColorSelect(view)
                                                        brushColor = color
                                                    }
                                                    .then(
                                                        if (brushColor == color) Modifier.border(2.dp, Color.White, CircleShape)
                                                        else Modifier
                                                    )
                                            )
                                        }
                                    }
                                    Text("${brushSizePx.roundToInt()}px", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                    Slider(
                                        value = brushSizePx,
                                        onValueChange = { brushSizePx = it },
                                        valueRange = 4f..32f,
                                        modifier = Modifier.width(88.dp)
                                    )
                                }
                            }

                            StudioEditorTab.TEXT -> {
                                val selectedOverlay = state.textOverlays.firstOrNull { it.id == selectedTextId }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                editingTextId = null
                                                showTextDialog = true
                                            },
                                            modifier = Modifier.testTag("photo_editor_add_text"),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("+ Text Layer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (selectedOverlay != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    editingTextId = selectedOverlay.id
                                                    showTextDialog = true
                                                },
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("Edit", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                                if (selectedOverlay != null) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(TextOverlayStyle.entries) { st ->
                                            FilterChip(
                                                selected = selectedOverlay.style == st,
                                                onClick = {
                                                    val updated = state.textOverlays.map {
                                                        if (it.id == selectedOverlay.id) it.copy(style = st) else it
                                                    }
                                                    pushState(state.copy(textOverlays = updated))
                                                },
                                                label = { Text(st.label, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF38BDF8),
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    labelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                                if (selectedOverlay != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        STUDIO_COLORS.take(5).forEach { c ->
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .clickable {
                                                        val updated = state.textOverlays.map {
                                                            if (it.id == selectedOverlay.id) it.copy(color = c) else it
                                                        }
                                                        pushState(state.copy(textOverlays = updated))
                                                    }
                                            )
                                        }
                                        Slider(
                                            value = selectedOverlay.scale,
                                            onValueChange = { s ->
                                                val updated = state.textOverlays.map {
                                                    if (it.id == selectedOverlay.id) it.copy(scale = s) else it
                                                }
                                                pushState(state.copy(textOverlays = updated), recordHistory = false)
                                            },
                                            onValueChangeFinished = { pushState(state, recordHistory = true) },
                                            valueRange = 0.6f..2.0f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Bottom Studio Dock (5 Tabs + Reset + Export/Send)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(StudioEditorTab.entries) { tab ->
                                    val selected = activeTab == tab
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                haptic.performTelegramPhotoToolSwitch(view)
                                                activeTab = tab
                                            }
                                            .testTag("photo_editor_tab_${tab.name.lowercase()}"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            text = tab.label,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    haptic.performTelegramMediaDelete(view)
                                    pushState(EditorSnapshot())
                                },
                                modifier = Modifier.testTag("photo_editor_reset")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset All", tint = Color(0xFFFB7185))
                            }

                            Button(
                                onClick = {
                                    val src = sourceBitmap
                                    if (src == null || isExporting) return@Button
                                    isExporting = true
                                    scope.launch {
                                        val renderedPath = withContext(Dispatchers.Default) {
                                            val finalBmp = ProImageEngine.renderFinalBitmap(
                                                source = src,
                                                adjustments = state.adjustments,
                                                transform = state.transform,
                                                strokes = state.strokes,
                                                textOverlays = state.textOverlays
                                            )
                                            val saved = ProImageEngine.saveBitmapToCache(context, finalBmp).getOrNull()
                                            finalBmp.recycle()
                                            saved
                                        }
                                        haptic.performTelegramMessageSent(view)
                                        if (onSendRendered != null) {
                                            onSendRendered(isViewOnce, renderedPath)
                                        } else {
                                            onSend(isViewOnce)
                                        }
                                        isExporting = false
                                        onDismiss()
                                    }
                                },
                                enabled = !isExporting && sourceBitmap != null,
                                modifier = Modifier.testTag("photo_editor_send_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Send", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        val currentText = editingTextId?.let { id -> state.textOverlays.firstOrNull { it.id == id }?.text } ?: ""
        TextOverlayInputDialog(
            initialText = currentText,
            onConfirm = { caption ->
                if (editingTextId != null) {
                    val updated = state.textOverlays.map {
                        if (it.id == editingTextId) it.copy(text = caption) else it
                    }
                    pushState(state.copy(textOverlays = updated))
                } else {
                    val item = EditorTextOverlay(
                        id = UUID.randomUUID().toString().take(8),
                        text = caption,
                        color = brushColor
                    )
                    pushState(state.copy(textOverlays = state.textOverlays + item))
                    selectedTextId = item.id
                }
                showTextDialog = false
                editingTextId = null
            },
            onDismiss = {
                showTextDialog = false
                editingTextId = null
            }
        )
    }
}
