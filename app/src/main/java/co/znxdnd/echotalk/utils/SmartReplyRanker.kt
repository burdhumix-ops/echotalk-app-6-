package co.znxdnd.echotalk.utils

import co.znxdnd.echotalk.data.model.ChatMessage
import co.znxdnd.echotalk.data.model.User

/**
 * SmartReplyRanker: Deterministic contextual NLP intent classifier that ranks 3 context-aware
 * quick replies for active conversations based on the latest incoming message type & semantics.
 */
object SmartReplyRanker {

    fun suggestReplies(
        thread: List<ChatMessage>,
        currentUserName: String,
        partner: User? = null
    ): List<String> {
        val lastIncoming = thread.lastOrNull {
            !it.sender.equals(currentUserName, ignoreCase = true)
        } ?: return defaultReplies(partner)

        if (lastIncoming.isChallenge) {
            return listOf("Accept Challenge ♟️", "On my way! 🎮", "Give me 5 mins ⏳")
        }
        if (lastIncoming.isViewOnce) {
            return listOf("Whoa, love it! 🔥", "So clean! ✨", "Send another! 📸")
        }
        if (lastIncoming.isAudio) {
            return listOf("Sounds amazing! 🎧", "Listening now 🔊", "Love the vibe ✨")
        }

        val text = lastIncoming.text.lowercase()
        val hasGameIntent = containsAny(text, "chess", "game", "play", "rematch", "mafia", "duel", "challenge")
        val hasAudioIntent = containsAny(text, "synth", "audio", "track", "music", "beat", "recording", "lofi", "studio")
        val hasGreetingIntent = containsAny(text, "hey ", "hi ", "hello", "morning", "evening", "how are you", "what's up")
        val hasQuestion = text.contains("?") || containsAny(text, "ready", "when ", "where ", "want to", "up for")

        return when {
            hasGameIntent && hasQuestion -> listOf("I'm ready! ♟️", "Let's start now 🎮", "Give me 2 mins ⏳")
            hasGameIntent -> listOf("Ready for Chess ♟️", "Let's run a match!", "Bring it on 🔥")
            hasAudioIntent -> listOf("Drop the track! 🎧", "Synth sounds clean 🎛️", "Love that acoustic vibe ✨")
            hasQuestion -> listOf("Yes, sounds great! 👍", "Let me check in a sec", "Count me in! ✨")
            hasGreetingIntent -> listOf("Hey! Good to see you 👋", "Doing great, you?", "What's the plan today? ✨")
            else -> defaultReplies(partner)
        }
    }

    private fun defaultReplies(partner: User?): List<String> {
        return if (partner?.online == true) {
            listOf("Sounds great! 👍", "Ready for Chess ♟️", "Can't wait! ✨")
        } else {
            listOf("Catch you soon! 👋", "Sounds good 👍", "Let's play later ♟️")
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
