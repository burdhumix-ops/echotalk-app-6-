package co.znxdnd.echotalk.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.auth.FirebaseAuthManager
import co.znxdnd.echotalk.data.remote.SupabaseConfig
import co.znxdnd.echotalk.ui.components.AdaptiveContainer
import co.znxdnd.echotalk.ui.components.PreSignInGatewayDialog
import co.znxdnd.echotalk.ui.components.tactilePress
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * AuthScreen: Premium authentication portal providing email/password registration,
 * secure sign-in, password recovery, and Google Identity via Credential Manager.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: (userId: String) -> Unit,
    onPreSignInPreviewSuccess: (userId: String) -> Unit = onAuthSuccess
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val authManager = remember { FirebaseAuthManager.getInstance(context) }
    val isLoading by authManager.isLoading.collectAsState()
    val authError by authManager.authError.collectAsState()

    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var localValidationError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var resetEmailSentMessage by remember { mutableStateOf<String?>(null) }
    var logoTapCount by remember { mutableIntStateOf(0) }
    var showPreSignInGatewayDialog by remember { mutableStateOf(false) }
    val silentInteractionSource = remember { MutableInteractionSource() }

    AdaptiveContainer(maxWidth = 480.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                    .testTag("auth_logo_gateway_trigger")
                    .clickable(
                        interactionSource = silentInteractionSource,
                        indication = null
                    ) {
                        val nextCount = logoTapCount + 1
                        if (nextCount >= 15) {
                            logoTapCount = 0
                            showPreSignInGatewayDialog = true
                        } else {
                            logoTapCount = nextCount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Forum,
                    contentDescription = "EchoTalks",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "EchoTalks",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (authMode == AuthMode.LOGIN) {
                    "Welcome back to your private social lounge"
                } else {
                    "Create your account & join the crew"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Mode Selector Tabs (Log In / Create Account)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                authMode = AuthMode.LOGIN
                                localValidationError = null
                            },
                        color = if (authMode == AuthMode.LOGIN) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = if (authMode == AuthMode.LOGIN) 2.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Log In",
                                fontSize = 14.sp,
                                fontWeight = if (authMode == AuthMode.LOGIN) FontWeight.Bold else FontWeight.Medium,
                                color = if (authMode == AuthMode.LOGIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                authMode = AuthMode.REGISTER
                                localValidationError = null
                            },
                        color = if (authMode == AuthMode.REGISTER) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = if (authMode == AuthMode.REGISTER) 2.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Create Account",
                                fontSize = 14.sp,
                                fontWeight = if (authMode == AuthMode.REGISTER) FontWeight.Bold else FontWeight.Medium,
                                color = if (authMode == AuthMode.REGISTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!SupabaseConfig.isConfigured) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add SUPABASE_URL & SUPABASE_PUBLISHABLE_KEY in Secrets to enable live cloud sign-in.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Error Feedback Banner
            val displayError = localValidationError ?: authError
            AnimatedVisibility(visible = displayError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = displayError ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Registration: Full Name
            if (authMode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Email Address
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    localValidationError = null
                },
                label = { Text("Email Address") },
                placeholder = { Text("you@example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    localValidationError = null
                },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (authMode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Registration: Confirm Password
            if (authMode == AuthMode.REGISTER) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localValidationError = null
                    },
                    label = { Text("Confirm Password") },
                    leadingIcon = {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_confirm_password_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // Forgot Password Link (Login mode only)
            if (authMode == AuthMode.LOGIN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        forgotPasswordEmail = email
                        showForgotPasswordDialog = true
                    }) {
                        Text(
                            text = "Forgot Password?",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Submit Action
            Button(
                onClick = {
                    focusManager.clearFocus()

                    // Local Pre-Validation
                    val cleanEmail = email.trim()
                    if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                        localValidationError = "Please enter a valid email address."
                        return@Button
                    }
                    if (password.length < 6) {
                        localValidationError = "Password must be at least 6 characters."
                        return@Button
                    }
                    if (authMode == AuthMode.REGISTER && password != confirmPassword) {
                        localValidationError = "Passwords do not match."
                        return@Button
                    }

                    scope.launch {
                        if (authMode == AuthMode.LOGIN) {
                            val res = authManager.signInWithEmail(cleanEmail, password)
                            if (res.isSuccess) {
                                val user = res.getOrNull()
                                onAuthSuccess(user?.uid ?: "user_${cleanEmail.hashCode()}")
                            }
                        } else {
                            val res = authManager.signUpWithEmail(
                                email = cleanEmail,
                                password = password,
                                displayName = displayName
                            )
                            if (res.isSuccess) {
                                val user = res.getOrNull()
                                onAuthSuccess(user?.uid ?: "user_${cleanEmail.hashCode()}")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_submit_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (authMode == AuthMode.LOGIN) "Log In" else "Create Account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider: OR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Google Sign-In Button
            OutlinedButton(
                onClick = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        scope.launch {
                            val result = authManager.signInWithGoogle(activity)
                            if (result.isSuccess) {
                                val uid = authManager.currentUser.value?.uid ?: "user_google"
                                onAuthSuccess(uid)
                            }
                        }
                    } else {
                        localValidationError = "Google Sign-In requires an active screen window."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_google_button"),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Google",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continue with Google",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter your registered email address and we'll send a password recovery link.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { forgotPasswordEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = forgotPasswordEmail.trim()
                        if (clean.isNotBlank()) {
                            scope.launch {
                                val res = authManager.sendPasswordReset(clean)
                                showForgotPasswordDialog = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Password reset email sent to $clean", Toast.LENGTH_LONG).show()
                                } else {
                                    val errMsg = res.exceptionOrNull()?.message ?: "Could not send password reset email."
                                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPreSignInGatewayDialog) {
        PreSignInGatewayDialog(
            onDismiss = {
                logoTapCount = 0
                showPreSignInGatewayDialog = false
            },
            onAccessGranted = { uid ->
                logoTapCount = 0
                onPreSignInPreviewSuccess(uid)
            }
        )
    }
}
