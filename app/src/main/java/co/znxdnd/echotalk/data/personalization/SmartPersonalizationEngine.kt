package co.znxdnd.echotalk.data.personalization

import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.ChatRoom
import co.znxdnd.echotalk.data.model.User
import kotlin.math.min

/**
 * SmartPersonalizationEngine:
 * Pure, deterministic, local-first scoring algorithms for ordering rooms, Crew discovery,
 * and recent conversations without hiding content, inventing interests, or ranking by popularity/appearance.
 */
object SmartPersonalizationEngine {

    private const val SCORE_BUCKET_SIZE = 5

    /**
     * Orders rooms by recent visits, visit frequency, and optional user-selected interests.
     * - Never filters out or hides any room.
     * - Stabilizes ordering via score bucketing and original-index tie-breaking.
     * - Returns rooms unchanged when personalization is disabled or no signals exist.
     */
    fun rankRooms(
        rooms: List<ChatRoom>,
        config: PersonalizationConfig
    ): List<ChatRoom> {
        if (rooms.size <= 1 || !config.personalizationEnabled) return rooms
        val hasHistory = config.recentRoomIds.isNotEmpty() ||
            config.roomVisitCounts.isNotEmpty() ||
            config.selectedInterests.isNotEmpty()
        if (!hasHistory) return rooms

        val interests = config.selectedInterests
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .toList()

        return rooms.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<ChatRoom>> { (_, room) ->
                    computeRoomScore(room, config, interests) / SCORE_BUCKET_SIZE
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun computeRoomScore(
        room: ChatRoom,
        config: PersonalizationConfig,
        normalizedInterests: List<String>
    ): Int {
        var score = 0

        val recentIndex = config.recentRoomIds.indexOf(room.id)
        if (recentIndex >= 0) {
            score += maxOf(1, 10 - recentIndex) * 10
        }

        val visits = (config.roomVisitCounts[room.id] ?: 0).coerceAtLeast(0)
        if (visits > 0) {
            score += min(visits, 20) * 3
        }

        if (normalizedInterests.isNotEmpty()) {
            val haystack = "${room.name} ${room.subtitle} ${room.description} ${room.badge}".lowercase()
            var interestBonus = 0
            for (interest in normalizedInterests) {
                if (haystack.contains(interest)) {
                    interestBonus += 15
                    if (interestBonus >= 30) break
                }
            }
            score += interestBonus
        }

        return score
    }

    /**
     * Orders Crew users strictly preserving online-before-offline ordering.
     * Uses only permitted, available profile attributes (connection status, recent conversations,
     * shared country, and optional explicit user-selected interests).
     * Never uses likesCount, photos, or perceived attractiveness.
     */
    fun rankCrewUsers(
        users: List<User>,
        currentUser: User,
        config: PersonalizationConfig
    ): List<User> {
        if (users.size <= 1) return users

        if (!config.personalizationEnabled) {
            return users.withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<User>> { it.value.online }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        val interests = config.selectedInterests
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .toList()

        return users.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<User>> { it.value.online }
                    .thenByDescending { computeCrewScore(it.value, currentUser, config, interests) / SCORE_BUCKET_SIZE }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun computeCrewScore(
        user: User,
        currentUser: User,
        config: PersonalizationConfig,
        normalizedInterests: List<String>
    ): Int {
        var score = 0

        val recentIndex = config.recentConversationIds.indexOf(user.id)
        if (recentIndex >= 0) {
            score += maxOf(1, 10 - recentIndex) * 8
        }

        if (user.isFriend || user.friendStatus == "accepted") {
            score += 10
        }

        if (currentUser.country.isNotBlank() &&
            user.country.equals(currentUser.country, ignoreCase = true)
        ) {
            score += 8
        }

        if (normalizedInterests.isNotEmpty() && user.bio.isNotBlank()) {
            val bioLower = user.bio.lowercase()
            var interestBonus = 0
            for (interest in normalizedInterests) {
                if (bioLower.contains(interest)) {
                    interestBonus += 10
                    if (interestBonus >= 20) break
                }
            }
            score += interestBonus
        }

        return score
    }

    /**
     * Orders DM partners keeping pinned chats at the top, prioritizing recently opened conversations
     * when personalization is enabled, and falling back to thread activity / original order.
     */
    fun rankConversations(
        partners: List<User>,
        pinnedIds: Set<String>,
        dms: Map<String, List<ChatMessage>>,
        config: PersonalizationConfig
    ): List<User> {
        if (partners.size <= 1) return partners

        return partners.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<User>> { pinnedIds.contains(it.value.id) }
                    .thenBy { (index, user) ->
                        if (!config.personalizationEnabled) {
                            Int.MAX_VALUE
                        } else {
                            val recentIdx = config.recentConversationIds.indexOf(user.id)
                            if (recentIdx >= 0) recentIdx else Int.MAX_VALUE
                        }
                    }
                    .thenByDescending { (_, user) ->
                        dms[user.id]?.lastOrNull()?.timestamp ?: ""
                    }
                    .thenBy { it.index }
            )
            .map { it.value }
    }
}
