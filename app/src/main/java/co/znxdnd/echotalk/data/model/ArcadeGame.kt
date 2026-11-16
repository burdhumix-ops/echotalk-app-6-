package co.znxdnd.echotalk.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ArcadeGameState(
    val gameId: String = "",
    val gameType: String = "", // "chess", "mafia"
    val challenger: String = "",
    val opponent: String = "",
    val currentTurn: String = "White", // For chess: White/Black
    val chessBoard: List<String> = emptyList(), // 64 strings representing squares 0..63
    val capturedWhite: List<String> = emptyList(),
    val capturedBlack: List<String> = emptyList(),
    val lastMoveDescription: String = "",
    val winner: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMoveFromRow: Int? = null,
    val lastMoveFromCol: Int? = null,
    val lastMoveToRow: Int? = null,
    val lastMoveToCol: Int? = null
)
