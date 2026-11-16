package co.znxdnd.echotalk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.image.CropTransformState
import co.znxdnd.echotalk.image.EditorTextOverlay
import co.znxdnd.echotalk.image.ImageHistogram
import co.znxdnd.echotalk.image.TextOverlayStyle
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Translucent darkroom RGB + Luminance histogram HUD overlay.
 */
@Composable
fun RgbHistogramHud(
    histogram: ImageHistogram,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(156.dp)
            .height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xCC060913),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTOGRAM RGB",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.72f),
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "LUMA",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bins = histogram.lumaBins.size
                if (bins <= 1) return@Canvas
                val stepW = size.width / (bins - 1).toFloat()

                fun buildChannelPath(values: List<Float>): Path {
                    return Path().apply {
                        moveTo(0f, size.height)
                        values.forEachIndexed { idx, v ->
                            val x = idx * stepW
                            val y = size.height - (v.coerceIn(0f, 1f) * size.height)
                            lineTo(x, y)
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                }

                drawPath(buildChannelPath(histogram.redBins), color = Color(0x55EF4444))
                drawPath(buildChannelPath(histogram.greenBins), color = Color(0x5522C55E))
                drawPath(buildChannelPath(histogram.blueBins), color = Color(0x553B82F6))
                drawPath(
                    buildChannelPath(histogram.lumaBins),
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 1.6f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private enum class CropDragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE_BOX
}

/**
 * Interactive 4-corner + center-pan crop frame overlay mapped strictly onto [imageRect] bounds.
 */
@Composable
fun CropOverlayBox(
    imageRect: Rect,
    cropState: CropTransformState,
    onCropChange: (CropTransformState) -> Unit,
    onCropCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageRect.width <= 4f || imageRect.height <= 4f) return

    var activeHandle by remember { mutableStateOf<CropDragHandle?>(null) }
    val currentCrop by rememberUpdatedState(cropState)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageRect, cropState.aspectPreset) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val l = imageRect.left + currentCrop.left * imageRect.width
                        val t = imageRect.top + currentCrop.top * imageRect.height
                        val r = imageRect.left + currentCrop.right * imageRect.width
                        val b = imageRect.top + currentCrop.bottom * imageRect.height
                        val threshold = 48.dp.toPx()

                        val corners = listOf(
                            CropDragHandle.TOP_LEFT to Offset(l, t),
                            CropDragHandle.TOP_RIGHT to Offset(r, t),
                            CropDragHandle.BOTTOM_LEFT to Offset(l, b),
                            CropDragHandle.BOTTOM_RIGHT to Offset(r, b)
                        )
                        val nearest = corners.minByOrNull { hypot(pos.x - it.second.x, pos.y - it.second.y) }
                        activeHandle = if (nearest != null && hypot(pos.x - nearest.second.x, pos.y - nearest.second.y) <= threshold) {
                            nearest.first
                        } else if (pos.x in l..r && pos.y in t..b) {
                            CropDragHandle.MOVE_BOX
                        } else {
                            null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val handle = activeHandle ?: return@detectDragGestures
                        change.consume()
                        val dx = dragAmount.x / imageRect.width
                        val dy = dragAmount.y / imageRect.height
                        val c = currentCrop
                        val minSpan = 0.16f

                        val updated = when (handle) {
                            CropDragHandle.MOVE_BOX -> {
                                val w = c.right - c.left
                                val h = c.bottom - c.top
                                val nl = (c.left + dx).coerceIn(0f, 1f - w)
                                val nt = (c.top + dy).coerceIn(0f, 1f - h)
                                c.copy(left = nl, top = nt, right = nl + w, bottom = nt + h)
                            }
                            CropDragHandle.TOP_LEFT -> {
                                val nl = (c.left + dx).coerceIn(0f, c.right - minSpan)
                                val nt = (c.top + dy).coerceIn(0f, c.bottom - minSpan)
                                c.copy(left = nl, top = nt)
                            }
                            CropDragHandle.TOP_RIGHT -> {
                                val nr = (c.right + dx).coerceIn(c.left + minSpan, 1f)
                                val nt = (c.top + dy).coerceIn(0f, c.bottom - minSpan)
                                c.copy(right = nr, top = nt)
                            }
                            CropDragHandle.BOTTOM_LEFT -> {
                                val nl = (c.left + dx).coerceIn(0f, c.right - minSpan)
                                val nb = (c.bottom + dy).coerceIn(c.top + minSpan, 1f)
                                c.copy(left = nl, bottom = nb)
                            }
                            CropDragHandle.BOTTOM_RIGHT -> {
                                val nr = (c.right + dx).coerceIn(c.left + minSpan, 1f)
                                val nb = (c.bottom + dy).coerceIn(c.top + minSpan, 1f)
                                c.copy(right = nr, bottom = nb)
                            }
                        }
                        onCropChange(updated)
                    },
                    onDragEnd = {
                        if (activeHandle != null) onCropCommit()
                        activeHandle = null
                    },
                    onDragCancel = { activeHandle = null }
                )
            }
    ) {
        val l = imageRect.left + cropState.left * imageRect.width
        val t = imageRect.top + cropState.top * imageRect.height
        val r = imageRect.left + cropState.right * imageRect.width
        val b = imageRect.top + cropState.bottom * imageRect.height
        val cw = (r - l).coerceAtLeast(1f)
        val ch = (b - t).coerceAtLeast(1f)

        val scrimColor = Color(0x99020617)
        // Top scrim
        if (t > imageRect.top) {
            drawRect(scrimColor, topLeft = Offset(imageRect.left, imageRect.top), size = Size(imageRect.width, t - imageRect.top))
        }
        // Bottom scrim
        if (b < imageRect.bottom) {
            drawRect(scrimColor, topLeft = Offset(imageRect.left, b), size = Size(imageRect.width, imageRect.bottom - b))
        }
        // Left scrim
        if (l > imageRect.left) {
            drawRect(scrimColor, topLeft = Offset(imageRect.left, t), size = Size(l - imageRect.left, ch))
        }
        // Right scrim
        if (r < imageRect.right) {
            drawRect(scrimColor, topLeft = Offset(r, t), size = Size(imageRect.right - r, ch))
        }

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = Size(cw, ch),
            style = Stroke(width = 2f)
        )

        // Rule-of-thirds gridlines
        val thirdW = cw / 3f
        val thirdH = ch / 3f
        for (i in 1..2) {
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(l + thirdW * i, t),
                end = Offset(l + thirdW * i, b),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(l, t + thirdH * i),
                end = Offset(r, t + thirdH * i),
                strokeWidth = 1f
            )
        }

        // Bold L-shaped corner handles
        val handleLen = 18.dp.toPx().coerceAtMost(minOf(cw, ch) * 0.35f)
        val accent = Color(0xFF38BDF8)
        val hw = 5f
        // Top-Left
        drawLine(accent, Offset(l, t), Offset(l + handleLen, t), strokeWidth = hw, cap = StrokeCap.Round)
        drawLine(accent, Offset(l, t), Offset(l, t + handleLen), strokeWidth = hw, cap = StrokeCap.Round)
        // Top-Right
        drawLine(accent, Offset(r - handleLen, t), Offset(r, t), strokeWidth = hw, cap = StrokeCap.Round)
        drawLine(accent, Offset(r, t), Offset(r, t + handleLen), strokeWidth = hw, cap = StrokeCap.Round)
        // Bottom-Left
        drawLine(accent, Offset(l, b), Offset(l + handleLen, b), strokeWidth = hw, cap = StrokeCap.Round)
        drawLine(accent, Offset(l, b - handleLen), Offset(l, b), strokeWidth = hw, cap = StrokeCap.Round)
        // Bottom-Right
        drawLine(accent, Offset(r - handleLen, b), Offset(r, b), strokeWidth = hw, cap = StrokeCap.Round)
        drawLine(accent, Offset(r, b - handleLen), Offset(r, b), strokeWidth = hw, cap = StrokeCap.Round)
    }
}

/**
 * Interactive draggable & selectable text layer rendered inside [imageRect].
 */
@Composable
fun DraggableTextOverlayItem(
    overlay: EditorTextOverlay,
    imageRect: Rect,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (newXFraction: Float, newYFraction: Float) -> Unit,
    onMoveEnd: () -> Unit,
    onDelete: () -> Unit
) {
    if (imageRect.width <= 4f || imageRect.height <= 4f) return

    val pxX = imageRect.left + overlay.xFraction * imageRect.width
    val pxY = imageRect.top + overlay.yFraction * imageRect.height
    var itemWidthPx by remember { mutableIntStateOf(120) }
    var itemHeightPx by remember { mutableIntStateOf(40) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (pxX - itemWidthPx / 2f).roundToInt(),
                    y = (pxY - itemHeightPx / 2f).roundToInt()
                )
            }
            .pointerInput(overlay.id, imageRect) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val nx = (overlay.xFraction + dragAmount.x / imageRect.width).coerceIn(0.06f, 0.94f)
                        val ny = (overlay.yFraction + dragAmount.y / imageRect.height).coerceIn(0.06f, 0.94f)
                        onMove(nx, ny)
                    },
                    onDragEnd = { onMoveEnd() }
                )
            }
            .clickable { onSelect() }
    ) {
        val fontSizeSp = (20f * overlay.scale).coerceIn(12f, 44f).sp
        Box(
            modifier = Modifier
                .then(
                    if (isSelected) Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF38BDF8),
                        shape = RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .then(
                    if (overlay.style == TextOverlayStyle.PILL) Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC0F172A))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    else Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
        ) {
            Text(
                text = overlay.text,
                color = overlay.color,
                fontSize = fontSizeSp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    shadow = when (overlay.style) {
                        TextOverlayStyle.NEON -> Shadow(
                            color = overlay.color.copy(alpha = 0.9f),
                            offset = Offset.Zero,
                            blurRadius = 18f
                        )
                        else -> Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    }
                ),
                onTextLayout = { result ->
                    itemWidthPx = result.size.width + 28
                    itemHeightPx = result.size.height + 18
                }
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .clickable { onDelete() }
                    .testTag("photo_editor_delete_text_${overlay.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Text",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun TextOverlayInputDialog(
    initialText: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Text Layer", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 64) draft = it },
                placeholder = { Text("Type caption or watermark...", color = Color.White.copy(alpha = 0.45f)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_editor_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = draft.trim()
                    if (clean.isNotEmpty()) onConfirm(clean) else onDismiss()
                },
                modifier = Modifier.testTag("photo_editor_text_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
