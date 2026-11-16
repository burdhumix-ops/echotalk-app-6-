package co.znxdnd.echotalk.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.znxdnd.echotalk.auth.OnboardingStep
import co.znxdnd.echotalk.ui.components.AdaptiveContainer
import co.znxdnd.echotalk.ui.components.tactilePress

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    AdaptiveContainer(maxWidth = 640.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Header: Step Progress & Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.currentStep.stepIndex > 0) {
                    IconButton(
                        onClick = { viewModel.prevStep() },
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Step",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Progress Indicator
                val progress = (uiState.currentStep.stepIndex + 1f) / OnboardingStep.entries.size.toFloat()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Step ${uiState.currentStep.stepIndex + 1} of ${OnboardingStep.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .width(140.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Skip button (visible on non-terminal steps)
                if (uiState.currentStep.stepIndex < OnboardingStep.COMPLETED.stepIndex) {
                    TextButton(
                        onClick = { viewModel.finishOnboarding(onFinish) },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body content with animated step transition
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        if (targetState.stepIndex > initialState.stepIndex) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "OnboardingStepContent"
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStepContent(onStart = { viewModel.nextStep() })
                        OnboardingStep.USERNAME -> UsernameStepContent(viewModel, uiState)
                        OnboardingStep.PROFILE_PICTURE -> ProfilePicStepContent(viewModel, uiState)
                        OnboardingStep.BASIC_PROFILE -> BasicProfileStepContent(viewModel, uiState)
                        OnboardingStep.PRIVACY_DISCOVERABILITY -> PrivacyStepContent(viewModel, uiState)
                        OnboardingStep.INTRO_DISCOVER_DMS -> IntroStepContent(viewModel, uiState)
                        OnboardingStep.COMPLETED -> CompletedStepContent(uiState, onFinish = {
                            viewModel.finishOnboarding(onFinish)
                        })
                    }
                }
            }

            // Bottom Navigation Actions
            if (uiState.currentStep != OnboardingStep.WELCOME && uiState.currentStep != OnboardingStep.COMPLETED) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Button(
                        onClick = { viewModel.nextStep() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_continue_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Forum,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome to EchoTalks",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "A real-time social platform combining zero-latency private local DMs, live audio soundscapes, and arcade gaming.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        FeatureHighlightRow(
            icon = Icons.Default.Shield,
            title = "Zero-Cloud DM Privacy",
            subtitle = "Your direct chats stay 100% on device & sync to your Google Drive vault."
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureHighlightRow(
            icon = Icons.Default.SportsEsports,
            title = "Multiplayer Arcade",
            subtitle = "Challenge crew members to Chess with AI, Ludo, and arcade classics."
        )

        Spacer(modifier = Modifier.height(16.dp))

        FeatureHighlightRow(
            icon = Icons.Default.SurroundSound,
            title = "Immersive Audio Rooms",
            subtitle = "Enjoy high-fidelity soundscapes and natural voice notes."
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_get_started_btn"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Get Started",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UsernameStepContent(viewModel: OnboardingViewModel, uiState: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Choose Your Handle",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Your unique identity for friend requests and discovery.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = { viewModel.updateUsername(it) },
            label = { Text("Username") },
            placeholder = { Text("e.g. echo_star") },
            leadingIcon = {
                Text(
                    text = "@",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            trailingIcon = {
                if (uiState.isCheckingAvailability) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (uiState.isUsernameAvailable == true && uiState.isUsernameValid) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Available", tint = Color(0xFF10B981))
                } else if (uiState.usernameError != null) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                }
            },
            isError = uiState.usernameError != null,
            supportingText = {
                if (uiState.usernameError != null) {
                    Text(uiState.usernameError ?: "", color = MaterialTheme.colorScheme.error)
                } else if (uiState.isUsernameAvailable == true) {
                    Text("Handle is available!", color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                } else {
                    Text("3-20 characters: alphanumeric and underscores only")
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_username_input"),
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun ProfilePicStepContent(viewModel: OnboardingViewModel, uiState: OnboardingUiState) {
    val presets = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=200"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pick Your Avatar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Choose an avatar preset or update later from your profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uiState.avatarUrl,
                contentDescription = "Selected Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Suggested Avatars", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { url ->
                val isSelected = uiState.avatarUrl == url
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .clickable { viewModel.updateAvatarUrl(url) }
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Avatar Preset",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicProfileStepContent(viewModel: OnboardingViewModel, uiState: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Personal Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Tell the crew a bit about yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = { viewModel.updateDisplayName(it) },
            label = { Text("Display Name") },
            placeholder = { Text("e.g. Alex Hunter") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_display_name_input"),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.bio,
            onValueChange = { viewModel.updateBio(it) },
            label = { Text("Bio") },
            placeholder = { Text("Lover of ambient soundscapes and retro arcade games...") },
            supportingText = {
                Text(
                    text = "${uiState.bio.length}/150",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            },
            minLines = 3,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_bio_input"),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Gender Identity", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Female", "Male", "Non-Binary").forEach { g ->
                FilterChip(
                    selected = uiState.gender == g,
                    onClick = { viewModel.updateGender(g) },
                    label = { Text(g) }
                )
            }
        }
    }
}

@Composable
private fun PrivacyStepContent(viewModel: OnboardingViewModel, uiState: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Privacy & Discovery",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "You are in complete control of how others see you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrivacySwitchCard(
            title = "Ghost Mode",
            subtitle = "Hide yourself completely from Crew Discovery",
            checked = uiState.ghostMode,
            onCheckedChange = { viewModel.updateGhostMode(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacySwitchCard(
            title = "Profile Visibility",
            subtitle = "Allow other users to view your bio and portfolio",
            checked = uiState.profileVisibility,
            onCheckedChange = { viewModel.updateProfileVisibility(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacySwitchCard(
            title = "Online Status",
            subtitle = "Show green presence indicator when active",
            checked = uiState.onlineStatusVisible,
            onCheckedChange = { viewModel.updateOnlineStatusVisible(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacySwitchCard(
            title = "Read Receipts",
            subtitle = "Show blue delivery and read checkmarks in DMs",
            checked = uiState.readReceipts,
            onCheckedChange = { viewModel.updateReadReceipts(it) }
        )
    }
}

@Composable
private fun PrivacySwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun IntroStepContent(viewModel: OnboardingViewModel, uiState: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Welcome to the Crew",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Here is what you can do right away:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureHighlightRow(
            icon = Icons.Default.People,
            title = "Discover & Connect",
            subtitle = "Find verified people in your area and send friendship requests."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightRow(
            icon = Icons.Default.ChatBubble,
            title = "Private Direct Messages",
            subtitle = "Send voice notes, reaction emojis, and view-once media."
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureHighlightRow(
            icon = Icons.Default.VideogameAsset,
            title = "Chess & Arcade Challenges",
            subtitle = "Play against local offline minimax AI or real-time companions."
        )
    }
}

@Composable
private fun CompletedStepContent(uiState: OnboardingUiState, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Celebration,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF10B981)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your profile is ready and securely registered. Dive in and explore EchoTalks.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_enter_app_btn"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Enter EchoTalks",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
