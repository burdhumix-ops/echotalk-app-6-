package co.znxdnd.echotalk.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import kotlinx.coroutines.delay

private enum class PreSignInStage {
    PIN_ENTRY,
    ROLE_SELECTION
}

private data class RolePreviewOption(
    val id: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * Hidden two-stage pre-sign-in modal reached only via 15 silent taps on the AuthScreen logo.
 * Stage 1 verifies PIN cleanly with zero hint disclosure.
 * Stage 2 selects the local preview role before entering the app without cloud DB writes.
 */
@Composable
fun PreSignInGatewayDialog(
    onDismiss: () -> Unit,
    onAccessGranted: (userId: String) -> Unit,
    repository: EchoTalkRepository = EchoTalkRepository.getInstance()
) {
    val view = LocalView.current
    val context = LocalContext.current

    var stage by remember { mutableStateOf(PreSignInStage.PIN_ENTRY) }
    var pin by remember { mutableStateOf("") }
    var verifiedPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("owner") }

    val roleOptions = remember {
        listOf(
            RolePreviewOption(
                id = "owner",
                label = "Owner",
                subtitle = "Full local studio preview controls",
                icon = Icons.Default.Shield
            ),
            RolePreviewOption(
                id = "admin",
                label = "Admin",
                subtitle = "Room moderation & review view",
                icon = Icons.Default.AdminPanelSettings
            ),
            RolePreviewOption(
                id = "member",
                label = "Member",
                subtitle = "Standard community member view",
                icon = Icons.Default.Person
            )
        )
    }

    fun onDigit(digit: String) {
        if (stage != PreSignInStage.PIN_ENTRY) return
        val lockoutSec = co.znxdnd.echotalk.utils.RateLimiter.remainingPinLockoutSeconds("presignin_gateway")
        if (lockoutSec > 0) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            errorMessage = "Too many attempts. Wait ${lockoutSec}s."
            return
        }
        if (pin.length < 4) {
            val nextPin = pin + digit
            pin = nextPin
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (nextPin.length == 4) {
                if (repository.verifyPreSignInPin(nextPin)) {
                    co.znxdnd.echotalk.utils.RateLimiter.recordSuccessfulPin("presignin_gateway")
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    verifiedPin = nextPin
                    errorMessage = ""
                    stage = PreSignInStage.ROLE_SELECTION
                } else {
                    val cooldown = co.znxdnd.echotalk.utils.RateLimiter.recordFailedPinAttempt("presignin_gateway")
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    co.znxdnd.echotalk.utils.EchoHapticManager.getInstance(context).performAlertReceived(view)
                    errorMessage = if (cooldown > 0) "Too many attempts. Locked for ${cooldown}s." else "Access denied"
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            delay(1200)
            pin = ""
            errorMessage = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(332.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF21A1822),
            border = BorderStroke(1.dp, Color(0x33E8A5C8)),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33E8A5C8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (stage == PreSignInStage.ROLE_SELECTION) Icons.Default.Shield else Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFE8A5C8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (stage == PreSignInStage.ROLE_SELECTION) "Select Session Role" else "Security Access",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (stage == PreSignInStage.ROLE_SELECTION) "Local preview session" else "Enter 4-digit PIN",
                                fontSize = 10.sp,
                                color = Color(0xFFC4A1B3)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("presignin_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "PreSignInGatewayStage"
                ) { currentStage ->
                    if (currentStage == PreSignInStage.PIN_ENTRY) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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
                                    textAlign = TextAlign.Center
                                )
                            }

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
                                                        .testTag(
                                                            if (digit == "DEL") "presignin_pin_del"
                                                            else "presignin_pin_digit_$digit"
                                                        )
                                                        .clickable {
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
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            roleOptions.forEach { option ->
                                val isSelected = selectedRole == option.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .testTag("presignin_role_${option.id}")
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedRole = option.id
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color(0x33E8A5C8) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFE8A5C8) else Color.White.copy(alpha = 0.10f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = option.label,
                                            tint = if (isSelected) Color(0xFFE8A5C8) else Color(0xFFC4A1B3),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.label,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = option.subtitle,
                                                fontSize = 11.sp,
                                                color = Color(0xFFC4A1B3)
                                            )
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedRole = option.id },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFFE8A5C8),
                                                unselectedColor = Color(0xFF7D6B77)
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    val result = repository.unlockPreSignInPreviewSession(
                                        pin = verifiedPin,
                                        selectedRole = selectedRole
                                    )
                                    result.onSuccess { unlockedUser ->
                                        onAccessGranted(unlockedUser.id)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8A5C8)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("presignin_enter_app_button")
                            ) {
                                Text(
                                    text = "Open EchoTalks",
                                    color = Color(0xFF0F0E17),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
