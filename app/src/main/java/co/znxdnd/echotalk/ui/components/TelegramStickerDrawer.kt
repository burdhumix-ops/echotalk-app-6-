package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import co.znxdnd.echotalk.data.stickers.TelegramGifItem
import co.znxdnd.echotalk.data.stickers.TelegramStickerItem
import co.znxdnd.echotalk.data.stickers.TelegramStickerManager
import co.znxdnd.echotalk.data.stickers.TelegramStickerPack
import co.znxdnd.echotalk.utils.EchoHapticManager
import kotlinx.coroutines.launch

enum class StickerDrawerTab(val title: String, val icon: String) {
    STICKERS("Stickers", "🎭"),
    GIFS("GIFs", "🎬"),
    EMOJIS("Emojis", "😊"),
    STORE("Pack Store", "📦")
}

/**
 * TelegramStickerDrawer:
 * Bottom sheet modal providing Telegram sticker packages, animated GIFs,
 * in-app offline download progress, and 1-tap quick sending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramStickerDrawer(
    onSendSticker: (TelegramStickerItem) -> Unit,
    onSendGif: (TelegramGifItem) -> Unit,
    onSendEmoji: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val stickerManager = remember { TelegramStickerManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val downloadedPacks by stickerManager.downloadedPackIds.collectAsState()
    val progressMap by stickerManager.downloadProgressMap.collectAsState()
    val allPacks = remember(downloadedPacks, progressMap) { stickerManager.getAllPacks() }

    var selectedTab by remember { mutableStateOf(StickerDrawerTab.STICKERS) }
    var selectedPackId by remember { mutableStateOf(allPacks.firstOrNull()?.id ?: "duck_classics") }
    var selectedGifCategory by remember { mutableStateOf("Trending") }

    val currentPack = allPacks.find { it.id == selectedPackId } ?: allPacks.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            ) {}
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(horizontal = 12.dp)
        ) {
            // Top Tab Navigation: STICKERS | GIFS | EMOJIS | STORE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StickerDrawerTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedTab = tab
                                    EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(tab.icon, fontSize = 13.sp)
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(8.dp))

            // TAB 1: STICKERS VIEW
            if (selectedTab == StickerDrawerTab.STICKERS) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pack Selector Chips Bar
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(allPacks, key = { it.id }) { pack ->
                            val isPackSelected = pack.id == selectedPackId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isPackSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isPackSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedPackId = pack.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = pack.iconEmoji, fontSize = 15.sp)
                                    Text(
                                        text = pack.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isPackSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (pack.isDownloaded) {
                                        Text("✓", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (currentPack != null) {
                        val isDownloaded = currentPack.isDownloaded
                        val downloadProgress = progressMap[currentPack.id]

                        // Download Banner if not installed
                        if (!isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Download ${currentPack.name} (${currentPack.sizeKb} KB)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Save in-app offline sticker library for instant 0ms sending.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (downloadProgress != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { downloadProgress },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                stickerManager.downloadPack(currentPack.id)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        enabled = downloadProgress == null
                                    ) {
                                        Text(if (downloadProgress != null) "Downloading..." else "Download", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Sticker Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentPack.stickers, key = { it.id }) { sticker ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            EchoHapticManager.getInstance(context).performMessageSent(view)
                                            onSendSticker(sticker)
                                            onDismiss()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (sticker.mediaUrl.startsWith("http")) {
                                            AsyncImage(
                                                model = sticker.mediaUrl,
                                                contentDescription = sticker.label,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        } else {
                                            Text(text = sticker.emoji, fontSize = 38.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: GIFS VIEW
            else if (selectedTab == StickerDrawerTab.GIFS) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Category Filters Row
                    val gifCategories = listOf("Trending", "Reactions", "Gaming", "Laughter", "Celebration")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(gifCategories) { category ->
                            val isSelected = selectedGifCategory == category
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedGifCategory = category }
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    val gifs = remember(selectedGifCategory) { stickerManager.getCuratedGifs(selectedGifCategory) }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gifs, key = { it.id }) { gif ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        EchoHapticManager.getInstance(context).performMessageSent(view)
                                        onSendGif(gif)
                                        onDismiss()
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = gif.mediaUrl,
                                        contentDescription = gif.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Lower title gradient badge
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                                )
                                            )
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(gif.previewEmoji, fontSize = 12.sp)
                                            Text(
                                                text = gif.title,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 3: EMOJIS VIEW
            else if (selectedTab == StickerDrawerTab.EMOJIS) {
                val emojiList = listOf(
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥹", "😊",
                    "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙",
                    "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
                    "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒", "🙄",
                    "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                    "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳",
                    "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮", "😯", "😲",
                    "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
                    "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡",
                    "👍", "👎", "👏", "🙌", "🫶", "🤝", "🔥", "💯", "🎉", "❤️",
                    "✨", "🚀", "💡", "🎮", "🏆", "🍕", "🍔", "☕", "🍺", "🥂"
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(emojiList) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    EchoHapticManager.getInstance(context).performEmojiSwelling(view, 1)
                                    onSendEmoji(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            // TAB 4: STICKER PACK STORE & OFFLINE DOWNLOAD MANAGER
            else if (selectedTab == StickerDrawerTab.STORE) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DownloadDone,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "In-App Offline Sticker Library",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Downloaded packs stay cached in private app storage. No external permissions needed.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(allPacks, key = { it.id }) { pack ->
                        val isDl = pack.isDownloaded
                        val prog = progressMap[pack.id]

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = pack.iconEmoji, fontSize = 22.sp)
                                    }

                                    Column {
                                        Text(
                                            text = pack.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "by ${pack.author} • ${pack.stickers.size} stickers • ${pack.sizeKb} KB",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (prog != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { prog },
                                                modifier = Modifier.width(140.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }

                                if (isDl) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "✓ Offline",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        if (pack.id != "duck_classics") {
                                            IconButton(
                                                onClick = { stickerManager.deleteDownloadedPack(pack.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Remove pack",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                EchoHapticManager.getInstance(context).performTelegramDmContextMenu(view)
                                                stickerManager.downloadPack(pack.id)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        enabled = prog == null
                                    ) {
                                        Text(
                                            text = if (prog != null) "Downloading..." else "Download",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
