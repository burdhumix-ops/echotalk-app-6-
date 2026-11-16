package co.znxdnd.echotalk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.ui.screens.ChessBoardManager
import co.znxdnd.echotalk.ui.screens.ChessBoardState

enum class ChessAiDifficulty(val label: String, val depth: Int) {
    CASUAL("Casual", 2),
    TACTICAL("Tactical", 3),
    MASTER("Master", 4)
}

/**
 * Top / Bottom player HUD bar showing username, country flag, captured pieces,
 * net material advantage (+N), and live tabular chess clock.
 */
@Composable
fun ChessPlayerHudBar(
    username: String,
    countryFlag: String,
    isWhiteSide: Boolean,
    isActiveTurn: Boolean,
    remainingTimeMs: Long,
    capturedPieceCodes: List<String>,
    materialAdvantage: Int,
    badgeLabel: String? = null,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isLowTime = remainingTimeMs in 1..29_999L
    val totalSeconds = (remainingTimeMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    val clockText = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    val clockBg by animateColorAsState(
        targetValue = when {
            isLowTime && isActiveTurn -> Color(0x33EF4444)
            isActiveTurn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        label = "clockBg_$testTagPrefix"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_hud"),
        shape = RoundedCornerShape(14.dp),
        color = if (isActiveTurn) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.58f else 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
        border = BorderStroke(
            width = if (isActiveTurn) 1.dp else 0.5.dp,
            color = if (isActiveTurn) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Side piece badge + Username + Flag + Captures
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isWhiteSide) Color(0xFFF1F5F9) else Color(0xFF1E293B)
                        )
                        .border(
                            1.dp,
                            if (isActiveTurn) MaterialTheme.colorScheme.primary else Color(0xFF64748B),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ChessVectorPiece(
                        pieceCode = if (isWhiteSide) "K" else "k",
                        pieceSize = 20.dp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = countryFlag,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("${testTagPrefix}_flag")
                        )
                        Text(
                            text = username,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!badgeLabel.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Captured pieces + material advantage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (capturedPieceCodes.isEmpty() && materialAdvantage <= 0) {
                            Text(
                                text = if (isActiveTurn) "To move" else "Waiting",
                                fontSize = 10.sp,
                                color = if (isActiveTurn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            capturedPieceCodes.take(7).forEach { code ->
                                ChessVectorPiece(pieceCode = code, pieceSize = 13.dp)
                            }
                            if (capturedPieceCodes.size > 7) {
                                Text(
                                    text = "+${capturedPieceCodes.size - 7}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (materialAdvantage > 0) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x2210B981)
                                ) {
                                    Text(
                                        text = "+$materialAdvantage",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right: Live Chess Clock
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = clockBg,
                border = if (isActiveTurn) {
                    BorderStroke(
                        1.dp,
                        if (isLowTime) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                } else null,
                modifier = Modifier.testTag("${testTagPrefix}_clock")
            ) {
                Text(
                    text = clockText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = when {
                        isLowTime && isActiveTurn -> Color(0xFFEF4444)
                        isActiveTurn -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Full-width responsive Tournament Slate 8x8 chess board with crisp vector pieces,
 * flip-aware algebraic coordinates (1-8 / a-h), legal target indicators, check glow, and hint highlight.
 */
@Composable
fun TournamentChessBoard(
    boardState: ChessBoardState,
    selectedSquare: Pair<Int, Int>?,
    lastMoveFrom: Pair<Int, Int>?,
    lastMoveTo: Pair<Int, Int>?,
    hintMove: Pair<Pair<Int, Int>, Pair<Int, Int>>?,
    isFlipped: Boolean,
    onSquareClick: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val files = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    val lightSlate = Color(0xFFDCE3EC)
    val darkSlate = Color(0xFF4A5B70)
    val lightLabelColor = Color(0xFF334155)
    val darkLabelColor = Color(0xFFCBD5E1)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val boardSide = minOf(maxWidth, 460.dp)
        val pieceSize = (boardSide / 8f) * 0.78f

        Surface(
            modifier = Modifier
                .size(boardSide)
                .testTag("tournament_chess_board"),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (displayRow in 0..7) {
                    val r = if (isFlipped) 7 - displayRow else displayRow
                    Row(modifier = Modifier.weight(1f)) {
                        for (displayCol in 0..7) {
                            val c = if (isFlipped) 7 - displayCol else displayCol
                            val isDark = (r + c) % 2 == 1
                            val piece = boardState.board[r][c]
                            val isSelected = selectedSquare?.first == r && selectedSquare?.second == c
                            val isLastMove = (lastMoveFrom?.first == r && lastMoveFrom?.second == c) ||
                                (lastMoveTo?.first == r && lastMoveTo?.second == c)
                            val isHintSquare = hintMove != null &&
                                ((hintMove.first.first == r && hintMove.first.second == c) ||
                                    (hintMove.second.first == r && hintMove.second.second == c))
                            val isLegalTarget = selectedSquare != null &&
                                ChessBoardManager.isValidMove(boardState, selectedSquare.first, selectedSquare.second, r, c)
                            val isCheckedKing = boardState.isCheck &&
                                ((boardState.activeColor == "w" && piece == "K") ||
                                    (boardState.activeColor == "b" && piece == "k"))

                            val baseSquareColor = if (isDark) darkSlate else lightSlate
                            val overlayColor = when {
                                isSelected -> Color(0x99F59E0B)
                                isHintSquare -> Color(0x7738BDF8)
                                isLastMove -> Color(0x55FBBF24)
                                else -> Color.Transparent
                            }
                            val coordColor = if (isDark) darkLabelColor else lightLabelColor

                            val pieceScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.12f else 1.0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "pieceScale_${r}_$c"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(baseSquareColor)
                                    .background(overlayColor)
                                    .then(
                                        when {
                                            isSelected -> Modifier.border(1.8.dp, Color(0xFFF59E0B))
                                            isHintSquare -> Modifier.border(1.8.dp, Color(0xFF38BDF8))
                                            else -> Modifier
                                        }
                                    )
                                    .testTag(if (isLastMove) "last_move_square_${r}_${c}" else "chess_square_${r}_${c}")
                                    .clickable { onSquareClick(r, c) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCheckedKing) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                listOf(
                                                    Color(0xCCEF4444),
                                                    Color(0x33EF4444),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension * 0.52f
                                        )
                                    }
                                }

                                // Inner Rank Label on leftmost displayed column
                                if (displayCol == 0) {
                                    Text(
                                        text = (8 - r).toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = coordColor.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 3.dp, top = 1.dp)
                                    )
                                }

                                // Inner File Label on bottommost displayed row
                                if (displayRow == 7) {
                                    Text(
                                        text = files[c],
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = coordColor.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 3.dp, bottom = 1.dp)
                                    )
                                }

                                if (piece.isNotEmpty()) {
                                    ChessVectorPiece(
                                        pieceCode = piece,
                                        pieceSize = pieceSize,
                                        modifier = Modifier.scale(pieceScale)
                                    )
                                    if (isLegalTarget) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                                            drawCircle(
                                                color = Color(0xCC10B981),
                                                radius = size.minDimension * 0.44f,
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                        }
                                    }
                                } else if (isLegalTarget) {
                                    Box(
                                        modifier = Modifier
                                            .size((boardSide / 8f) * 0.28f)
                                            .clip(CircleShape)
                                            .background(Color(0x9910B981))
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

/**
 * Scrollable horizontal algebraic move log strip (1. e4 e5  2. Nf3 ...).
 */
@Composable
fun ChessMoveHistoryStrip(
    moveHistory: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(moveHistory.size) {
        if (moveHistory.isNotEmpty()) {
            listState.animateScrollToItem((moveHistory.size - 1) / 2)
        }
    }

    val pairedMoves = moveHistory.chunked(2)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chess_move_history_strip"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        if (pairedMoves.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Move log • Make the opening move on the board",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
            }
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(pairedMoves) { idx, pair ->
                    val isLatest = idx == pairedMoves.lastIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLatest) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        },
                        border = if (isLatest) {
                            BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        } else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = pair.getOrNull(0).orEmpty(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val blackMove = pair.getOrNull(1)
                            if (!blackMove.isNullOrBlank()) {
                                Text(
                                    text = blackMove,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist frosted floating bottom control pill combining turn status, check/mate banner,
 * AI difficulty selector, Undo, Flip Board, Tactical Hint, and Reset controls cleanly.
 */
@Composable
fun ChessBottomControlPill(
    turn: String,
    isCheck: Boolean,
    winner: String?,
    isAiMode: Boolean,
    isAiThinking: Boolean,
    aiDifficulty: ChessAiDifficulty,
    canUndo: Boolean,
    statusSummary: String,
    onUndo: () -> Unit,
    onFlipBoard: () -> Unit,
    onHint: () -> Unit,
    onCycleDifficulty: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val frostBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDarkTheme) 0.88f else 0.94f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.72f else 0.82f)
        )
    )

    val borderStroke = BorderStroke(
        width = 0.9.dp,
        brush = Brush.verticalGradient(
            listOf(
                if (isDarkTheme) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        )
    )

    val dotColor = when {
        winner != null -> Color(0xFF10B981)
        isCheck -> Color(0xFFEF4444)
        turn == "White" -> Color(0xFFF8FAFC)
        else -> Color(0xFF1E293B)
    }

    val headlineText = when {
        winner != null -> "$winner Wins"
        isAiThinking -> "EchoBot thinking..."
        isCheck -> "$turn in Check"
        else -> statusSummary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(frostBrush)
            .testTag("chess_bottom_control_pill"),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = borderStroke,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Compact Row: Turn Dot + Concise Status + AI Difficulty Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        text = headlineText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            winner != null -> Color(0xFF10B981)
                            isCheck -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isAiMode) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onCycleDifficulty)
                            .testTag("chess_difficulty_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "AI: ${aiDifficulty.label}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Bottom Action Pills: Undo, Flip, Hint, Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillActionChip(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Undo",
                    enabled = canUndo && !isAiThinking,
                    testTag = "chess_undo_button",
                    onClick = onUndo
                )
                PillActionChip(
                    icon = Icons.Default.SwapVert,
                    label = "Flip",
                    enabled = true,
                    testTag = "chess_flip_button",
                    onClick = onFlipBoard
                )
                PillActionChip(
                    icon = Icons.Default.Lightbulb,
                    label = "Hint",
                    enabled = winner == null && !isAiThinking,
                    testTag = "chess_hint_button",
                    onClick = onHint
                )
                PillActionChip(
                    icon = Icons.Default.Refresh,
                    label = "Reset",
                    enabled = true,
                    isAccent = true,
                    testTag = "chess_reset_button",
                    onClick = onReset
                )
            }
        }
    }
}

@Composable
private fun PillActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isAccent: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isAccent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when {
        !enabled -> Color.Transparent
        isAccent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/**
 * Interactive Pawn Promotion Chooser modal displaying Queen, Rook, Bishop, and Knight.
 */
@Composable
fun PawnPromotionDialog(
    isWhite: Boolean,
    onSelectPiece: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Q" to "Queen",
        "R" to "Rook",
        "B" to "Bishop",
        "N" to "Knight"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag("pawn_promotion_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Promote Pawn",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose a piece to promote your pawn",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    options.forEach { (code, name) ->
                        val renderedCode = if (isWhite) code else code.lowercase()
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectPiece(code) }
                                .testTag("chess_promote_$code"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ChessVectorPiece(pieceCode = renderedCode, pieceSize = 40.dp)
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
