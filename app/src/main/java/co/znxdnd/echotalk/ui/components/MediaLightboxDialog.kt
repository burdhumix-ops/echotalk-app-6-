package co.znxdnd.echotalk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.image.ProImageEngine
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Full-screen pinch-to-zoom & double-tap zoom lightbox for photos and studio artworks in chat,
 * with direct Re-Edit in Pro Studio support.
 */
@Composable
fun MediaLightboxDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onEditInStudio: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale > 1.01f) {
            val maxPanX = ((viewportSize.width * (nextScale - 1f)) * 0.5f).coerceAtLeast(220f)
            val maxPanY = ((viewportSize.height * (nextScale - 1f)) * 0.5f).coerceAtLeast(220f)
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                y = (offset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
            )
        } else {
            Offset.Zero
        }
    }

    val mediaUrl = message.mediaUrl
    val hasDisplayableUrl = !mediaUrl.isNullOrBlank() &&
        (mediaUrl.startsWith("/") || mediaUrl.startsWith("http") || mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF7030712)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Zoomable Media Viewport
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 76.dp, horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    haptic.performPixelClick(view)
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.2f
                                    }
                                }
                            )
                        }
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    val mediaModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                    if (hasDisplayableUrl) {
                        SubcomposeAsyncImage(
                            model = if (mediaUrl!!.startsWith("/")) File(mediaUrl) else mediaUrl,
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = mediaModifier,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                                }
                            },
                            error = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_echotalk_logo_1792796066052),
                                    contentDescription = message.text,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_echotalk_logo_1792796066052),
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = mediaModifier
                        )
                    }
                }

                // Top Metadata + Close Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = message.sender,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${message.timestamp} • Pinch or double-tap to zoom (${String.format(Locale.US, "%.1fx", scale)})",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("media_lightbox_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Viewer", tint = Color.White)
                    }
                }

                // Bottom Studio Actions Dock
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performPixelTick(view)
                                    scale = if (scale > 1.2f) 1f else 2f
                                    if (scale == 1f) offset = Offset.Zero
                                },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (scale > 1.2f) Icons.Default.ZoomOutMap else Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (scale > 1.2f) "Reset" else "2x Zoom", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (hasDisplayableUrl) {
                                OutlinedButton(
                                    onClick = {
                                        if (isSaving) return@OutlinedButton
                                        isSaving = true
                                        scope.launch {
                                            val res = ProImageEngine.saveMediaUrlToGallery(context, mediaUrl!!)
                                            isSaving = false
                                            res.onSuccess {
                                                haptic.performPixelConfirm(view)
                                                Toast.makeText(context, "Saved to Pictures/EchoTalk!", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Save failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isSaving,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("media_lightbox_save")
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            if (hasDisplayableUrl && onEditInStudio != null) {
                                Button(
                                    onClick = {
                                        haptic.performPixelClick(view)
                                        onEditInStudio(mediaUrl!!)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("media_lightbox_edit_studio")
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Edit in Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
