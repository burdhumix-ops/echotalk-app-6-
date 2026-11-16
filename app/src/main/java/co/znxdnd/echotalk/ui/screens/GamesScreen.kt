package co.znxdnd.echotalk.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.znxdnd.echotalk.data.model.ArcadeGameState
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.di.DatabaseModule
import co.znxdnd.echotalk.ui.components.ChessAiDifficulty
import co.znxdnd.echotalk.ui.components.ChessBottomControlPill
import co.znxdnd.echotalk.ui.components.ChessMoveHistoryStrip
import co.znxdnd.echotalk.ui.components.ChessPlayerHudBar
import co.znxdnd.echotalk.ui.components.LocalFloatingBottomBarClearance
import co.znxdnd.echotalk.ui.components.PawnPromotionDialog
import co.znxdnd.echotalk.ui.components.TournamentChessBoard
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import co.znxdnd.echotalk.audio.AudioEffectManager
import androidx.compose.ui.platform.testTag
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class GamePeekInfo(
    val id: String,
    val title: String,
    val icon: String,
    val badge: String,
    val playerCount: String,
    val rules: String,
    val accentColor: Color
)

data class FloatingTaunt(
    val id: Long = System.currentTimeMillis() + Random.nextLong(1000),
    val emoji: String,
    val offsetX: Float = Random.nextFloat() * 160f - 80f
)

@Composable
fun GamesScreen(
    repository: EchoTalkRepository,
    initialGameId: String? = null,
    opponentName: String? = null
) {
    var activeGame by remember { mutableStateOf<String?>(initialGameId) }
    var gameMode by remember { mutableStateOf("ai") } // "ai" or "pass_and_play"
    var peekingGame by remember { mutableStateOf<GamePeekInfo?>(null) }
    val floatingTaunts = remember { mutableStateListOf<FloatingTaunt>() }
    val coroutineScope = rememberCoroutineScope()

    val gameCatalog = remember {
        listOf(
            GamePeekInfo(
                id = "chess",
                title = "Grandmaster Chess",
                icon = "♟️",
                badge = "Strategy 8x8",
                playerCount = "1v1 vs AI or Crew DM",
                rules = "Full legal move generator, piece captures, check/checkmate detection, and EchoBot AI.",
                accentColor = Color(0xFFFBBF24)
            ),
            GamePeekInfo(
                id = "mafia",
                title = "Mafia Roundtable",
                icon = "🎭",
                badge = "Party Game Host",
                playerCount = "5-10 Players",
                rules = "Interactive role manager for room drop-ins: secret role peeker, night elimination, doctor shield, detective check, and dawn trial voting.",
                accentColor = Color(0xFFC084FC)
            )
        )
    }

    LaunchedEffect(initialGameId) {
        if (initialGameId != null) activeGame = initialGameId
    }

    fun fireTaunt(emoji: String) {
        val taunt = FloatingTaunt(emoji = emoji)
        floatingTaunts.add(taunt)
        coroutineScope.launch {
            delay(2200)
            floatingTaunts.remove(taunt)
        }
    }

    val floatingBottomClearance = LocalFloatingBottomBarClearance.current

    if (activeGame != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = floatingBottomClearance)
        ) {
            // Slim borderless top bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeGame = null }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Hub", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (activeGame) {
                                "chess" -> "Grandmaster Chess"
                                "mafia" -> "Mafia Roundtable"
                                else -> "Arcade Match"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (opponentName != null) "• vs $opponentName" else if (gameMode == "ai") "• AI" else "• Local",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.size(38.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)
            }

            // In-Game Voice & Spectator Taunts Bar
            InGameVoiceAndTauntBar(
                onFireTaunt = { emoji -> fireTaunt(emoji) }
            )

            // Game Engine Viewport with Floating Taunts Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = if (activeGame == "chess") 8.dp else 12.dp,
                        vertical = if (activeGame == "chess") 4.dp else 12.dp
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                when (activeGame) {
                    "chess" -> ChessGameView(
                        mode = gameMode,
                        opponent = opponentName,
                        repository = repository
                    )
                    "mafia" -> MafiaGameView()
                }

                // Floating Animated Taunts Overlay across the game board
                floatingTaunts.forEach { taunt ->
                    AnimatedTauntEmoji(taunt = taunt)
                }
            }
        }
    } else {
        // Arcade Hub Overview
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = floatingBottomClearance + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arcade Hub",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hold logo to peek",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CLEAN LOGO GRID (Section 6: Minimalist icon-only logos with zero-delay touch-and-hold peek engine)
                Text(
                    text = "QUICK PEEK & PLAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    gameCatalog.forEach { gameInfo ->
                        MinimalistGameLogo(
                            info = gameInfo,
                            onQuickTap = { activeGame = gameInfo.id },
                            onPeekStart = { peekingGame = gameInfo },
                            onPeekEnd = { peekingGame = null }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector: vs AI Bot vs Pass & Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { gameMode = "ai" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gameMode == "ai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (gameMode == "ai") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("vs EchoBot AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { gameMode = "pass_and_play" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gameMode == "pass_and_play") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (gameMode == "pass_and_play") Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pass & Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Detailed Catalog Cards
            item {
                Text(
                    text = "FEATURED BOARD GAMES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            items(gameCatalog.size) { index ->
                val game = gameCatalog[index]
                GameCatalogCard(
                    title = game.title,
                    badge = game.badge,
                    description = game.rules,
                    icon = game.icon,
                    gradientColor = game.accentColor,
                    onPlay = { activeGame = game.id }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Frosted Glass Peek Info Modal (Opens on onTouchStart, snaps closed on release)
        if (peekingGame != null) {
            Dialog(
                onDismissRequest = { peekingGame = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { peekingGame = null },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, peekingGame!!.accentColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(peekingGame!!.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = peekingGame!!.icon, fontSize = 36.sp)
                            }
                            Text(
                                text = peekingGame!!.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = peekingGame!!.badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = peekingGame!!.accentColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(peekingGame!!.accentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "👥 Players: ${peekingGame!!.playerCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = peekingGame!!.rules,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    val targetId = peekingGame!!.id
                                    peekingGame = null
                                    activeGame = targetId
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = peekingGame!!.accentColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Play Now", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalistGameLogo(
    info: GamePeekInfo,
    onQuickTap: () -> Unit,
    onPeekStart: () -> Unit,
    onPeekEnd: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }

    val scale = remember { Animatable(1.0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(info.accentColor.copy(alpha = 0.15f))
            .border(2.dp, info.accentColor.copy(alpha = 0.4f), CircleShape)
            .pointerInput(info.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    // Immediate onTouchStart: scale(1.05) with spring physics and open frosted glass info modal
                    coroutineScope.launch {
                        scale.animateTo(
                            1.05f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    haptic.performArcadePeek(view)
                    onPeekStart()

                    val up = waitForUpOrCancellation()
                    val duration = System.currentTimeMillis() - startTime
                    if (up != null && duration < 200) {
                        // Single quick tap under 200ms opens the full-screen game directly
                        coroutineScope.launch {
                            scale.snapTo(1.0f)
                        }
                        onPeekEnd()
                        haptic.performArcadeSelect(view)
                        onQuickTap()
                    } else {
                        // Instant Release Snap-Back: snaps preview closed with zero delay
                        coroutineScope.launch {
                            scale.snapTo(1.0f)
                        }
                        onPeekEnd()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = info.icon, fontSize = 36.sp)
    }
}

@Composable
fun InGameVoiceAndTauntBar(
    onFireTaunt: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }

    var isMuted by remember { mutableStateOf(false) }
    var showTaunts by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color.Red else Color(0xFF10B981))
                    )
                    Text(
                        text = if (isMuted) "Match Audio: Muted" else "Live Match Voice Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            haptic.performPixelClick(view)
                            isMuted = !isMuted
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            modifier = Modifier.size(18.dp),
                            tint = if (isMuted) Color.Red else Color(0xFF10B981)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performPixelClick(view)
                            showTaunts = !showTaunts
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Celebration,
                            contentDescription = "Taunts",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFFBBF24)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showTaunts) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val taunts = listOf("🔥", "👑", "😂", "♟️", "💀", "💥", "👏", "⚡")
                    taunts.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clickable {
                                    haptic.performMessengerEmojiSelect(view, emoji)
                                    onFireTaunt(emoji)
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTauntEmoji(taunt: FloatingTaunt) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1.0f) }

    LaunchedEffect(taunt.id) {
        launch {
            offsetY.animateTo(-160f, animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1200)
            alpha.animateTo(0f, animationSpec = tween(durationMillis = 800))
        }
    }

    Box(
        modifier = Modifier
            .offset(x = taunt.offsetX.dp, y = offsetY.value.dp)
            .scale(1.2f)
    ) {
        Text(
            text = taunt.emoji,
            fontSize = 32.sp,
            color = Color.White.copy(alpha = alpha.value)
        )
    }
}

@Composable
fun GameCatalogCard(
    title: String,
    badge: String,
    description: String,
    icon: String,
    gradientColor: Color,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable { onPlay() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(gradientColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 24.sp)
                }

                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = gradientColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(gradientColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)

            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class ChessMatchSnapshot(
    val fen: String,
    val lastMoveFrom: Pair<Int, Int>?,
    val lastMoveTo: Pair<Int, Int>?,
    val capturedWhiteCodes: List<String>,
    val capturedBlackCodes: List<String>,
    val moveHistory: List<String>,
    val whiteTimeMs: Long,
    val blackTimeMs: Long,
    val lastMoveText: String
)

private data class PendingPromotion(
    val fromR: Int,
    val fromC: Int,
    val toR: Int,
    val toC: Int,
    val isWhite: Boolean
)

// -------------------------------------------------------------
// CHESS ENGINE COMPONENT (TOURNAMENT SLATE FULL-GAME ARENA)
// -------------------------------------------------------------
@Composable
fun ChessGameView(
    mode: String,
    opponent: String?,
    repository: EchoTalkRepository = EchoTalkRepository.getInstance()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState()
    val allUsers by repository.users.collectAsState()

    val matchId = remember(opponent, mode) {
        val cleanOpp = (opponent ?: "ai").lowercase().replace(" ", "_").replace("@", "")
        "chess_match_$cleanOpp"
    }

    val matchedOpponentUser = remember(opponent, allUsers) {
        if (opponent.isNullOrBlank()) null
        else allUsers.find {
            it.name.equals(opponent, ignoreCase = true) ||
                it.handle.equals(opponent, ignoreCase = true) ||
                it.id.equals(opponent, ignoreCase = true)
        }
    }

    val whiteUsername = currentUser.name.ifBlank { "You" }
    val whiteFlag = ChessBoardManager.countryCodeToFlagEmoji(currentUser.countryCode)
    val blackUsername = if (mode == "ai" && opponent.isNullOrBlank()) "EchoBot" else (matchedOpponentUser?.name ?: opponent ?: "Player 2")
    val blackFlag = when {
        matchedOpponentUser != null -> ChessBoardManager.countryCodeToFlagEmoji(matchedOpponentUser.countryCode)
        mode == "ai" && opponent.isNullOrBlank() -> "🤖"
        else -> "🌐"
    }

    val initialClockMs = 5 * 60 * 1000L // 5+0 Blitz match clocks
    var currentFen by remember { mutableStateOf(ChessBoardManager.STARTING_FEN) }
    var boardState by remember { mutableStateOf(ChessBoardManager.parseFen(currentFen)) }
    var board by remember { mutableStateOf(boardState.board) }
    var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastMoveFrom by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastMoveTo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var turn by remember { mutableStateOf("White") }
    var capturedWhiteCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var capturedBlackCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var moveHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var undoStack by remember { mutableStateOf<List<ChessMatchSnapshot>>(emptyList()) }
    var winner by remember { mutableStateOf<String?>(null) }
    var lastMoveText by remember { mutableStateOf("White to move") }
    var lastSyncedTimestamp by remember { mutableLongStateOf(0L) }
    var aiDifficulty by remember { mutableStateOf(ChessAiDifficulty.TACTICAL) }
    var isAiCalculating by remember { mutableStateOf(false) }
    var isBoardFlipped by remember { mutableStateOf(false) }
    var pendingPromotion by remember { mutableStateOf<PendingPromotion?>(null) }
    var hintMove by remember { mutableStateOf<Pair<Pair<Int, Int>, Pair<Int, Int>>?>(null) }
    var whiteTimeMs by remember { mutableLongStateOf(initialClockMs) }
    var blackTimeMs by remember { mutableLongStateOf(initialClockMs) }

    // Live Blitz Clock Tick (starts once opening move is played)
    LaunchedEffect(turn, winner, pendingPromotion, moveHistory.size) {
        if (winner == null && pendingPromotion == null && moveHistory.isNotEmpty()) {
            while (winner == null && pendingPromotion == null) {
                delay(1000L)
                if (turn == "White") {
                    whiteTimeMs = (whiteTimeMs - 1000L).coerceAtLeast(0L)
                    if (whiteTimeMs == 0L) {
                        winner = "Black (Time)"
                        lastMoveText = "Time expired • Black wins"
                        haptic.performArcadeLoss(view)
                    }
                } else {
                    blackTimeMs = (blackTimeMs - 1000L).coerceAtLeast(0L)
                    if (blackTimeMs == 0L) {
                        winner = "White (Time)"
                        lastMoveText = "Time expired • White wins"
                        haptic.performArcadeWin(view)
                    }
                }
            }
        }
    }

    // Observe real-time multiplayer moves when online
    LaunchedEffect(matchId) {
        try {
            DatabaseModule.observeArcadeGameState(matchId).collect { remoteState ->
                if (remoteState != null && remoteState.updatedAt > lastSyncedTimestamp && remoteState.chessBoard.size == 64) {
                    val syncedBoard = ChessEngine.listToBoard(remoteState.chessBoard)
                    board = syncedBoard
                    turn = remoteState.currentTurn
                    currentFen = ChessBoardManager.toFen(syncedBoard, if (remoteState.currentTurn == "White") "w" else "b")
                    boardState = ChessBoardManager.parseFen(currentFen)
                    capturedWhiteCodes = remoteState.capturedWhite
                    capturedBlackCodes = remoteState.capturedBlack
                    winner = remoteState.winner
                    lastMoveText = remoteState.lastMoveDescription.ifBlank { "Move synced" }
                    if (remoteState.lastMoveFromRow != null && remoteState.lastMoveFromCol != null &&
                        remoteState.lastMoveToRow != null && remoteState.lastMoveToCol != null) {
                        lastMoveFrom = Pair(remoteState.lastMoveFromRow, remoteState.lastMoveFromCol)
                        lastMoveTo = Pair(remoteState.lastMoveToRow, remoteState.lastMoveToCol)
                    }
                    lastSyncedTimestamp = remoteState.updatedAt
                }
            }
        } catch (_: Exception) {
            // Local match mode continues gracefully
        }
    }

    fun syncStateToFirestore(
        updatedBoard: Array<Array<String>>,
        nextTurn: String,
        moveDesc: String,
        matchWinner: String?,
        capW: List<String>,
        capB: List<String>,
        fromRow: Int? = null,
        fromCol: Int? = null,
        toRow: Int? = null,
        toCol: Int? = null
    ) {
        val now = System.currentTimeMillis()
        lastSyncedTimestamp = now
        val state = ArcadeGameState(
            gameId = matchId,
            gameType = "chess",
            challenger = whiteUsername,
            opponent = blackUsername,
            currentTurn = nextTurn,
            chessBoard = ChessEngine.boardToList(updatedBoard),
            capturedWhite = capW,
            capturedBlack = capB,
            lastMoveDescription = moveDesc,
            winner = matchWinner,
            updatedAt = now,
            lastMoveFromRow = fromRow,
            lastMoveFromCol = fromCol,
            lastMoveToRow = toRow,
            lastMoveToCol = toCol
        )
        DatabaseModule.saveArcadeGameState(
            gameState = state,
            onSuccess = {},
            onFailure = {}
        )
    }

    fun reset() {
        currentFen = ChessBoardManager.STARTING_FEN
        boardState = ChessBoardManager.parseFen(currentFen)
        board = boardState.board
        selectedSquare = null
        lastMoveFrom = null
        lastMoveTo = null
        turn = "White"
        capturedWhiteCodes = emptyList()
        capturedBlackCodes = emptyList()
        moveHistory = emptyList()
        undoStack = emptyList()
        winner = null
        pendingPromotion = null
        hintMove = null
        whiteTimeMs = initialClockMs
        blackTimeMs = initialClockMs
        isAiCalculating = false
        lastMoveText = "White to move"
        haptic.performPixelClick(view)
        syncStateToFirestore(board, "White", "Match reset", null, emptyList(), emptyList())
    }

    fun undoLastTurn() {
        if (undoStack.isEmpty() || isAiCalculating) return
        val stepsToPop = if (mode == "ai" && turn == "White" && undoStack.size >= 2) 2 else 1
        val targetIndex = (undoStack.size - stepsToPop).coerceAtLeast(0)
        val snap = undoStack[targetIndex]
        undoStack = undoStack.take(targetIndex)
        currentFen = snap.fen
        boardState = ChessBoardManager.parseFen(snap.fen)
        board = boardState.board
        lastMoveFrom = snap.lastMoveFrom
        lastMoveTo = snap.lastMoveTo
        capturedWhiteCodes = snap.capturedWhiteCodes
        capturedBlackCodes = snap.capturedBlackCodes
        moveHistory = snap.moveHistory
        whiteTimeMs = snap.whiteTimeMs
        blackTimeMs = snap.blackTimeMs
        lastMoveText = snap.lastMoveText
        turn = if (boardState.activeColor == "w") "White" else "Black"
        winner = null
        selectedSquare = null
        hintMove = null
        pendingPromotion = null
        haptic.performPixelClick(view)
    }

    fun executeMoveWithPromotion(fromR: Int, fromC: Int, toR: Int, toC: Int, promoPiece: String = "Q") {
        val snapshotBefore = ChessMatchSnapshot(
            fen = currentFen,
            lastMoveFrom = lastMoveFrom,
            lastMoveTo = lastMoveTo,
            capturedWhiteCodes = capturedWhiteCodes,
            capturedBlackCodes = capturedBlackCodes,
            moveHistory = moveHistory,
            whiteTimeMs = whiteTimeMs,
            blackTimeMs = blackTimeMs,
            lastMoveText = lastMoveText
        )

        val moveResult = ChessBoardManager.executeMove(boardState, fromR, fromC, toR, toC, promoPiece)
        if (moveResult == null) {
            selectedSquare = null
            haptic.performArcadeInvalid(view)
            return
        }

        undoStack = undoStack + snapshotBefore
        hintMove = null
        lastMoveFrom = Pair(fromR, fromC)
        lastMoveTo = Pair(toR, toC)
        boardState = moveResult.newState
        currentFen = moveResult.newState.fen
        board = moveResult.newState.board
        moveHistory = moveHistory + moveResult.moveNotation

        val capturedPiece = moveResult.capturedPiece
        var newCapWhite = capturedWhiteCodes
        var newCapBlack = capturedBlackCodes

        if (capturedPiece != null) {
            if (capturedPiece.first().isUpperCase()) {
                newCapWhite = newCapWhite + capturedPiece
            } else {
                newCapBlack = newCapBlack + capturedPiece
            }
            PoliteSoundSynthesizer.playCaptureSound()
            AudioEffectManager.getInstance(context).playArcadeCaptureSound()
        } else {
            PoliteSoundSynthesizer.playMoveChime()
            AudioEffectManager.getInstance(context).playArcadeMoveSound()
        }

        if (moveResult.isCheckmate) {
            winner = if (moveResult.newState.activeColor == "w") "Black" else "White"
        } else if (moveResult.isDraw) {
            winner = "Draw"
        }

        capturedWhiteCodes = newCapWhite
        capturedBlackCodes = newCapBlack
        selectedSquare = null
        val nextTurn = if (moveResult.newState.activeColor == "w") "White" else "Black"
        turn = nextTurn
        lastMoveText = "${moveResult.moveNotation} • $nextTurn to move"

        haptic.performArcadeMove(view, isCapture = (capturedPiece != null))
        if (winner == "White") haptic.performArcadeWin(view)
        else if (winner == "Black") haptic.performArcadeLoss(view)

        syncStateToFirestore(board, nextTurn, moveResult.moveNotation, winner, newCapWhite, newCapBlack, fromR, fromC, toR, toC)

        if (mode == "ai" && nextTurn == "Black" && winner == null) {
            coroutineScope.launch {
                isAiCalculating = true
                delay(180)
                val searchResult = ChessBoardManager.findBestMoveAsync(boardState, depth = aiDifficulty.depth)
                val chosen = searchResult.bestMove
                isAiCalculating = false

                if (chosen != null) {
                    val aiSnap = ChessMatchSnapshot(
                        fen = currentFen,
                        lastMoveFrom = lastMoveFrom,
                        lastMoveTo = lastMoveTo,
                        capturedWhiteCodes = capturedWhiteCodes,
                        capturedBlackCodes = capturedBlackCodes,
                        moveHistory = moveHistory,
                        whiteTimeMs = whiteTimeMs,
                        blackTimeMs = blackTimeMs,
                        lastMoveText = lastMoveText
                    )
                    val aiResult = ChessBoardManager.executeMove(
                        boardState,
                        chosen.first.first,
                        chosen.first.second,
                        chosen.second.first,
                        chosen.second.second
                    )
                    if (aiResult != null) {
                        undoStack = undoStack + aiSnap
                        lastMoveFrom = Pair(chosen.first.first, chosen.first.second)
                        lastMoveTo = Pair(chosen.second.first, chosen.second.second)
                        boardState = aiResult.newState
                        currentFen = aiResult.newState.fen
                        board = aiResult.newState.board
                        moveHistory = moveHistory + aiResult.moveNotation
                        if (aiResult.capturedPiece != null) {
                            capturedWhiteCodes = capturedWhiteCodes + aiResult.capturedPiece
                            PoliteSoundSynthesizer.playCaptureSound()
                            AudioEffectManager.getInstance(context).playArcadeCaptureSound()
                        } else {
                            PoliteSoundSynthesizer.playMoveChime()
                            AudioEffectManager.getInstance(context).playArcadeMoveSound()
                        }
                        if (aiResult.isCheckmate) {
                            winner = "Black"
                            haptic.performArcadeLoss(view)
                        } else if (aiResult.isDraw) {
                            winner = "Draw"
                        } else {
                            haptic.performArcadeMove(view, isCapture = (aiResult.capturedPiece != null))
                        }
                        turn = "White"
                        lastMoveText = "${aiResult.moveNotation} • White to move"
                        syncStateToFirestore(
                            board, "White", aiResult.moveNotation, winner,
                            capturedWhiteCodes, capturedBlackCodes,
                            chosen.first.first, chosen.first.second, chosen.second.first, chosen.second.second
                        )
                    }
                }
            }
        }
    }

    val (whiteMat, blackMat) = remember(currentFen) {
        ChessBoardManager.computeMaterialScore(board)
    }
    val whiteAdv = (whiteMat - blackMat).coerceAtLeast(0)
    val blackAdv = (blackMat - whiteMat).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Player HUD (Opponent by default, swaps when board is flipped)
        if (!isBoardFlipped) {
            ChessPlayerHudBar(
                username = blackUsername,
                countryFlag = blackFlag,
                isWhiteSide = false,
                isActiveTurn = turn == "Black" && winner == null,
                remainingTimeMs = blackTimeMs,
                capturedPieceCodes = capturedWhiteCodes,
                materialAdvantage = blackAdv,
                badgeLabel = if (mode == "ai") "AI" else null,
                testTagPrefix = "chess_black_player"
            )
        } else {
            ChessPlayerHudBar(
                username = whiteUsername,
                countryFlag = whiteFlag,
                isWhiteSide = true,
                isActiveTurn = turn == "White" && winner == null,
                remainingTimeMs = whiteTimeMs,
                capturedPieceCodes = capturedBlackCodes,
                materialAdvantage = whiteAdv,
                testTagPrefix = "chess_white_player"
            )
        }

        // Full-Width Responsive Tournament Slate 8x8 Board
        TournamentChessBoard(
            boardState = boardState,
            selectedSquare = selectedSquare,
            lastMoveFrom = lastMoveFrom,
            lastMoveTo = lastMoveTo,
            hintMove = hintMove,
            isFlipped = isBoardFlipped,
            onSquareClick = { r, c ->
                if (winner != null || isAiCalculating || pendingPromotion != null) return@TournamentChessBoard
                val piece = board[r][c]
                if (selectedSquare == null) {
                    if (piece.isNotEmpty()) {
                        val isWhitePiece = piece.first().isUpperCase()
                        if ((turn == "White" && isWhitePiece) || (turn == "Black" && !isWhitePiece)) {
                            selectedSquare = Pair(r, c)
                            haptic.performArcadeSelect(view)
                        }
                    }
                } else {
                    val (sr, sc) = selectedSquare!!
                    if (sr == r && sc == c) {
                        selectedSquare = null
                    } else if (piece.isNotEmpty() &&
                        ((turn == "White" && piece.first().isUpperCase()) ||
                            (turn == "Black" && !piece.first().isUpperCase()))
                    ) {
                        // Switch selection cleanly to another friendly piece
                        selectedSquare = Pair(r, c)
                        haptic.performArcadeSelect(view)
                    } else if (ChessBoardManager.needsPawnPromotion(boardState, sr, sc, r, c)) {
                        pendingPromotion = PendingPromotion(
                            fromR = sr,
                            fromC = sc,
                            toR = r,
                            toC = c,
                            isWhite = boardState.activeColor == "w"
                        )
                    } else {
                        executeMoveWithPromotion(sr, sc, r, c)
                    }
                }
            }
        )

        // Bottom Player HUD (You by default, swaps when board is flipped)
        if (!isBoardFlipped) {
            ChessPlayerHudBar(
                username = whiteUsername,
                countryFlag = whiteFlag,
                isWhiteSide = true,
                isActiveTurn = turn == "White" && winner == null,
                remainingTimeMs = whiteTimeMs,
                capturedPieceCodes = capturedBlackCodes,
                materialAdvantage = whiteAdv,
                testTagPrefix = "chess_white_player"
            )
        } else {
            ChessPlayerHudBar(
                username = blackUsername,
                countryFlag = blackFlag,
                isWhiteSide = false,
                isActiveTurn = turn == "Black" && winner == null,
                remainingTimeMs = blackTimeMs,
                capturedPieceCodes = capturedWhiteCodes,
                materialAdvantage = blackAdv,
                badgeLabel = if (mode == "ai") "AI" else null,
                testTagPrefix = "chess_black_player"
            )
        }

        // Scrollable Algebraic Move History Strip
        ChessMoveHistoryStrip(moveHistory = moveHistory)

        // Minimalist Floating Bottom Control Pill
        ChessBottomControlPill(
            turn = turn,
            isCheck = boardState.isCheck,
            winner = winner,
            isAiMode = mode == "ai",
            isAiThinking = isAiCalculating,
            aiDifficulty = aiDifficulty,
            canUndo = undoStack.isNotEmpty(),
            statusSummary = lastMoveText,
            onUndo = { undoLastTurn() },
            onFlipBoard = {
                isBoardFlipped = !isBoardFlipped
                haptic.performPixelClick(view)
            },
            onHint = {
                if (winner == null && !isAiCalculating) {
                    haptic.performPixelClick(view)
                    coroutineScope.launch {
                        val hintResult = ChessBoardManager.findBestMoveAsync(boardState, depth = 2)
                        hintMove = hintResult.bestMove
                    }
                }
            },
            onCycleDifficulty = {
                val entries = ChessAiDifficulty.entries
                aiDifficulty = entries[(aiDifficulty.ordinal + 1) % entries.size]
                haptic.performPixelClick(view)
            },
            onReset = { reset() }
        )

        Spacer(modifier = Modifier.height(6.dp))
    }

    if (pendingPromotion != null) {
        val promo = pendingPromotion!!
        PawnPromotionDialog(
            isWhite = promo.isWhite,
            onSelectPiece = { chosenCode ->
                pendingPromotion = null
                executeMoveWithPromotion(promo.fromR, promo.fromC, promo.toR, promo.toC, chosenCode)
            },
            onDismiss = {
                pendingPromotion = null
                selectedSquare = null
            }
        )
    }
}

// -------------------------------------------------------------
// MAFIA ROUNDTABLE COMPONENT
// -------------------------------------------------------------
@Composable
fun MafiaGameView() {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = remember(context) { EchoHapticManager.getInstance(context) }

    var phase by remember { mutableStateOf("setup") } // setup, reveal, night, day
    var peekIndex by remember { mutableIntStateOf(0) }
    var isPeeking by remember { mutableStateOf(false) }

    val roles = listOf("Mafia", "Detective", "Doctor", "Citizen", "Citizen", "Citizen")
    val players = listOf("Zavier", "Chloe", "Lucas", "Kenji", "Amara", "Matteo")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (phase) {
            "setup" -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "🎭 Mafia Party Game Host", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Secret role assignments: 1 Mafia, 1 Detective, 1 Doctor, 3 Citizens. Tap begin to pass device.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                haptic.performPixelClick(view)
                                phase = "reveal"
                                peekIndex = 0
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Begin Secret Role Reveal")
                        }
                    }
                }
            }

            "reveal" -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Pass device to: ${players[peekIndex]}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isPeeking) Color(0xFF312E81) else Color(0xFF0F172A))
                                .clickable {
                                    haptic.performArcadePeek(view)
                                    isPeeking = !isPeeking
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPeeking) "YOUR ROLE:\n${roles[peekIndex]}" else "Tap to Peek Secret Identity",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performPixelClick(view)
                                if (peekIndex < players.size - 1) {
                                    peekIndex += 1
                                    isPeeking = false
                                } else {
                                    phase = "night"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (peekIndex < players.size - 1) "Next Player" else "All Roles Confirmed • Night Phase")
                        }
                    }
                }
            }

            "night" -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "🌙 Night Phase: City is Asleep", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Moderator Actions:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "1. Mafia secretly selected target.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "2. Doctor protected innocent citizen.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "3. Detective investigated suspect.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Button(
                            onClick = { phase = "setup" },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dawn Breaks • New Game")
                        }
                    }
                }
            }
        }
    }
}
