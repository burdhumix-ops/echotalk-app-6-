package co.znxdnd.echotalk.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PasscodeDialog(
    onDismiss: () -> Unit,
    onRoleGranted: (String) -> Unit,
    repository: EchoTalkRepository = EchoTalkRepository.getInstance(),
    isCurrentlyUnlocked: Boolean = false,
    serverRole: String = "member"
) {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    val hasVerifiedAdminSession = isCurrentlyUnlocked && (serverRole == "owner" || serverRole == "admin")

    fun onDigit(d: String) {
        if (isVerifying || hasVerifiedAdminSession) return
        if (pin.length < 4) {
            val nextPin = pin + d
            pin = nextPin
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (nextPin.length == 4) {
                isVerifying = true
                coroutineScope.launch {
                    val result = repository.verifyAndUnlockRoleGateway(nextPin)
                    isVerifying = false
                    result.onSuccess { verifiedRole ->
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onRoleGranted(verifiedRole)
                        onDismiss()
                    }.onFailure { err ->
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(context).performAlertReceived(view)
                        errorMessage = err.message ?: "Access denied"
                    }
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            delay(1500)
            pin = ""
            errorMessage = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(330.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF21A1822),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33E8A5C8)),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33E8A5C8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasVerifiedAdminSession) Icons.Default.Shield else Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFE8A5C8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (hasVerifiedAdminSession) "Verified Gateway" else "Security Gateway",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (hasVerifiedAdminSession) "Server-verified access active" else "Security PIN required",
                                fontSize = 10.sp,
                                color = Color(0xFFC4A1B3)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                if (!hasVerifiedAdminSession) {
                    Text(
                        text = if (isVerifying) "Verifying credentials with server..." else "Enter 4-digit security PIN",
                        fontSize = 11.sp,
                        color = Color(0xFFC4A1B3),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // 4 Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { idx ->
                            val filled = pin.length > idx
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) Color(0xFFE8A5C8)
                                        else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            fontSize = 11.sp,
                            color = Color(0xFFFB7185),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Keypad
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("", "0", "DEL")
                        )

                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { digit ->
                                    if (digit.isEmpty()) {
                                        Spacer(modifier = Modifier.size(50.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable(enabled = !isVerifying) {
                                                    if (digit == "DEL") {
                                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                    } else {
                                                        onDigit(digit)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = digit,
                                                fontSize = if (digit == "DEL") 11.sp else 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Authoritative ${serverRole.uppercase()} privileges are active for this session. Lock to hide all elevated controls immediately.",
                        fontSize = 11.sp,
                        color = Color(0xFFC4A1B3),
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            repository.lockRoleGateway()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8A5C8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF0F0E17), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock & Hide Privileges", color = Color(0xFF0F0E17), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
