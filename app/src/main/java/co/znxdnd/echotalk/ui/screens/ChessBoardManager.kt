package co.znxdnd.echotalk.ui.screens

import kotlin.math.abs
import kotlin.math.max

/**
 * ChessBoardState: Comprehensive state snapshot represented by and synchronized with a FEN string.
 */
data class ChessBoardState(
    val fen: String,
    val board: Array<Array<String>>,
    val activeColor: String, // "w" or "b"
    val castlingRights: String, // "KQkq", "-", etc.
    val enPassantTarget: String, // "-", "e3", etc.
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false,
    val isStalemate: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChessBoardState
        return fen == other.fen
    }

    override fun hashCode(): Int {
        return fen.hashCode()
    }
}

/**
 * ChessMoveResult: Outcome of an executed move on the chess board.
 */
data class ChessMoveResult(
    val newState: ChessBoardState,
    val capturedPiece: String?,
    val moveNotation: String,
    val isCheck: Boolean,
    val isCheckmate: Boolean,
    val isDraw: Boolean
)

/**
 * ChessBoardManager: Manages Chess game state, turn-based logic, and move validation
 * using Forsyth-Edwards Notation (FEN) strings in the EchoTalk Arcade Hub.
 */
object ChessBoardManager {

    const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    val pieceSymbols = mapOf(
        "K" to "♔", "Q" to "♕", "R" to "♖", "B" to "♗", "N" to "♘", "P" to "♙",
        "k" to "♚", "q" to "♛", "r" to "♜", "b" to "♝", "n" to "♞", "p" to "♟"
    )

    private val cols = listOf("a", "b", "c", "d", "e", "f", "g", "h")

    /**
     * Parses a standard 6-field FEN string into a ChessBoardState.
     */
    fun parseFen(fen: String = STARTING_FEN): ChessBoardState {
        val parts = fen.trim().split(Regex("\\s+"))
        val placement = parts.getOrNull(0) ?: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
        val activeColor = parts.getOrNull(1) ?: "w"
        val castling = parts.getOrNull(2) ?: "KQkq"
        val enPassant = parts.getOrNull(3) ?: "-"
        val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1

        val board = Array(8) { Array(8) { "" } }
        val ranks = placement.split("/")

        for (r in 0 until minOf(8, ranks.size)) {
            val rankStr = ranks[r]
            var c = 0
            for (char in rankStr) {
                if (c >= 8) break
                if (char.isDigit()) {
                    val emptySquares = char.digitToInt()
                    c += emptySquares
                } else {
                    board[r][c] = char.toString()
                    c++
                }
            }
        }

        val inCheck = isKingInCheck(board, activeColor == "w")
        val legalMovesExist = hasAnyLegalMoves(board, activeColor, castling, enPassant)

        return ChessBoardState(
            fen = fen,
            board = board,
            activeColor = activeColor,
            castlingRights = castling,
            enPassantTarget = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove,
            isCheck = inCheck,
            isCheckmate = inCheck && !legalMovesExist,
            isStalemate = !inCheck && !legalMovesExist
        )
    }

    /**
     * Converts a 2D board array and game state variables into a standard FEN string.
     */
    fun toFen(
        board: Array<Array<String>>,
        activeColor: String,
        castlingRights: String = "KQkq",
        enPassantTarget: String = "-",
        halfmoveClock: Int = 0,
        fullmoveNumber: Int = 1
    ): String {
        val sb = StringBuilder()

        // 1. Piece placement
        for (r in 0..7) {
            var emptyCount = 0
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece.isEmpty()) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r < 7) sb.append("/")
        }

        // 2. Active color
        sb.append(" ").append(activeColor)

        // 3. Castling availability
        sb.append(" ").append(if (castlingRights.isBlank()) "-" else castlingRights)

        // 4. En passant target square
        sb.append(" ").append(if (enPassantTarget.isBlank()) "-" else enPassantTarget)

        // 5. Halfmove clock
        sb.append(" ").append(halfmoveClock)

        // 6. Fullmove number
        sb.append(" ").append(fullmoveNumber)

        return sb.toString()
    }

    /**
     * Checks if a move is legal according to chess rules and current FEN state.
     */
    fun isValidMove(
        state: ChessBoardState,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int
    ): Boolean {
        if (fromR !in 0..7 || fromC !in 0..7 || toR !in 0..7 || toC !in 0..7) return false
        if (fromR == toR && fromC == toC) return false

        val board = state.board
        val piece = board[fromR][fromC]
        if (piece.isEmpty()) return false

        val isWhite = piece.first().isUpperCase()
        if (state.activeColor == "w" && !isWhite) return false
        if (state.activeColor == "b" && isWhite) return false

        val dest = board[toR][toC]
        if (dest.isNotEmpty()) {
            val destIsWhite = dest.first().isUpperCase()
            if (isWhite == destIsWhite) return false // Cannot capture friendly piece
        }

        val dr = toR - fromR
        val dc = toC - fromC
        val absDr = abs(dr)
        val absDc = abs(dc)

        // Geometric move validation
        val isGeometricallyValid = when (piece.uppercase()) {
            "P" -> isValidPawnMove(board, fromR, fromC, toR, toC, isWhite, state.enPassantTarget)
            "N" -> (absDr == 2 && absDc == 1) || (absDr == 1 && absDc == 2)
            "B" -> absDr == absDc && isPathClear(board, fromR, fromC, toR, toC)
            "R" -> (absDr == 0 || absDc == 0) && isPathClear(board, fromR, fromC, toR, toC)
            "Q" -> ((absDr == absDc) || (absDr == 0 || absDc == 0)) && isPathClear(board, fromR, fromC, toR, toC)
            "K" -> isValidKingMove(board, fromR, fromC, toR, toC, isWhite, state.castlingRights)
            else -> false
        }

        if (!isGeometricallyValid) return false

        // Ensure king does not end up in check after this move
        val clonedBoard = cloneBoard(board)
        applyMoveToBoard(clonedBoard, fromR, fromC, toR, toC, isWhite, state.enPassantTarget)
        if (isKingInCheck(clonedBoard, isWhite)) {
            return false
        }

        return true
    }

    private fun isValidPawnMove(
        board: Array<Array<String>>,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int,
        isWhite: Boolean,
        enPassantTarget: String
    ): Boolean {
        val dr = toR - fromR
        val dc = toC - fromC
        val absDc = abs(dc)
        val dest = board[toR][toC]

        return if (isWhite) {
            // Forward 1
            if (dc == 0 && dr == -1 && dest.isEmpty()) true
            // Forward 2 from initial rank 6
            else if (dc == 0 && dr == -2 && fromR == 6 && dest.isEmpty() && board[5][fromC].isEmpty()) true
            // Normal diagonal capture
            else if (absDc == 1 && dr == -1 && dest.isNotEmpty() && !dest.first().isUpperCase()) true
            // En passant diagonal capture
            else if (absDc == 1 && dr == -1 && dest.isEmpty() && enPassantTarget != "-") {
                val targetSquare = "${cols[toC]}${8 - toR}"
                targetSquare == enPassantTarget
            } else false
        } else {
            // Black forward 1
            if (dc == 0 && dr == 1 && dest.isEmpty()) true
            // Black forward 2 from initial rank 1
            else if (dc == 0 && dr == 2 && fromR == 1 && dest.isEmpty() && board[2][fromC].isEmpty()) true
            // Black normal diagonal capture
            else if (absDc == 1 && dr == 1 && dest.isNotEmpty() && dest.first().isUpperCase()) true
            // Black en passant diagonal capture
            else if (absDc == 1 && dr == 1 && dest.isEmpty() && enPassantTarget != "-") {
                val targetSquare = "${cols[toC]}${8 - toR}"
                targetSquare == enPassantTarget
            } else false
        }
    }

    private fun isValidKingMove(
        board: Array<Array<String>>,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int,
        isWhite: Boolean,
        castlingRights: String
    ): Boolean {
        val dr = toR - fromR
        val dc = toC - fromC
        val absDr = abs(dr)
        val absDc = abs(dc)

        // Standard 1 square king step
        if (max(absDr, absDc) == 1) return true

        // Castling
        if (isWhite && fromR == 7 && fromC == 4 && toR == 7) {
            // Kingside castling
            if (toC == 6 && castlingRights.contains("K") &&
                board[7][5].isEmpty() && board[7][6].isEmpty() && board[7][7] == "R" &&
                !isKingInCheck(board, true)
            ) {
                return true
            }
            // Queenside castling
            if (toC == 2 && castlingRights.contains("Q") &&
                board[7][1].isEmpty() && board[7][2].isEmpty() && board[7][3].isEmpty() && board[7][0] == "R" &&
                !isKingInCheck(board, true)
            ) {
                return true
            }
        } else if (!isWhite && fromR == 0 && fromC == 4 && toR == 0) {
            // Black kingside castling
            if (toC == 6 && castlingRights.contains("k") &&
                board[0][5].isEmpty() && board[0][6].isEmpty() && board[0][7] == "r" &&
                !isKingInCheck(board, false)
            ) {
                return true
            }
            // Black queenside castling
            if (toC == 2 && castlingRights.contains("q") &&
                board[0][1].isEmpty() && board[0][2].isEmpty() && board[0][3].isEmpty() && board[0][0] == "r" &&
                !isKingInCheck(board, false)
            ) {
                return true
            }
        }

        return false
    }

    fun isPathClear(board: Array<Array<String>>, fromR: Int, fromC: Int, toR: Int, toC: Int): Boolean {
        val dr = (toR - fromR).coerceIn(-1, 1)
        val dc = (toC - fromC).coerceIn(-1, 1)

        var curR = fromR + dr
        var curC = fromC + dc
        while (curR != toR || curC != toC) {
            if (board[curR][curC].isNotEmpty()) return false
            curR += dr
            curC += dc
        }
        return true
    }

    /**
     * Checks if moving from (fromR, fromC) to (toR, toC) is a legal pawn promotion move.
     */
    fun needsPawnPromotion(
        state: ChessBoardState,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int
    ): Boolean {
        if (!isValidMove(state, fromR, fromC, toR, toC)) return false
        val piece = state.board[fromR][fromC]
        return (piece == "P" && toR == 0) || (piece == "p" && toR == 7)
    }

    /**
     * Computes total remaining standard piece material points (P=1, N=3, B=3, R=5, Q=9) for (White, Black).
     */
    fun computeMaterialScore(board: Array<Array<String>>): Pair<Int, Int> {
        var whiteScore = 0
        var blackScore = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p.isEmpty()) continue
                val pts = when (p.uppercase()) {
                    "P" -> 1
                    "N", "B" -> 3
                    "R" -> 5
                    "Q" -> 9
                    else -> 0
                }
                if (p.first().isUpperCase()) whiteScore += pts else blackScore += pts
            }
        }
        return Pair(whiteScore, blackScore)
    }

    /**
     * Converts an ISO 3166-1 alpha-2 country code (e.g., "US", "JP", "CA") to a Unicode flag emoji.
     */
    fun countryCodeToFlagEmoji(countryCode: String): String {
        val clean = countryCode.trim().uppercase()
        if (clean.length != 2 || !clean[0].isLetter() || !clean[1].isLetter()) return "🌍"
        val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    /**
     * Executes a move on the board state, returning a rich ChessMoveResult with updated FEN.
     */
    fun executeMove(
        state: ChessBoardState,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int,
        promotionPiece: String = "Q"
    ): ChessMoveResult? {
        if (!isValidMove(state, fromR, fromC, toR, toC)) return null

        val board = cloneBoard(state.board)
        val piece = board[fromR][fromC]
        val dest = board[toR][toC]
        val isWhite = piece.first().isUpperCase()
        var capturedPiece: String? = if (dest.isNotEmpty()) dest else null

        // En passant capture execution
        if (piece.equals("P", ignoreCase = true) && abs(toC - fromC) == 1 && dest.isEmpty()) {
            val targetSquare = "${cols[toC]}${8 - toR}"
            if (targetSquare == state.enPassantTarget) {
                val capR = if (isWhite) toR + 1 else toR - 1
                capturedPiece = board[capR][toC]
                board[capR][toC] = ""
            }
        }

        // Castling rook repositioning
        var isCastlingMove = false
        if (piece == "K" && fromR == 7 && fromC == 4) {
            if (toR == 7 && toC == 6) {
                board[7][5] = "R"
                board[7][7] = ""
                isCastlingMove = true
            } else if (toR == 7 && toC == 2) {
                board[7][3] = "R"
                board[7][0] = ""
                isCastlingMove = true
            }
        } else if (piece == "k" && fromR == 0 && fromC == 4) {
            if (toR == 0 && toC == 6) {
                board[0][5] = "r"
                board[0][7] = ""
                isCastlingMove = true
            } else if (toR == 0 && toC == 2) {
                board[0][3] = "r"
                board[0][0] = ""
                isCastlingMove = true
            }
        }

        // Move piece
        board[toR][toC] = piece
        board[fromR][fromC] = ""

        // Pawn promotion (supports Q, R, B, N; defaults to Q)
        var isPromotion = false
        val normalizedPromo = when (promotionPiece.uppercase()) {
            "R", "B", "N", "Q" -> promotionPiece.uppercase()
            else -> "Q"
        }
        if (piece == "P" && toR == 0) {
            board[toR][toC] = normalizedPromo
            isPromotion = true
        } else if (piece == "p" && toR == 7) {
            board[toR][toC] = normalizedPromo.lowercase()
            isPromotion = true
        }

        // Update castling rights
        var newCastling = state.castlingRights
        if (piece == "K") {
            newCastling = newCastling.replace("K", "").replace("Q", "")
        } else if (piece == "k") {
            newCastling = newCastling.replace("k", "").replace("q", "")
        } else if (piece == "R") {
            if (fromR == 7 && fromC == 7) newCastling = newCastling.replace("K", "")
            if (fromR == 7 && fromC == 0) newCastling = newCastling.replace("Q", "")
        } else if (piece == "r") {
            if (fromR == 0 && fromC == 7) newCastling = newCastling.replace("k", "")
            if (fromR == 0 && fromC == 0) newCastling = newCastling.replace("q", "")
        }
        if (newCastling.isEmpty()) newCastling = "-"

        // Update en passant square (if pawn advanced 2 squares)
        val newEnPassant = if (piece.equals("P", ignoreCase = true) && abs(toR - fromR) == 2) {
            val epRow = if (isWhite) 8 - (fromR - 1) else 8 - (fromR + 1)
            "${cols[fromC]}$epRow"
        } else {
            "-"
        }

        // Halfmove clock (resets on pawn move or capture)
        val newHalfmove = if (piece.equals("P", ignoreCase = true) || capturedPiece != null) {
            0
        } else {
            state.halfmoveClock + 1
        }

        // Fullmove number increments after Black moves
        val nextColor = if (state.activeColor == "w") "b" else "w"
        val newFullmove = if (state.activeColor == "b") state.fullmoveNumber + 1 else state.fullmoveNumber

        val newFen = toFen(
            board = board,
            activeColor = nextColor,
            castlingRights = newCastling,
            enPassantTarget = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove
        )

        val nextInCheck = isKingInCheck(board, nextColor == "w")
        val nextHasMoves = hasAnyLegalMoves(board, nextColor, newCastling, newEnPassant)
        val isCheckmate = nextInCheck && !nextHasMoves
        val isStalemate = !nextInCheck && !nextHasMoves
        val isDraw = isStalemate || newHalfmove >= 100 // 50-move rule

        // Notation builder
        val pieceLetter = when (piece.uppercase()) {
            "P" -> ""
            else -> piece.uppercase()
        }
        val fromCol = cols[fromC]
        val toNotation = "${cols[toC]}${8 - toR}"
        val captureSymbol = if (capturedPiece != null) {
            if (piece.equals("P", ignoreCase = true)) "${fromCol}x" else "x"
        } else {
            ""
        }
        val checkSymbol = if (isCheckmate) "#" else if (nextInCheck) "+" else ""
        val promoSymbol = if (isPromotion) "=$normalizedPromo" else ""

        val moveNotation = when {
            isCastlingMove && toC == 6 -> "O-O$checkSymbol"
            isCastlingMove && toC == 2 -> "O-O-O$checkSymbol"
            else -> "$pieceLetter$captureSymbol$toNotation$promoSymbol$checkSymbol"
        }

        val newState = ChessBoardState(
            fen = newFen,
            board = board,
            activeColor = nextColor,
            castlingRights = newCastling,
            enPassantTarget = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove,
            isCheck = nextInCheck,
            isCheckmate = isCheckmate,
            isStalemate = isStalemate
        )

        return ChessMoveResult(
            newState = newState,
            capturedPiece = capturedPiece,
            moveNotation = moveNotation,
            isCheck = nextInCheck,
            isCheckmate = isCheckmate,
            isDraw = isDraw
        )
    }

    private fun applyMoveToBoard(
        board: Array<Array<String>>,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int,
        isWhite: Boolean,
        enPassantTarget: String
    ) {
        val piece = board[fromR][fromC]
        if (piece.equals("P", ignoreCase = true) && abs(toC - fromC) == 1 && board[toR][toC].isEmpty()) {
            val targetSquare = "${cols[toC]}${8 - toR}"
            if (targetSquare == enPassantTarget) {
                val capR = if (isWhite) toR + 1 else toR - 1
                board[capR][toC] = ""
            }
        }
        board[toR][toC] = piece
        board[fromR][fromC] = ""
    }

    fun isKingInCheck(board: Array<Array<String>>, isWhite: Boolean): Boolean {
        val kingChar = if (isWhite) "K" else "k"
        var kingR = -1
        var kingC = -1
        for (r in 0..7) {
            for (c in 0..7) {
                if (board[r][c] == kingChar) {
                    kingR = r
                    kingC = c
                    break
                }
            }
            if (kingR != -1) break
        }
        if (kingR == -1) return false // No king found (e.g. captured)

        val oppColor = if (isWhite) "b" else "w"
        val dummyState = ChessBoardState(
            fen = "",
            board = board,
            activeColor = oppColor,
            castlingRights = "-",
            enPassantTarget = "-",
            halfmoveClock = 0,
            fullmoveNumber = 1
        )

        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece.isNotEmpty() && (piece.first().isUpperCase() != isWhite)) {
                    val dr = kingR - r
                    val dc = kingC - c
                    val absDr = abs(dr)
                    val absDc = abs(dc)
                    val canAttack = when (piece.uppercase()) {
                        "P" -> if (isWhite) (dr == 1 && absDc == 1) else (dr == -1 && absDc == 1)
                        "N" -> (absDr == 2 && absDc == 1) || (absDr == 1 && absDc == 2)
                        "B" -> absDr == absDc && isPathClear(board, r, c, kingR, kingC)
                        "R" -> (absDr == 0 || absDc == 0) && isPathClear(board, r, c, kingR, kingC)
                        "Q" -> ((absDr == absDc) || (absDr == 0 || absDc == 0)) && isPathClear(board, r, c, kingR, kingC)
                        "K" -> max(absDr, absDc) == 1
                        else -> false
                    }
                    if (canAttack) return true
                }
            }
        }
        return false
    }

    private fun hasAnyLegalMoves(
        board: Array<Array<String>>,
        activeColor: String,
        castling: String,
        enPassant: String
    ): Boolean {
        val isWhite = activeColor == "w"
        val testState = ChessBoardState(
            fen = "",
            board = board,
            activeColor = activeColor,
            castlingRights = castling,
            enPassantTarget = enPassant,
            halfmoveClock = 0,
            fullmoveNumber = 1
        )

        for (fromR in 0..7) {
            for (fromC in 0..7) {
                val piece = board[fromR][fromC]
                if (piece.isNotEmpty() && (piece.first().isUpperCase() == isWhite)) {
                    for (toR in 0..7) {
                        for (toC in 0..7) {
                            if (isValidMove(testState, fromR, fromC, toR, toC)) {
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    fun getAllLegalMoves(state: ChessBoardState): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val moves = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        val isWhite = state.activeColor == "w"
        for (fromR in 0..7) {
            for (fromC in 0..7) {
                val piece = state.board[fromR][fromC]
                if (piece.isNotEmpty() && (piece.first().isUpperCase() == isWhite)) {
                    for (toR in 0..7) {
                        for (toC in 0..7) {
                            if (isValidMove(state, fromR, fromC, toR, toC)) {
                                moves.add(Pair(Pair(fromR, fromC), Pair(toR, toC)))
                            }
                        }
                    }
                }
            }
        }
        return moves
    }

    /**
     * AI Search Result: contains the best calculated move alongside mobile telemetry metrics
     * (nodes evaluated, time elapsed, depth reached, and position evaluation).
     */
    data class AiSearchResult(
        val bestMove: Pair<Pair<Int, Int>, Pair<Int, Int>>?,
        val evaluation: Int,
        val nodesEvaluated: Int,
        val durationMs: Long,
        val depthReached: Int,
        val isCheckmate: Boolean = false
    )

    private fun getPieceBaseValue(pieceType: String): Int {
        return when (pieceType.uppercase()) {
            "P" -> 100
            "N" -> 320
            "B" -> 330
            "R" -> 500
            "Q" -> 900
            "K" -> 20000
            else -> 0
        }
    }

    // Complete Piece-Square Tables (PST) for positional chess mastery
    private val pawnPst = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(5, 5, 10, 25, 25, 10, 5, 5),
        intArrayOf(0, 0, 0, 20, 20, 0, 0, 0),
        intArrayOf(5, -5, -10, 0, 0, -10, -5, 5),
        intArrayOf(5, 10, 10, -20, -20, 10, 10, 5),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    private val knightPst = arrayOf(
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50),
        intArrayOf(-40, -20, 0, 0, 0, 0, -20, -40),
        intArrayOf(-30, 0, 10, 15, 15, 10, 0, -30),
        intArrayOf(-30, 5, 15, 20, 20, 15, 5, -30),
        intArrayOf(-30, 0, 15, 20, 20, 15, 0, -30),
        intArrayOf(-30, 5, 10, 15, 15, 10, 5, -30),
        intArrayOf(-40, -20, 0, 5, 5, 0, -20, -40),
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50)
    )

    private val bishopPst = arrayOf(
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20),
        intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        intArrayOf(-10, 0, 5, 10, 10, 5, 0, -10),
        intArrayOf(-10, 5, 5, 10, 10, 5, 5, -10),
        intArrayOf(-10, 0, 10, 10, 10, 10, 0, -10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10, -10),
        intArrayOf(-10, 5, 0, 0, 0, 0, 5, -10),
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20)
    )

    private val rookPst = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(5, 10, 10, 10, 10, 10, 10, 5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(0, 0, 0, 5, 5, 0, 0, 0)
    )

    private val queenPst = arrayOf(
        intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20),
        intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        intArrayOf(-10, 0, 5, 5, 5, 5, 0, -10),
        intArrayOf(-5, 0, 5, 5, 5, 5, 0, -5),
        intArrayOf(0, 0, 5, 5, 5, 5, 0, -5),
        intArrayOf(-10, 5, 5, 5, 5, 5, 0, -10),
        intArrayOf(-10, 0, 5, 0, 0, 0, 0, -10),
        intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20)
    )

    private val kingPstOpening = arrayOf(
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-20, -30, -30, -40, -40, -30, -30, -20),
        intArrayOf(-10, -20, -20, -20, -20, -20, -20, -10),
        intArrayOf(20, 20, 0, 0, 0, 0, 20, 20),
        intArrayOf(20, 30, 10, 0, 0, 10, 30, 20)
    )

    private val kingPstEndgame = arrayOf(
        intArrayOf(-50, -40, -30, -20, -20, -30, -40, -50),
        intArrayOf(-30, -20, -10, 0, 0, -10, -20, -30),
        intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
        intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
        intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
        intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
        intArrayOf(-30, -30, 0, 0, 0, 0, -30, -30),
        intArrayOf(-50, -30, -30, -30, -30, -30, -30, -50)
    )

    /**
     * Fast in-place board evaluation for zero-allocation mobile loops.
     */
    fun evaluateBoard(state: ChessBoardState): Int {
        return evaluateBoardRaw(state.board)
    }

    private fun evaluateBoardRaw(board: Array<Array<String>>): Int {
        var score = 0
        var queenCount = 0
        var majorPieceCount = 0

        // Pass 1: Material & piece counts to determine game phase (opening vs endgame)
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p.isNotEmpty()) {
                    val pType = p.uppercase()
                    if (pType == "Q") queenCount++
                    if (pType == "R" || pType == "B" || pType == "N") majorPieceCount++
                }
            }
        }
        val isEndgame = (queenCount == 0 || (queenCount <= 2 && majorPieceCount <= 2))

        // Pass 2: Position & tactical scoring
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p.isNotEmpty()) {
                    val isWhite = p.first().isUpperCase()
                    val pieceType = p.uppercase()
                    var posBonus = 0
                    val tableR = if (isWhite) r else 7 - r
                    when (pieceType) {
                        "P" -> posBonus = pawnPst[tableR][c]
                        "N" -> posBonus = knightPst[tableR][c]
                        "B" -> posBonus = bishopPst[tableR][c]
                        "R" -> posBonus = rookPst[tableR][c]
                        "Q" -> posBonus = queenPst[tableR][c]
                        "K" -> posBonus = if (isEndgame) kingPstEndgame[tableR][c] else kingPstOpening[tableR][c]
                    }
                    val total = getPieceBaseValue(pieceType) + posBonus
                    if (isWhite) score += total else score -= total
                }
            }
        }
        return score
    }

    /**
     * Computes a 64-bit board signature for transposition table caching.
     */
    private fun computeBoardHash(board: Array<Array<String>>, isWhiteTurn: Boolean): Long {
        var h = if (isWhiteTurn) 0x9E3779B97F4A7C15uL.toLong() else 0L
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p.isNotEmpty()) {
                    val code = (r shl 6) or (c shl 3) or p.first().code
                    h = h xor (code.toLong() * 0x517CC1B727220A95L)
                    h = java.lang.Long.rotateLeft(h, 5)
                }
            }
        }
        return h
    }

    /**
     * Move score for MVV-LVA (Most Valuable Victim - Least Valuable Attacker) and positional sorting.
     * Crucial for alpha-beta pruning: examining the best moves first prunes >85% of subtrees.
     */
    private fun scoreMove(
        board: Array<Array<String>>,
        fromR: Int,
        fromC: Int,
        toR: Int,
        toC: Int
    ): Int {
        val attacker = board[fromR][fromC]
        val victim = board[toR][toC]
        var score = 0

        // MVV-LVA capture scoring: high victim value, low attacker value
        if (victim.isNotEmpty()) {
            val victimValue = getPieceBaseValue(victim)
            val attackerValue = getPieceBaseValue(attacker)
            score += (10 * victimValue) - attackerValue + 100000
        }

        // Pawn promotion incentive
        if (attacker.equals("P", ignoreCase = true) && (toR == 0 || toR == 7)) {
            score += 90000
        }

        // Positional gain using Piece-Square Tables
        val isWhite = attacker.first().isUpperCase()
        val attackerType = attacker.uppercase()
        val srcTableR = if (isWhite) fromR else 7 - fromR
        val dstTableR = if (isWhite) toR else 7 - toR
        val pstDelta = when (attackerType) {
            "P" -> pawnPst[dstTableR][toC] - pawnPst[srcTableR][fromC]
            "N" -> knightPst[dstTableR][toC] - knightPst[srcTableR][fromC]
            "B" -> bishopPst[dstTableR][toC] - bishopPst[srcTableR][fromC]
            "R" -> rookPst[dstTableR][toC] - rookPst[srcTableR][fromC]
            "Q" -> queenPst[dstTableR][toC] - queenPst[srcTableR][fromC]
            else -> 0
        }
        score += pstDelta

        return score
    }

    /**
     * Search context tracking mobile safety constraints (time and node limits).
     */
    private class MobileSearchContext(
        val deadlineMs: Long,
        val maxNodes: Int
    ) {
        var nodesCount = 0
        val transpositionTable = HashMap<Long, Pair<Int, Int>>() // hash -> (depth, eval)

        fun isBudgetExceeded(): Boolean {
            if (nodesCount >= maxNodes) return true
            if (nodesCount % 256 == 0 && System.currentTimeMillis() >= deadlineMs) return true
            return false
        }
    }

    /**
     * Recursive Minimax search with Alpha-Beta pruning, MVV-LVA move ordering,
     * transposition caching, and mobile constraint safeguards.
     */
    fun minimax(
        state: ChessBoardState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean
    ): Int {
        val context = MobileSearchContext(
            deadlineMs = System.currentTimeMillis() + 800L,
            maxNodes = 25000
        )
        return minimaxInternal(state, depth, alpha, beta, isMaximizing, context)
    }

    private fun minimaxInternal(
        state: ChessBoardState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        context: MobileSearchContext
    ): Int {
        context.nodesCount++

        if (depth == 0 || context.isBudgetExceeded()) {
            return evaluateBoardRaw(state.board)
        }

        // Transposition table probe
        val boardHash = computeBoardHash(state.board, isMaximizing)
        val cached = context.transpositionTable[boardHash]
        if (cached != null && cached.first >= depth) {
            return cached.second
        }

        val legalMoves = getAllLegalMoves(state)
        if (legalMoves.isEmpty()) {
            val inCheck = isKingInCheck(state.board, isMaximizing)
            return if (inCheck) {
                if (isMaximizing) -100000 - depth else 100000 + depth
            } else {
                0 // Stalemate
            }
        }

        // Sort moves with MVV-LVA for maximum alpha-beta cutoff efficiency
        val sortedMoves = legalMoves.sortedByDescending { (from, to) ->
            scoreMove(state.board, from.first, from.second, to.first, to.second)
        }

        var curAlpha = alpha
        var curBeta = beta

        val evalResult: Int
        if (isMaximizing) {
            var maxEval = -500000
            for (move in sortedMoves) {
                val res = executeMove(state, move.first.first, move.first.second, move.second.first, move.second.second) ?: continue
                val eval = minimaxInternal(res.newState, depth - 1, curAlpha, curBeta, false, context)
                maxEval = maxOf(maxEval, eval)
                curAlpha = maxOf(curAlpha, eval)
                if (curBeta <= curAlpha) break // Beta cutoff
            }
            evalResult = if (maxEval == -500000) evaluateBoardRaw(state.board) else maxEval
        } else {
            var minEval = 500000
            for (move in sortedMoves) {
                val res = executeMove(state, move.first.first, move.first.second, move.second.first, move.second.second) ?: continue
                val eval = minimaxInternal(res.newState, depth - 1, curAlpha, curBeta, true, context)
                minEval = minOf(minEval, eval)
                curBeta = minOf(curBeta, eval)
                if (curBeta <= curAlpha) break // Alpha cutoff
            }
            evalResult = if (minEval == 500000) evaluateBoardRaw(state.board) else minEval
        }

        // Cache evaluated result in transposition table
        if (context.transpositionTable.size < 8000) {
            context.transpositionTable[boardHash] = Pair(depth, evalResult)
        }

        return evalResult
    }

    /**
     * Local offline Minimax with Alpha-Beta pruning AI engine optimized for mobile device constraints.
     * Returns rich telemetry (nodes evaluated, time elapsed, position score).
     */
    fun findBestMoveOptimized(
        state: ChessBoardState,
        depth: Int = 3,
        timeBudgetMs: Long = 750L,
        maxNodesBudget: Int = 25000
    ): AiSearchResult {
        val startTime = System.currentTimeMillis()
        val legalMoves = getAllLegalMoves(state)
        if (legalMoves.isEmpty()) {
            return AiSearchResult(
                bestMove = null,
                evaluation = 0,
                nodesEvaluated = 0,
                durationMs = 0L,
                depthReached = depth
            )
        }

        val isWhite = state.activeColor == "w"
        val context = MobileSearchContext(
            deadlineMs = startTime + timeBudgetMs,
            maxNodes = maxNodesBudget
        )

        // MVV-LVA move ordering at root
        val sortedMoves = legalMoves.sortedByDescending { (from, to) ->
            scoreMove(state.board, from.first, from.second, to.first, to.second)
        }

        var bestMove: Pair<Pair<Int, Int>, Pair<Int, Int>>? = null
        var bestEval = if (isWhite) -500000 else 500000
        var alpha = -500000
        var beta = 500000

        for (move in sortedMoves) {
            val res = executeMove(state, move.first.first, move.first.second, move.second.first, move.second.second) ?: continue
            val eval = minimaxInternal(res.newState, depth - 1, alpha, beta, !isWhite, context)

            if (isWhite) {
                if (eval > bestEval) {
                    bestEval = eval
                    bestMove = move
                }
                alpha = maxOf(alpha, eval)
            } else {
                if (eval < bestEval) {
                    bestEval = eval
                    bestMove = move
                }
                beta = minOf(beta, eval)
            }

            if (beta <= alpha) break // Alpha-Beta root cutoff
            if (context.isBudgetExceeded()) break // Mobile resource safeguard
        }

        val chosenMove = bestMove ?: legalMoves.random()
        val elapsed = System.currentTimeMillis() - startTime

        return AiSearchResult(
            bestMove = chosenMove,
            evaluation = bestEval,
            nodesEvaluated = context.nodesCount,
            durationMs = elapsed,
            depthReached = depth
        )
    }

    /**
     * Asynchronous background search on Dispatchers.Default.
     * Prevents any UI thread stalls or frame drops on mobile devices.
     */
    suspend fun findBestMoveAsync(
        state: ChessBoardState,
        depth: Int = 3,
        timeBudgetMs: Long = 750L
    ): AiSearchResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        findBestMoveOptimized(state, depth, timeBudgetMs)
    }

    /**
     * Synchronous entry point maintaining 100% backward compatibility with existing tests and callers.
     */
    fun findBestMove(state: ChessBoardState, depth: Int = 3): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        return findBestMoveOptimized(state, depth).bestMove
    }

    private fun cloneBoard(board: Array<Array<String>>): Array<Array<String>> {
        return Array(8) { r -> Array(8) { c -> board[r][c] } }
    }
}
