package co.znxdnd.echotalk.utils

import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.User
import kotlin.math.max
import kotlin.math.min

/**
 * ConversationSearchEngine: Multi-signal fuzzy & prefix search algorithm combining
 * exact substring boost, token overlap, Jaro-Winkler / prefix affinity, and bounded
 * Damerau-Levenshtein edit distance for typo-tolerant Crew & DM discovery.
 */
object ConversationSearchEngine {

    /**
     * Optimal String Alignment / Restricted Damerau-Levenshtein distance supporting
     * insertions, deletions, substitutions, and adjacent character transpositions.
     */
    fun damerauLevenshtein(source: String, target: String): Int {
        val a = source.lowercase()
        val b = target.lowercase()
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }

    /**
     * Normalized fuzzy similarity score in [0.0, 1.0].
     */
    fun similarity(query: String, candidate: String): Double {
        val q = query.trim().lowercase()
        val c = candidate.trim().lowercase()
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (c == q) return 1.0
        if (c.startsWith(q)) return 0.94
        if (c.contains(q)) return 0.85

        val distance = damerauLevenshtein(q, c)
        val maxLen = max(q.length, c.length).toDouble()
        return (1.0 - (distance / maxLen)).coerceIn(0.0, 1.0)
    }

    /**
     * Computes composite relevance score for a User against a multi-word or typo-prone query.
     * Returns 0 when below minimum relevance threshold.
     */
    fun scoreUser(user: User, query: String): Int {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return 100

        val handleBare = user.handle.removePrefix("@").lowercase()
        val searchableFields = listOf(
            user.name.lowercase() to 100,
            handleBare to 95,
            user.country.lowercase() to 75,
            user.bio.lowercase() to 55
        )

        var bestScore = 0
        val queryTokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

        for ((field, weight) in searchableFields) {
            if (field.isEmpty()) continue
            when {
                field == cleanQuery -> bestScore = max(bestScore, weight + 40)
                field.startsWith(cleanQuery) -> bestScore = max(bestScore, weight + 28)
                field.contains(cleanQuery) -> bestScore = max(bestScore, weight + 14)
            }

            // Token-level & typo-tolerant matching
            val fieldTokens = field.split(Regex("[^a-z0-9_]+")).filter { it.isNotBlank() }
            var matchedTokenWeight = 0
            for (qt in queryTokens) {
                if (qt.length < 2) continue
                val maxAllowedEdits = when {
                    qt.length <= 3 -> 1
                    qt.length <= 7 -> 2
                    else -> 3
                }
                var bestTokenSim = 0.0
                for (ft in fieldTokens) {
                    if (ft.startsWith(qt)) {
                        bestTokenSim = max(bestTokenSim, 0.92)
                    } else if (ft.contains(qt)) {
                        bestTokenSim = max(bestTokenSim, 0.84)
                    } else {
                        val dist = damerauLevenshtein(qt, ft)
                        if (dist <= maxAllowedEdits) {
                            val sim = 1.0 - (dist.toDouble() / max(qt.length, ft.length))
                            bestTokenSim = max(bestTokenSim, sim)
                        }
                    }
                }
                if (bestTokenSim >= 0.55) {
                    matchedTokenWeight += (bestTokenSim * weight * 0.8).toInt()
                }
            }
            bestScore = max(bestScore, matchedTokenWeight)
        }

        if (bestScore <= 0) return 0
        if (user.online) bestScore += 6
        return bestScore
    }

    /**
     * Filters and ranks matching users cleanly, preserving online-first grouping when tie-breaking.
     */
    fun searchUsers(
        users: List<User>,
        query: String,
        onlineFirst: Boolean = true
    ): List<User> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return users

        return users.mapNotNull { user ->
            val score = scoreUser(user, trimmed)
            if (score > 0) user to score else null
        }.sortedWith(
            compareByDescending<Pair<User, Int>> { if (onlineFirst) it.first.online else false }
                .thenByDescending { it.second }
                .thenBy { it.first.name.lowercase() }
        ).map { it.first }
    }

    /**
     * Searches conversation partners by profile relevance OR recent message content matches.
     */
    fun searchDmPartners(
        partners: List<User>,
        dms: Map<String, List<ChatMessage>>,
        query: String
    ): List<User> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return partners

        return partners.withIndex().mapNotNull { (idx, partner) ->
            val profileScore = scoreUser(partner, trimmed)
            val cleanQuery = trimmed.lowercase()
            val messageHits = dms[partner.id].orEmpty().count { msg ->
                msg.text.contains(cleanQuery, ignoreCase = true)
            }
            val msgBonus = min(messageHits * 22, 66)
            val totalScore = profileScore + msgBonus
            if (totalScore > 0) Triple(partner, totalScore, idx) else null
        }.sortedWith(
            compareByDescending<Triple<User, Int, Int>> { it.second }
                .thenBy { it.third }
        ).map { it.first }
    }
}
