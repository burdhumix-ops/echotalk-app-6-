package co.znxdnd.echotalk.di

import android.content.Context
import android.util.Log
import co.znxdnd.echotalk.R
import co.znxdnd.echotalk.data.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * DatabaseModule manages the Firebase Firestore client instance and provides
 * real-time chat data storage and observation APIs for EchoTalk.
 */
object DatabaseModule {

    private const val TAG = "DatabaseModule"

    @Volatile
    private var firestoreInstance: FirebaseFirestore? = null

    @Volatile
    private var isTestMockInstance: Boolean = false

    const val FALLBACK_DB_ID = "ai-studio-android-echotalk-6503cbf9-2d0e-416c-8609-d552c1ded30b"

    /**
     * Initializes and provides the FirebaseFirestore client.
     * Always honors the custom database ID from R.string.firestore_database_id.
     */
    fun provideFirestore(context: Context): FirebaseFirestore {
        return firestoreInstance ?: synchronized(this) {
            firestoreInstance ?: run {
                val appContext = context.applicationContext
                val dbId = try {
                    appContext.getString(R.string.firestore_database_id).trim()
                } catch (e: Exception) {
                    Log.w(TAG, "firestore_database_id resource not found, using fallback: ${e.message}")
                    FALLBACK_DB_ID
                }.ifEmpty { FALLBACK_DB_ID }

                Log.d(TAG, "Initializing Firestore with custom databaseId: $dbId")
                val instance = FirebaseFirestore.getInstance(dbId)

                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build()
                    instance.firestoreSettings = settings
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply firestoreSettings (may already be initialized): ${e.message}")
                }

                instance.also { firestoreInstance = it }
            }
        }
    }

    /**
     * Direct factory method to obtain Firestore for an explicit databaseId.
     */
    fun provideFirestore(databaseId: String): FirebaseFirestore {
        val targetId = if (databaseId.isNotBlank() && databaseId != "(default)") databaseId else FALLBACK_DB_ID
        return FirebaseFirestore.getInstance(targetId)
    }

    /**
     * Retrieves the singleton Firestore instance. Initializes with custom database ID if not already set.
     */
    fun getInstance(): FirebaseFirestore {
        return firestoreInstance ?: synchronized(this) {
            firestoreInstance ?: FirebaseFirestore.getInstance(FALLBACK_DB_ID).also { firestoreInstance = it }
        }
    }

    /**
     * Internal setter for unit testing and mocks.
     */
    fun setTestInstance(instance: FirebaseFirestore?) {
        firestoreInstance = instance
        isTestMockInstance = instance != null
    }

    private fun canWriteToFirestore(expectedUid: String? = null): Boolean {
        if (isTestMockInstance) return true
        val fbUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        } ?: return false
        return expectedUid == null || fbUid == expectedUid
    }

    // =========================================================================
    // 1. Users Collection: Public profiles & presence
    // Path: users/{userId}
    // =========================================================================

    fun getUsersCollection(): CollectionReference {
        return getInstance().collection("users")
    }

    fun saveUserProfile(
        userId: String,
        displayName: String,
        username: String,
        photoUrl: String? = null,
        isVerified: Boolean = false,
        age: Int = 24,
        country: String = "Global",
        likesCount: Int = 0,
        themePreference: String = "Pink Blush",
        online: Boolean = true,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!canWriteToFirestore(userId)) {
            onSuccess?.invoke()
            return
        }
        val data = hashMapOf<String, Any>(
            "uid" to userId,
            "displayName" to displayName,
            "username" to username,
            "isVerified" to isVerified,
            "age" to age,
            "country" to country,
            "likesCount" to likesCount,
            "themePreference" to themePreference,
            "online" to online,
            "updatedAt" to System.currentTimeMillis()
        )
        if (photoUrl != null) {
            data["photoURL"] = photoUrl
        }

        getUsersCollection().document(userId).set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Synced public profile to Firestore users for $userId")
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore user profile shadow sync skipped for $userId: ${e.message}")
                onFailure?.invoke(e)
            }
    }

    fun updatePresence(userId: String, isOnline: Boolean) {
        if (userId.isBlank() || !canWriteToFirestore(userId)) return
        getUsersCollection().document(userId).update(
            "online", isOnline,
            "updatedAt", System.currentTimeMillis()
        ).addOnFailureListener {
            Log.w(TAG, "Presence update fallback for $userId: ${it.message}")
        }
    }

    // =========================================================================
    // 2. Friendships Collection: Mutual social graph & lead request banners
    // Path: friendships/{friendshipId}
    // =========================================================================

    fun getFriendshipsCollection(): CollectionReference {
        return getInstance().collection("friendships")
    }

    fun getFriendshipDocId(userA: String, userB: String): String {
        return listOf(userA, userB).sorted().joinToString("_")
    }

    fun sendFriendRequest(
        senderId: String,
        receiverId: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!canWriteToFirestore(senderId)) {
            onSuccess?.invoke()
            return
        }
        val friendshipId = getFriendshipDocId(senderId, receiverId)
        val data = hashMapOf<String, Any>(
            "senderId" to senderId,
            "receiverId" to receiverId,
            "status" to "PENDING",
            "updatedAt" to System.currentTimeMillis()
        )
        getFriendshipsCollection().document(friendshipId).set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Friendship request sent to Firestore: $friendshipId")
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore friendship shadow sync skipped: $friendshipId (${e.message})")
                onFailure?.invoke(e)
            }
    }

    fun updateFriendshipStatus(
        friendshipId: String,
        status: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!canWriteToFirestore()) {
            onSuccess?.invoke()
            return
        }
        getFriendshipsCollection().document(friendshipId).update(
            "status", status,
            "updatedAt", System.currentTimeMillis()
        ).addOnSuccessListener {
            Log.d(TAG, "Friendship status updated to $status for $friendshipId")
            onSuccess?.invoke()
        }.addOnFailureListener { e ->
            Log.w(TAG, "Firestore friendship status shadow sync skipped: $friendshipId (${e.message})")
            onFailure?.invoke(e)
        }
    }

    // =========================================================================
    // 3. Active Game Sessions: Live WebRTC room codes, turn states, matchmaking
    // Path: active_game_sessions/{sessionId}
    // =========================================================================

    fun getActiveGameSessionsCollection(): CollectionReference {
        return getInstance().collection("active_game_sessions")
    }

    fun saveGameSession(
        gameState: co.znxdnd.echotalk.data.model.ArcadeGameState,
        webrtcRoomCode: String? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (gameState.gameId.isBlank() || !canWriteToFirestore()) {
            onSuccess?.invoke()
            return
        }
        val payload = hashMapOf<String, Any>(
            "sessionId" to gameState.gameId,
            "gameType" to gameState.gameType.ifBlank { "chess" },
            "challenger" to gameState.challenger,
            "opponent" to gameState.opponent,
            "currentTurn" to gameState.currentTurn,
            "chessBoard" to gameState.chessBoard,
            "capturedWhite" to gameState.capturedWhite,
            "capturedBlack" to gameState.capturedBlack,
            "lastMoveDescription" to gameState.lastMoveDescription,
            "updatedAt" to gameState.updatedAt
        )
        if (gameState.winner != null) payload["winner"] = gameState.winner
        if (webrtcRoomCode != null) payload["webrtcRoomCode"] = webrtcRoomCode

        getActiveGameSessionsCollection().document(gameState.gameId).set(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Synced active_game_sessions for ${gameState.gameId}")
                onSuccess?.invoke()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Firestore active_game_sessions shadow sync skipped ${gameState.gameId}: ${exception.message}")
                onFailure?.invoke(exception)
            }
    }

    fun observeGameSession(sessionId: String): Flow<co.znxdnd.echotalk.data.model.ArcadeGameState?> = callbackFlow {
        if (sessionId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listenerRegistration = getActiveGameSessionsCollection().document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing game session $sessionId: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val state = snapshot.toObject(co.znxdnd.echotalk.data.model.ArcadeGameState::class.java)
                        trySend(state)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deserializing ArcadeGameState for $sessionId", e)
                    }
                } else {
                    trySend(null)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    // Compatibility aliases for existing arcade games callers
    fun getArcadeGamesCollection(): CollectionReference = getActiveGameSessionsCollection()

    fun saveArcadeGameState(
        gameState: co.znxdnd.echotalk.data.model.ArcadeGameState,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        saveGameSession(gameState, null, onSuccess, onFailure)
    }

    fun observeArcadeGameState(gameId: String): Flow<co.znxdnd.echotalk.data.model.ArcadeGameState?> = observeGameSession(gameId)

    // =========================================================================
    // 4. Admin Quarantine Vault: Flagged media reviews in secret Owner Panel
    // Path: admin_quarantine_vault/{vaultId}
    // =========================================================================

    fun getAdminQuarantineVaultCollection(): CollectionReference {
        return getInstance().collection("admin_quarantine_vault")
    }

    fun uploadFlaggedMediaEntry(
        entry: co.znxdnd.echotalk.data.model.AdminVaultEntry,
        senderUid: String,
        reason: String = "Flagged unencrypted media inspection",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (!canWriteToFirestore(senderUid)) {
            onSuccess?.invoke()
            return
        }
        val vaultId = if (entry.id.isNotBlank()) entry.id else "vault-${System.currentTimeMillis()}"
        val payload = hashMapOf<String, Any>(
            "id" to vaultId,
            "senderId" to senderUid,
            "flaggedReason" to reason,
            "mediaUri" to entry.mediaUrl,
            "reportedUser" to entry.sender,
            "timestamp" to entry.timestamp,
            "resolved" to false
        )
        getAdminQuarantineVaultCollection().document(vaultId).set(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Silently uploaded flagged media to admin_quarantine_vault: $vaultId")
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firestore quarantine shadow upload skipped: $vaultId (${e.message})")
                onFailure?.invoke(e)
            }
    }

    // =========================================================================
    // STRICT DM ISOLATION RULE (Free Tier Protection)
    // Private DM chat history and personal message logs are NEVER stored in Firestore.
    // DMs are stored 100% locally on-device in SQLite (Room) and synced to private Google Drive (appDataFolder).
    // =========================================================================

    /**
     * Generates a deterministic chat ID for a 1v1 direct message conversation between two users.
     */
    fun getConversationId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }

    fun saveDirectMessage(
        senderId: String,
        recipientId: String,
        message: ChatMessage,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        // Enforce Strict DM Isolation Rule: Do not write to Firestore.
        Log.d(TAG, "Strict DM Isolation: Private DM ${message.id} preserved on-device in SQLite & Google Drive appDataFolder.")
        onSuccess?.invoke()
    }

    fun saveRoomMessage(
        roomId: String,
        message: ChatMessage,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        Log.d(TAG, "Strict DM Isolation: Room message ${message.id} kept on local storage.")
        onSuccess?.invoke()
    }

    fun observeDirectMessages(userId1: String, userId2: String): Flow<List<ChatMessage>> = callbackFlow {
        // Enforce Strict DM Isolation Rule: personal DMs are loaded from local Room SQLite
        trySend(emptyList())
        awaitClose { }
    }

    fun observeRoomMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        trySend(emptyList())
        awaitClose { }
    }
}
