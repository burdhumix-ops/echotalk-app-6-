package co.znxdnd.echotalk.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.znxdnd.echotalk.data.remote.SupabaseSocialRepository
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.components.AdaptiveContainer
import kotlinx.coroutines.launch

@Composable
fun ProfileSetupScreen(
    repository: EchoTalkRepository,
    onComplete: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentUser by repository.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(currentUser.name) }
    var handle by remember { mutableStateOf(currentUser.handle.removePrefix("@")) }
    var bio by remember { mutableStateOf(currentUser.bio) }
    var avatarUrl by remember { mutableStateOf(currentUser.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" }) }
    var selectedGender by remember { mutableStateOf(currentUser.gender) }
    var selectedCountry by remember { mutableStateOf(currentUser.country) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                val uploadRes = SupabaseSocialRepository.getInstance().uploadCompressedProfileImage(
                    context = context,
                    uri = uri,
                    folderTag = "avatar"
                )
                isUploadingAvatar = false
                uploadRes.onSuccess { publicUrl ->
                    avatarUrl = publicUrl
                    Toast.makeText(context, "Photo uploaded!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Photo upload failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val presets = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=200"
    )

    AdaptiveContainer(maxWidth = 640.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = "Complete Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Avatar Display
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isUploadingAvatar,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading...")
                    } else {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose from Gallery")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Or Select Avatar Preset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(presets) { url ->
                        val isSelected = avatarUrl == url
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { avatarUrl = url }
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_setup_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it.trim().lowercase() },
                    label = { Text("Username Handle") },
                    leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_setup_handle_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 150) bio = it },
                    label = { Text("Bio") },
                    supportingText = {
                        Text(
                            text = "${bio.length}/150",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_setup_bio_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Female", "Male", "Non-Binary").forEach { g ->
                        FilterChip(
                            selected = selectedGender == g,
                            onClick = { selectedGender = g },
                            label = { Text(g) }
                        )
                    }
                }
            }

            // Save CTA
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val updated = currentUser.copy(
                            name = displayName.ifBlank { currentUser.name },
                            handle = if (handle.isNotBlank()) "@$handle" else currentUser.handle,
                            avatarUrl = avatarUrl,
                            photos = if (avatarUrl.isNotBlank()) listOf(avatarUrl) else currentUser.photos,
                            bio = bio,
                            gender = selectedGender,
                            country = selectedCountry,
                            profileCompleted = true,
                            onboardingCompleted = true
                        )
                        repository.setCurrentUser(updated)
                        repository.updateCurrentUserProfile(
                            name = updated.name,
                            handle = updated.handle,
                            bio = updated.bio,
                            gender = updated.gender,
                            age = updated.age,
                            country = updated.country,
                            countryCode = updated.countryCode,
                            photos = updated.photos,
                            bannerUrl = updated.bannerUrl,
                            audioPreviewTitle = updated.audioPreviewTitle
                        )
                        isSaving = false
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_setup_save_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save & Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
