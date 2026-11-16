package co.znxdnd.echotalk.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import co.znxdnd.echotalk.image.ProImageEngine
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

enum class DoodleBrushStyle(val label: String) {
    PEN("Ink Pen"),
    NEON("Neon Glow"),
    MARKER("Marker"),
    ERASER("Eraser")
}

enum class DoodleBackdrop(val label: String, val bgColor: Color, val showGrid: Boolean) {
    OBSIDIAN("Obsidian", Color(0xFF030712), false),
    STUDIO_GRID("Grid", Color(0xFF0B1220), true),
    PARCHMENT("Studio Light", Color(0xFFF8FAFC), false)
}

data class DoodlePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val style: DoodleBrushStyle = if (isEraser) DoodleBrushStyle.ERASER else DoodleBrushStyle.PEN
)

private val DOODLE_COLORS = listOf(
    Color(0xFFFFFFFF),
    Color(0xFF818CF8),
    Color(0xFF38BDF8),
    Color(0xFF34D399),
    Color(0xFFFBBF24),
    Color(0xFFFB7185),
    Color(0xFFC084FC),
    Color(0xFF0F172A)
)

private fun exportDoodleBitmap(
    paths: List<DoodlePath>,
    backdrop: DoodleBackdrop,
    canvasSize: IntSize,
    outputSize: Int = 960
): Bitmap {
    val w = canvasSize.width.coerceAtLeast(1)
    val h = canvasSize.height.coerceAtLeast(1)
    val aspect = w.toFloat() / h.toFloat()
    val outW = if (aspect >= 1f) outputSize else (outputSize * aspect).roundToInt().coerceAtLeast(320)
    val outH = if (aspect >= 1f) (outputSize / aspect).roundToInt().coerceAtLeast(320) else outputSize

    val finalBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val finalCanvas = AndroidCanvas(finalBitmap)
    finalCanvas.drawColor(backdrop.bgColor.toArgb())

    if (backdrop.showGrid) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2294A3B8
            strokeWidth = 2f
        }
        val step = (outW / 12f).coerceAtLeast(24f)
        var x = step
        while (x < outW) {
            finalCanvas.drawLine(x, 0f, x, outH.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < outH) {
            finalCanvas.drawLine(0f, y, outW.toFloat(), y, gridPaint)
            y += step
        }
    }

    // Separate transparent stroke layer so true eraser clears only ink, preserving backdrop/grid
    val inkLayer = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val inkCanvas = AndroidCanvas(inkLayer)
    val scale = min(outW.toFloat() / w.toFloat(), outH.toFloat() / h.toFloat())

    for (doodlePath in paths) {
        if (doodlePath.points.isEmpty()) continue
        val normPts = doodlePath.points.map { Offset((it.x / w).coerceIn(0f, 1f), (it.y / h).coerceIn(0f, 1f)) }
        val androidPath = ProImageEngine.buildSmoothAndroidPath(normPts, outW.toFloat(), outH.toFloat())
        val scaledWidth = (doodlePath.strokeWidth * scale).coerceAtLeast(3f)

        when {
            doodlePath.isEraser || doodlePath.style == DoodleBrushStyle.ERASER -> {
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    style = Paint.Style.STROKE
                    strokeWidth = scaledWidth * 1.4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                inkCanvas.drawPath(androidPath, clearPaint)
            }
            doodlePath.style == DoodleBrushStyle.NEON -> {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = doodlePath.color.toArgb()
                    alpha = 95
                    style = Paint.Style.STROKE
                    strokeWidth = scaledWidth * 2.4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                inkCanvas.drawPath(androidPath, glow)
                val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = scaledWidth * 0.65f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                inkCanvas.drawPath(androidPath, core)
            }
            else -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = doodlePath.color.toArgb()
                    alpha = if (doodlePath.style == DoodleBrushStyle.MARKER) 135 else 255
                    style = Paint.Style.STROKE
                    strokeWidth = if (doodlePath.style == DoodleBrushStyle.MARKER) scaledWidth * 1.35f else scaledWidth
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                inkCanvas.drawPath(androidPath, paint)
            }
        }
    }

    finalCanvas.drawBitmap(inkLayer, 0f, 0f, null)
    inkLayer.recycle()
    return finalBitmap
}

@Composable
fun DoodleCanvasDialog(
    recipientName: String,
    onDismiss: () -> Unit,
    onSendDoodle: (isViewOnce: Boolean, pathCount: Int) -> Unit,
    onSendRenderedDoodle: ((isViewOnce: Boolean, pathCount: Int, renderedFilePath: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var selectedColor by remember { mutableStateOf(DOODLE_COLORS[1]) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(10f) }
    var activeBrush by remember { mutableStateOf(DoodleBrushStyle.PEN) }
    var activeBackdrop by remember { mutableStateOf(DoodleBackdrop.OBSIDIAN) }
    var isViewOnce by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(900, 900)) }

    val paths = remember { mutableStateListOf<DoodlePath>() }
    val redoPaths = remember { mutableStateListOf<DoodlePath>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF090D16),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header + Undo / Redo / Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pro Sketch Studio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Artwork for $recipientName",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (paths.isNotEmpty()) {
                                    haptic.performPixelTick(view)
                                    redoPaths.add(paths.removeAt(paths.size - 1))
                                }
                            },
                            enabled = paths.isNotEmpty(),
                            modifier = Modifier.testTag("doodle_undo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (paths.isNotEmpty()) Color.White else Color.Gray
                            )
                        }
                        IconButton(
                            onClick = {
                                if (redoPaths.isNotEmpty()) {
                                    haptic.performPixelTick(view)
                                    paths.add(redoPaths.removeAt(redoPaths.size - 1))
                                }
                            },
                            enabled = redoPaths.isNotEmpty(),
                            modifier = Modifier.testTag("doodle_redo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoPaths.isNotEmpty()) Color.White else Color.Gray
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performTelegramMediaDelete(view)
                                paths.clear()
                                redoPaths.clear()
                            },
                            modifier = Modifier.testTag("doodle_clear_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFFB7185))
                        }
                        IconButton(
                            onClick = { if (!isExporting) onDismiss() },
                            modifier = Modifier.testTag("doodle_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Brush Modes Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DoodleBrushStyle.entries.forEach { style ->
                        val selected = activeBrush == style
                        val bgColor = when {
                            selected && style == DoodleBrushStyle.ERASER -> Color(0xFFFB7185)
                            selected -> Color(0xFF38BDF8)
                            else -> Color.White.copy(alpha = 0.08f)
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performTelegramPhotoToolSwitch(view)
                                    activeBrush = style
                                }
                                .testTag("doodle_brush_${style.name.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            color = bgColor,
                            border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = style.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.Black else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Backdrop Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Canvas:",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Medium
                    )
                    DoodleBackdrop.entries.forEach { bg ->
                        val selected = activeBackdrop == bg
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performPixelClick(view)
                                    activeBackdrop = bg
                                }
                                .testTag("doodle_bg_${bg.name.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.08f),
                            border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = bg.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Colors + Dynamic Brush Size Preview Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(DOODLE_COLORS) { color ->
                            val selected = activeBrush != DoodleBrushStyle.ERASER && selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        haptic.performTelegramPhotoColorSelect(view)
                                        selectedColor = color
                                        if (activeBrush == DoodleBrushStyle.ERASER) {
                                            activeBrush = DoodleBrushStyle.PEN
                                        }
                                    }
                                    .then(
                                        if (selected) Modifier.border(2.dp, Color(0xFF38BDF8), CircleShape)
                                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    )
                            )
                        }
                    }

                    // Live brush diameter dot preview + Slider
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((selectedStrokeWidth * 0.75f).coerceIn(6f, 24f).dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeBrush == DoodleBrushStyle.ERASER) Color(0xFFFB7185) else selectedColor
                                )
                        )
                    }
                    Slider(
                        value = selectedStrokeWidth,
                        onValueChange = { selectedStrokeWidth = it },
                        valueRange = 4f..30f,
                        modifier = Modifier.width(96.dp)
                    )
                }

                // Drawing Canvas Area (Backdrop separated from Offscreen Ink Layer for true BlendMode.Clear Eraser)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(activeBackdrop.bgColor)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                        .onSizeChanged { canvasSize = it }
                ) {
                    // Optional Architectural Studio Grid
                    if (activeBackdrop.showGrid) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val step = 28.dp.toPx()
                            var x = step
                            while (x < size.width) {
                                drawLine(Color(0x2294A3B8), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                x += step
                            }
                            var y = step
                            while (y < size.height) {
                                drawLine(Color(0x2294A3B8), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                y += step
                            }
                        }
                    }

                    // Smooth Bezier + Offscreen Compositing Ink Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .pointerInput(activeBrush, selectedColor, selectedStrokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        haptic.performTelegramPhotoBrushStroke(view)
                                        currentPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.isNotEmpty()) {
                                            redoPaths.clear()
                                            paths.add(
                                                DoodlePath(
                                                    points = currentPoints,
                                                    color = selectedColor,
                                                    strokeWidth = selectedStrokeWidth,
                                                    isEraser = activeBrush == DoodleBrushStyle.ERASER,
                                                    style = activeBrush
                                                )
                                            )
                                            currentPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = { currentPoints = emptyList() }
                                )
                            }
                    ) {
                        fun drawSmoothDoodleStroke(pathObj: DoodlePath) {
                            if (pathObj.points.isEmpty()) return
                            val bezier = ProImageEngine.buildSmoothComposePath(pathObj.points)
                            when {
                                pathObj.isEraser || pathObj.style == DoodleBrushStyle.ERASER -> {
                                    drawPath(
                                        path = bezier,
                                        color = Color.Transparent,
                                        blendMode = BlendMode.Clear,
                                        style = Stroke(width = pathObj.strokeWidth * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                                pathObj.style == DoodleBrushStyle.NEON -> {
                                    drawPath(
                                        path = bezier,
                                        color = pathObj.color.copy(alpha = 0.42f),
                                        style = Stroke(width = pathObj.strokeWidth * 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                    drawPath(
                                        path = bezier,
                                        color = Color.White,
                                        style = Stroke(width = pathObj.strokeWidth * 0.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                                pathObj.style == DoodleBrushStyle.MARKER -> {
                                    drawPath(
                                        path = bezier,
                                        color = pathObj.color.copy(alpha = 0.52f),
                                        style = Stroke(width = pathObj.strokeWidth * 1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                                else -> {
                                    drawPath(
                                        path = bezier,
                                        color = pathObj.color,
                                        style = Stroke(width = pathObj.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }

                        paths.forEach { drawSmoothDoodleStroke(it) }

                        if (currentPoints.isNotEmpty()) {
                            drawSmoothDoodleStroke(
                                DoodlePath(
                                    points = currentPoints,
                                    color = selectedColor,
                                    strokeWidth = selectedStrokeWidth,
                                    isEraser = activeBrush == DoodleBrushStyle.ERASER,
                                    style = activeBrush
                                )
                            )
                        }
                    }
                }

                // Bottom Bar: View-Once Toggle + Export/Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performMessengerFlameTap(view)
                                isViewOnce = !isViewOnce
                            }
                            .testTag("doodle_view_once_toggle"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isViewOnce) Color(0x33FBBF24) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isViewOnce) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.16f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔥", fontSize = 12.sp)
                            Text(
                                text = if (isViewOnce) "View-Once: ON" else "View-Once: OFF",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isViewOnce) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            isExporting = true
                            val snapshotPaths = paths.toList()
                            scope.launch {
                                val filePath = withContext(Dispatchers.Default) {
                                    val bmp = exportDoodleBitmap(snapshotPaths, activeBackdrop, canvasSize)
                                    val saved = ProImageEngine.saveBitmapToCache(context, bmp, prefix = "echotalk_doodle_").getOrNull()
                                    bmp.recycle()
                                    saved
                                }
                                haptic.performTelegramMessageSent(view)
                                if (onSendRenderedDoodle != null) {
                                    onSendRenderedDoodle(isViewOnce, snapshotPaths.size, filePath)
                                } else {
                                    onSendDoodle(isViewOnce, snapshotPaths.size)
                                }
                                isExporting = false
                                onDismiss()
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.testTag("doodle_send_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Artwork", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Send Artwork", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
