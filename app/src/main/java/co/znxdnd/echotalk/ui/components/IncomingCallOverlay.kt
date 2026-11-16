package co.znxdnd.echotalk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import co.znxdnd.echotalk.calling.CallType
import co.znxdnd.echotalk.calling.IncomingCallInvite
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.getCountryFlag

@Composable
fun IncomingCallOverlay(
    invite: IncomingCallInvite,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val caller = invite.caller

    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("incoming_call_overlay"),
            color = Color(0xFF020617)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF020617)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(28.dp)
            ) {
                // Top Call Type Pill
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x336366F1),
                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (invite.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Incoming ${invite.callType.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Caller Identity
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5))
                            .border(3.dp, Color(0xFF10B981).copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (caller.avatarUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = caller.avatarUrl,
                                contentDescription = caller.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Text(
                                        text = caller.name.take(1).uppercase(),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = caller.name.take(1).uppercase(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = caller.name,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Text(
                        text = "${caller.handle} • ${getCountryFlag(caller.countryCode)}",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Accept / Decline Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performPixelHeavyClick(view)
                                onReject()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE11D48))
                                .testTag("incoming_call_reject_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline Call",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text("Decline", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFDA4AF))
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performPixelConfirm(view)
                                onAccept()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .testTag("incoming_call_accept_button")
                        ) {
                            Icon(
                                imageVector = if (invite.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept Call",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6EE7B7))
                    }
                }
            }
        }
    }
}
