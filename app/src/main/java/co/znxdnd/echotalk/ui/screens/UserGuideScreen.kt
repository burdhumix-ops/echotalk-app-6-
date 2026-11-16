package co.znxdnd.echotalk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.znxdnd.echotalk.ui.components.AdaptiveContainer

data class GuideTopic(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val tips: List<String>
)

@Composable
fun UserGuideScreen(
    onBack: () -> Unit
) {
    val topics = listOf(
        GuideTopic(
            icon = Icons.Default.Lock,
            title = "Zero-Cloud Private DMs",
            description = "Direct messages are kept strictly on your device inside high-speed SQLite storage. When you enable Google Drive backup, your chat history is encrypted and saved directly to your private Google Account appDataFolder without touching any public database.",
            tips = listOf(
                "Tap the camera icon in DMs to send View-Once photos that self-destruct after viewing.",
                "Long press any message bubble to open the interactive Messenger emoji reaction tray.",
                "Voice notes feature a seekable waveform bar with duration timestamps."
            )
        ),
        GuideTopic(
            icon = Icons.Default.SportsEsports,
            title = "Arcade & Offline Chess AI",
            description = "Play Chess, Ludo, or retro arcade games with your friends or solo against a smart local AI opponent.",
            tips = listOf(
                "Offline Minimax Chess: Challenge the local AI even with airplane mode active.",
                "Real-time challenges: Tap the challenge icon in any DM conversation to send a live invite.",
                "Alpha-Beta pruning ensures fast, competitive moves at depth 3."
            )
        ),
        GuideTopic(
            icon = Icons.Default.Headphones,
            title = "Audio Soundscapes & Voice Notes",
            description = "Relax with high-fidelity synthesized background audio or exchange crisp voice notes.",
            tips = listOf(
                "Tap the Soundscape button in the top bar to play ambient soundscapes like Rain, Forest, or Cafe.",
                "Custom sound effect volume and mute switches can be customized in Settings > Sounds."
            )
        ),
        GuideTopic(
            icon = Icons.Default.Security,
            title = "Privacy & Discovery Engine",
            description = "You decide who sees your profile, presence, and activity in EchoTalks.",
            tips = listOf(
                "Ghost Mode: Hide yourself from Crew Discovery at any time.",
                "Read Receipts & Typing: Can be toggled on or off under Settings > Privacy."
            )
        )
    )

    AdaptiveContainer(maxWidth = 720.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("user_guide_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "User Guide & Knowledge Base",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Everything you need to master EchoTalks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(topics) { topic ->
                    GuideTopicCard(topic)
                }
            }
        }
    }
}

@Composable
private fun GuideTopicCard(topic: GuideTopic) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        topic.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = topic.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = topic.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            if (topic.tips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Pro Tips:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                topic.tips.forEach { tip ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = tip,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
