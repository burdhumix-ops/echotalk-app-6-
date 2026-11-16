package co.znxdnd.echotalk.data.personalization

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * PersonalizationManager: Centralized singleton managing controlled personalization
 * with strict customization boundaries, WCAG contrast protection, and local-first persistence.
 */
class PersonalizationManager private constructor(context: Context) {

    companion object {
        const val MAX_CUSTOM_THEMES = 10
        const val MAX_IMPORTED_FONTS = 2
        const val MAX_RECENT_ROOMS = 15
        const val MAX_RECENT_CONVERSATIONS = 20
        const val MAX_ROOM_VISIT_ENTRIES = 50
        const val MAX_INTERESTS = 15
        const val MAX_DRAFTS = 30
        const val MAX_RECENT_SEARCHES = 6
        private const val PREFS_NAME = "echotalk_personalization_prefs"

        @Volatile
        private var instance: PersonalizationManager? = null

        fun getInstance(context: Context? = null): PersonalizationManager {
            return instance ?: synchronized(this) {
                instance ?: PersonalizationManager(context!!.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<PersonalizationConfig> = _config.asStateFlow()

    internal fun reloadForTesting() {
        _config.value = loadConfig()
    }

    private fun safeGetString(key: String, default: String? = null): String? {
        return try {
            prefs.getString(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, default)
        } catch (_: Exception) {
            default
        }
    }

    private fun safeGetFloat(key: String, default: Float): Float {
        return try {
            val v = prefs.getFloat(key, default)
            if (v.isNaN() || v.isInfinite()) default else v
        } catch (_: Exception) {
            default
        }
    }

    private fun loadConfig(): PersonalizationConfig {
        val themesJson = safeGetString("custom_themes", null)
        val customThemes = if (themesJson != null) {
            parseThemes(themesJson)
        } else {
            getDefaultCustomThemes()
        }

        val activeThemeId = safeGetString("active_custom_theme_id", null)
        val fontId = safeGetString("curated_font_id", CuratedFont.SYSTEM_DEFAULT.id)
        val curatedFont = CuratedFont.entries.find { it.id == fontId } ?: CuratedFont.SYSTEM_DEFAULT

        val fontSlotsJson = safeGetString("custom_font_slots", null)
        val fontSlots = if (fontSlotsJson != null) parseFontSlots(fontSlotsJson) else emptyList()

        val textScale = safeGetFloat("interface_text_scale", 1.0f).coerceIn(0.85f, 1.25f)
        val msgTextSize = safeGetFloat("message_text_size_sp", 14f).coerceIn(12f, 20f)

        val densityId = safeGetString("density_id", UiDensity.STANDARD.id)
        val density = UiDensity.entries.find { it.id == densityId } ?: UiDensity.STANDARD

        val cornerId = safeGetString("corner_radius_id", CornerRadiusPreset.ROUNDED.id)
        val cornerRadius = CornerRadiusPreset.entries.find { it.id == cornerId } ?: CornerRadiusPreset.ROUNDED

        val animId = safeGetString("animation_level_id", AnimationLevel.NORMAL.id)
        val animationLevel = AnimationLevel.entries.find { it.id == animId } ?: AnimationLevel.NORMAL

        val chatPresetId = safeGetString("chat_preset_id", ChatAppearancePreset.MODERN_MINIMAL.id)
        val chatPreset = ChatAppearancePreset.entries.find { it.id == chatPresetId } ?: ChatAppearancePreset.MODERN_MINIMAL

        val dmBubbleHex = safeGetString("personal_dm_bubble_color", "#FF6366F1") ?: "#FF6366F1"
        val dmBubbleColor = SemanticColorRoles.fromHex(dmBubbleHex, Color(0xFF6366F1))

        val soundEnabled = safeGetBoolean("sound_effects_enabled", true)
        val soundProfileId = safeGetString("sound_profile_id", SoundProfile.POLITE_DIGITAL.id)
        val soundProfile = SoundProfile.entries.find { it.id == soundProfileId } ?: SoundProfile.POLITE_DIGITAL

        val hapticEnabled = safeGetBoolean("haptic_feedback_enabled", true)
        val hapticIntensityId = safeGetString("haptic_intensity_id", HapticIntensity.CRISP_GENTLE.id)
        val hapticIntensity = HapticIntensity.entries.find { it.id == hapticIntensityId } ?: HapticIntensity.CRISP_GENTLE

        val perfModeId = safeGetString("performance_mode_id", PerformanceMode.AUTOMATIC.id)
        val perfMode = PerformanceMode.entries.find { it.id == perfModeId } ?: PerformanceMode.AUTOMATIC

        val personalizationEnabled = safeGetBoolean("personalization_enabled", true)
        val recentRoomIds = parseStringList(safeGetString("recent_room_ids", null), MAX_RECENT_ROOMS)
        val roomVisitCounts = parseVisitCounts(safeGetString("room_visit_counts", null))
        val recentConversationIds = parseStringList(safeGetString("recent_conversation_ids", null), MAX_RECENT_CONVERSATIONS)
        val selectedInterests = parseStringList(safeGetString("selected_interests", null), MAX_INTERESTS)
        val lastSelectedTabId = safeGetString("last_selected_tab_id", null)
        val lastRoomCategory = safeGetString("last_room_category", "All Spaces") ?: "All Spaces"
        val lastDiscoverGender = safeGetString("last_discover_gender", "All") ?: "All"
        val lastDiscoverCountry = safeGetString("last_discover_country", "All") ?: "All"
        val lastDiscoverMinAge = safeGetFloat("last_discover_min_age", 18f).coerceIn(18f, 45f)
        val lastDiscoverMaxAge = safeGetFloat("last_discover_max_age", 35f).coerceIn(lastDiscoverMinAge, 45f)
        val dmDrafts = parseStringMap(safeGetString("dm_drafts", null), MAX_DRAFTS)
        val roomDrafts = parseStringMap(safeGetString("room_drafts", null), MAX_DRAFTS)
        val recentDiscoverSearches = parseStringList(safeGetString("recent_discover_searches", null), MAX_RECENT_SEARCHES)
        val recentDmSearches = parseStringList(safeGetString("recent_dm_searches", null), MAX_RECENT_SEARCHES)
        val lastDiscoverSearchQuery = safeGetString("last_discover_search_query", "") ?: ""
        val lastDmSearchQuery = safeGetString("last_dm_search_query", "") ?: ""
        val lastDmFolderTab = safeGetString("last_dm_folder_tab", "all") ?: "all"

        return PersonalizationConfig(
            activeCustomThemeId = activeThemeId,
            customThemes = customThemes,
            activeCuratedFont = curatedFont,
            customFontSlots = fontSlots,
            interfaceTextScale = textScale,
            messageTextSizeSp = msgTextSize,
            density = density,
            cornerRadius = cornerRadius,
            animationLevel = animationLevel,
            chatAppearancePreset = chatPreset,
            personalDmBubbleColor = dmBubbleColor,
            soundEffectsEnabled = soundEnabled,
            soundProfile = soundProfile,
            hapticFeedbackEnabled = hapticEnabled,
            hapticIntensity = hapticIntensity,
            performanceMode = perfMode,
            personalizationEnabled = personalizationEnabled,
            recentRoomIds = recentRoomIds,
            roomVisitCounts = roomVisitCounts,
            recentConversationIds = recentConversationIds,
            selectedInterests = selectedInterests,
            lastSelectedTabId = lastSelectedTabId,
            lastRoomCategory = lastRoomCategory,
            lastDiscoverGender = lastDiscoverGender,
            lastDiscoverCountry = lastDiscoverCountry,
            lastDiscoverMinAge = lastDiscoverMinAge,
            lastDiscoverMaxAge = lastDiscoverMaxAge,
            dmDrafts = dmDrafts,
            roomDrafts = roomDrafts,
            recentDiscoverSearches = recentDiscoverSearches,
            recentDmSearches = recentDmSearches,
            lastDiscoverSearchQuery = lastDiscoverSearchQuery,
            lastDmSearchQuery = lastDmSearchQuery,
            lastDmFolderTab = lastDmFolderTab
        )
    }

    private fun parseStringMap(jsonStr: String?, limit: Int): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val result = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext() && result.size < limit) {
                val key = keys.next().trim()
                val value = obj.optString(key, "")
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    result[key] = value.take(2000)
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistStringMap(key: String, map: Map<String, String>) {
        val obj = JSONObject()
        map.entries.take(MAX_DRAFTS).forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotEmpty()) {
                obj.put(k, v.take(2000))
            }
        }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    private fun parseStringList(jsonStr: String?, limit: Int): List<String> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val result = mutableListOf<String>()
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotEmpty() && seen.add(item)) {
                    result.add(item)
                    if (result.size >= limit) break
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistStringList(key: String, items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun parseVisitCounts(jsonStr: String?): Map<String, Int> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val result = LinkedHashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext() && result.size < MAX_ROOM_VISIT_ENTRIES) {
                val key = keys.next().trim()
                val count = obj.optInt(key, 0)
                if (key.isNotEmpty() && count > 0) {
                    result[key] = count
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistVisitCounts(counts: Map<String, Int>) {
        val obj = JSONObject()
        counts.entries
            .sortedByDescending { it.value }
            .take(MAX_ROOM_VISIT_ENTRIES)
            .forEach { (k, v) ->
                if (v > 0) obj.put(k, v)
            }
        prefs.edit().putString("room_visit_counts", obj.toString()).apply()
    }

    private fun persistThemes(themes: List<CustomTheme>) {
        val arr = JSONArray()
        themes.forEach { theme ->
            val obj = JSONObject()
            obj.put("id", theme.id)
            obj.put("name", theme.name)
            obj.put("isDark", theme.isDark)
            val colorsObj = JSONObject()
            theme.colors.toHexMap().forEach { (k, v) -> colorsObj.put(k, v) }
            obj.put("colors", colorsObj)
            arr.put(obj)
        }
        prefs.edit().putString("custom_themes", arr.toString()).apply()
    }

    private fun parseThemes(jsonStr: String): List<CustomTheme> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CustomTheme>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val isDark = obj.optBoolean("isDark", true)
                val colorsObj = obj.getJSONObject("colors")
                val map = mutableMapOf<String, String>()
                val keys = colorsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = colorsObj.getString(k)
                }
                val colors = ContrastProtector.protectPalette(SemanticColorRoles.fromHexMap(map))
                list.add(CustomTheme(id, name, colors, isBuiltIn = false, isDark = isDark))
            }
            list.take(MAX_CUSTOM_THEMES)
        } catch (e: Exception) {
            getDefaultCustomThemes()
        }
    }

    private fun parseFontSlots(jsonStr: String): List<CustomFontSlot> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CustomFontSlot>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CustomFontSlot(
                        slotIndex = obj.getInt("slotIndex"),
                        name = obj.getString("name"),
                        filePath = obj.getString("filePath"),
                        isLoaded = obj.optBoolean("isLoaded", true)
                    )
                )
            }
            list.take(MAX_IMPORTED_FONTS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getDefaultCustomThemes(): List<CustomTheme> {
        val defaultColors = ContrastProtector.protectPalette(SemanticColorRoles.defaultPalette())
        val theme1 = CustomTheme(
            id = "custom_neon_slate",
            name = "Neon Horizon",
            colors = defaultColors,
            isBuiltIn = false,
            isDark = true
        )
        val theme2 = CustomTheme(
            id = "custom_emerald_breeze",
            name = "Emerald Coast",
            colors = ContrastProtector.protectPalette(
                defaultColors.copy(
                    primary = Color(0xFF10B981),
                    secondary = Color(0xFF34D399),
                    accent = Color(0xFFF59E0B),
                    personalDmBubble = Color(0xFF10B981),
                    publicRoomAccent = Color(0xFF34D399)
                )
            ),
            isBuiltIn = false,
            isDark = true
        )
        return listOf(theme1, theme2)
    }

    // =========================================================================
    // THEMES (MAX 10, CONTRAST PROTECTED, CANNOT REPLACE BUILT-IN)
    // =========================================================================

    /**
     * Saves or updates a custom theme. Enforces MAX_CUSTOM_THEMES limit and contrast protection.
     */
    fun saveCustomTheme(theme: CustomTheme): Boolean {
        val currentList = _config.value.customThemes.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == theme.id }
        val protectedColors = ContrastProtector.protectPalette(theme.colors)
        val protectedTheme = theme.copy(colors = protectedColors, isBuiltIn = false)

        if (existingIndex >= 0) {
            currentList[existingIndex] = protectedTheme
        } else {
            if (currentList.size >= MAX_CUSTOM_THEMES) {
                return false // Strict limit reached!
            }
            currentList.add(protectedTheme)
        }

        persistThemes(currentList)
        _config.value = _config.value.copy(customThemes = currentList)
        return true
    }

    fun duplicateCustomTheme(id: String): Boolean {
        val currentList = _config.value.customThemes.toMutableList()
        if (currentList.size >= MAX_CUSTOM_THEMES) return false

        val original = currentList.find { it.id == id } ?: return false
        val newTheme = original.copy(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = "${original.name} (Copy)"
        )
        currentList.add(newTheme)
        persistThemes(currentList)
        _config.value = _config.value.copy(customThemes = currentList)
        return true
    }

    fun renameCustomTheme(id: String, newName: String) {
        val currentList = _config.value.customThemes.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(name = newName.trim().take(30))
            persistThemes(currentList)
            _config.value = _config.value.copy(customThemes = currentList)
        }
    }

    fun deleteCustomTheme(id: String): Boolean {
        val currentList = _config.value.customThemes.toMutableList()
        val removed = currentList.removeAll { it.id == id }
        if (removed) {
            persistThemes(currentList)
            val newActiveTheme = if (_config.value.activeCustomThemeId == id) null else _config.value.activeCustomThemeId
            prefs.edit().putString("active_custom_theme_id", newActiveTheme).apply()
            _config.value = _config.value.copy(
                customThemes = currentList,
                activeCustomThemeId = newActiveTheme
            )
        }
        return removed
    }

    fun restoreDefaultCustomThemes() {
        val defaults = getDefaultCustomThemes()
        persistThemes(defaults)
        prefs.edit().remove("active_custom_theme_id").apply()
        _config.value = _config.value.copy(
            customThemes = defaults,
            activeCustomThemeId = null
        )
    }

    fun setActiveCustomTheme(id: String?) {
        prefs.edit().putString("active_custom_theme_id", id).apply()
        _config.value = _config.value.copy(activeCustomThemeId = id)
    }

    // =========================================================================
    // TYPOGRAPHY (8 CURATED FONTS + MAX 2 IMPORTED)
    // =========================================================================

    fun setCuratedFont(font: CuratedFont) {
        prefs.edit().putString("curated_font_id", font.id).apply()
        _config.value = _config.value.copy(activeCuratedFont = font)
    }

    fun importCustomFont(slotIndex: Int, fontName: String, filePath: String): Boolean {
        if (slotIndex !in 1..MAX_IMPORTED_FONTS) return false
        val slots = _config.value.customFontSlots.toMutableList()
        slots.removeAll { it.slotIndex == slotIndex }
        slots.add(CustomFontSlot(slotIndex, fontName.take(24), filePath, isLoaded = true))
        val arr = JSONArray()
        slots.forEach { slot ->
            val obj = JSONObject()
            obj.put("slotIndex", slot.slotIndex)
            obj.put("name", slot.name)
            obj.put("filePath", slot.filePath)
            obj.put("isLoaded", slot.isLoaded)
            arr.put(obj)
        }
        prefs.edit().putString("custom_font_slots", arr.toString()).apply()
        _config.value = _config.value.copy(customFontSlots = slots)
        return true
    }

    fun removeImportedFont(slotIndex: Int) {
        val slots = _config.value.customFontSlots.filter { it.slotIndex != slotIndex }
        val arr = JSONArray()
        slots.forEach { slot ->
            val obj = JSONObject()
            obj.put("slotIndex", slot.slotIndex)
            obj.put("name", slot.name)
            obj.put("filePath", slot.filePath)
            obj.put("isLoaded", slot.isLoaded)
            arr.put(obj)
        }
        prefs.edit().putString("custom_font_slots", arr.toString()).apply()
        _config.value = _config.value.copy(customFontSlots = slots)
    }

    fun setInterfaceTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.25f)
        prefs.edit().putFloat("interface_text_scale", clamped).apply()
        _config.value = _config.value.copy(interfaceTextScale = clamped)
    }

    fun setMessageTextSizeSp(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(12f, 20f)
        prefs.edit().putFloat("message_text_size_sp", clamped).apply()
        _config.value = _config.value.copy(messageTextSizeSp = clamped)
    }

    // =========================================================================
    // DISPLAY (DENSITY, CORNERS, ANIMATIONS)
    // =========================================================================

    fun setDensity(density: UiDensity) {
        prefs.edit().putString("density_id", density.id).apply()
        _config.value = _config.value.copy(density = density)
    }

    fun setCornerRadius(radius: CornerRadiusPreset) {
        prefs.edit().putString("corner_radius_id", radius.id).apply()
        _config.value = _config.value.copy(cornerRadius = radius)
    }

    fun setAnimationLevel(level: AnimationLevel) {
        prefs.edit().putString("animation_level_id", level.id).apply()
        _config.value = _config.value.copy(animationLevel = level)
    }

    // =========================================================================
    // CHAT APPEARANCE (5 PRESETS, PERSONAL DM BUBBLE, LIGHT/DARK)
    // =========================================================================

    fun setChatAppearancePreset(preset: ChatAppearancePreset) {
        val hex = with(SemanticColorRoles) { preset.dmBubbleColor.toHex() }
        prefs.edit()
            .putString("chat_preset_id", preset.id)
            .putString("personal_dm_bubble_color", hex)
            .apply()
        _config.value = _config.value.copy(
            chatAppearancePreset = preset,
            personalDmBubbleColor = preset.dmBubbleColor
        )
    }

    fun setPersonalDmBubbleColor(color: Color) {
        val hex = with(SemanticColorRoles) { color.toHex() }
        prefs.edit().putString("personal_dm_bubble_color", hex).apply()
        _config.value = _config.value.copy(personalDmBubbleColor = color)
    }

    // =========================================================================
    // SOUNDS & HAPTICS (4 SOUND PROFILES, 2 HAPTIC INTENSITIES)
    // =========================================================================

    fun setSoundEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_effects_enabled", enabled).apply()
        _config.value = _config.value.copy(soundEffectsEnabled = enabled)
    }

    fun setSoundProfile(profile: SoundProfile) {
        prefs.edit().putString("sound_profile_id", profile.id).apply()
        _config.value = _config.value.copy(soundProfile = profile)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback_enabled", enabled).apply()
        _config.value = _config.value.copy(hapticFeedbackEnabled = enabled)
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        prefs.edit().putString("haptic_intensity_id", intensity.id).apply()
        _config.value = _config.value.copy(hapticIntensity = intensity)
    }

    // =========================================================================
    // PERFORMANCE MODES (AUTOMATIC, BALANCED, BATTERY SAVER)
    // =========================================================================

    fun setPerformanceMode(mode: PerformanceMode) {
        prefs.edit().putString("performance_mode_id", mode.id).apply()
        _config.value = _config.value.copy(performanceMode = mode)
    }

    // =========================================================================
    // PHASE 1: LIGHTWEIGHT LOCAL PERSONALIZATION, MEMORY & PRIVACY CONTROLS
    // =========================================================================

    fun setPersonalizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("personalization_enabled", enabled).apply()
        _config.value = _config.value.copy(personalizationEnabled = enabled)
    }

    fun recordRoomVisit(roomId: String) {
        val cleanId = roomId.trim()
        if (cleanId.isEmpty() || !_config.value.personalizationEnabled) return

        val updatedRecent = (listOf(cleanId) + _config.value.recentRoomIds.filter { it != cleanId })
            .take(MAX_RECENT_ROOMS)
        val updatedCounts = _config.value.roomVisitCounts.toMutableMap()
        val currentCount = updatedCounts[cleanId] ?: 0
        updatedCounts[cleanId] = (currentCount + 1).coerceAtMost(1000)

        persistStringList("recent_room_ids", updatedRecent)
        persistVisitCounts(updatedCounts)
        _config.value = _config.value.copy(
            recentRoomIds = updatedRecent,
            roomVisitCounts = updatedCounts.toMap()
        )
    }

    fun recordConversationOpened(partnerId: String) {
        val cleanId = partnerId.trim()
        if (cleanId.isEmpty() || !_config.value.personalizationEnabled) return

        val updatedRecent = (listOf(cleanId) + _config.value.recentConversationIds.filter { it != cleanId })
            .take(MAX_RECENT_CONVERSATIONS)
        persistStringList("recent_conversation_ids", updatedRecent)
        _config.value = _config.value.copy(recentConversationIds = updatedRecent)
    }

    fun setSelectedInterests(interests: List<String>) {
        if (!_config.value.personalizationEnabled) return
        val sanitized = interests.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_INTERESTS)
        persistStringList("selected_interests", sanitized)
        _config.value = _config.value.copy(selectedInterests = sanitized)
    }

    fun setLastSelectedTab(tabId: String) {
        if (tabId.isBlank() || !_config.value.personalizationEnabled) return
        prefs.edit().putString("last_selected_tab_id", tabId).apply()
        _config.value = _config.value.copy(lastSelectedTabId = tabId)
    }

    fun setLastRoomCategory(category: String) {
        if (category.isBlank() || !_config.value.personalizationEnabled) return
        prefs.edit().putString("last_room_category", category).apply()
        _config.value = _config.value.copy(lastRoomCategory = category)
    }

    fun setLastDiscoverFilters(gender: String, country: String, minAge: Float, maxAge: Float) {
        if (!_config.value.personalizationEnabled) return
        val safeMin = minAge.coerceIn(18f, 45f)
        val safeMax = maxAge.coerceIn(safeMin, 45f)
        prefs.edit()
            .putString("last_discover_gender", gender)
            .putString("last_discover_country", country)
            .putFloat("last_discover_min_age", safeMin)
            .putFloat("last_discover_max_age", safeMax)
            .apply()
        _config.value = _config.value.copy(
            lastDiscoverGender = gender,
            lastDiscoverCountry = country,
            lastDiscoverMinAge = safeMin,
            lastDiscoverMaxAge = safeMax
        )
    }

    /**
     * Saves or clears an unsent draft for a DM conversation.
     * Safe to call even if personalization is disabled, or cleared cleanly on send.
     */
    fun saveDmDraft(partnerId: String, draft: String) {
        if (partnerId.isBlank()) return
        try {
            val current = _config.value.dmDrafts.toMutableMap()
            if (draft.isBlank()) {
                if (current.remove(partnerId) == null) return
            } else {
                val trimmed = draft.take(2000)
                if (current[partnerId] == trimmed) return
                current[partnerId] = trimmed
            }
            val bounded = current.entries.toList().takeLast(MAX_DRAFTS).associate { it.key to it.value }
            persistStringMap("dm_drafts", bounded)
            _config.value = _config.value.copy(dmDrafts = bounded)
        } catch (_: Exception) {}
    }

    fun getDmDraft(partnerId: String): String {
        return try {
            _config.value.dmDrafts[partnerId].orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun saveRoomDraft(roomId: String, draft: String) {
        if (roomId.isBlank()) return
        try {
            val current = _config.value.roomDrafts.toMutableMap()
            if (draft.isBlank()) {
                if (current.remove(roomId) == null) return
            } else {
                val trimmed = draft.take(2000)
                if (current[roomId] == trimmed) return
                current[roomId] = trimmed
            }
            val bounded = current.entries.toList().takeLast(MAX_DRAFTS).associate { it.key to it.value }
            persistStringMap("room_drafts", bounded)
            _config.value = _config.value.copy(roomDrafts = bounded)
        } catch (_: Exception) {}
    }

    fun getRoomDraft(roomId: String): String {
        return try {
            _config.value.roomDrafts[roomId].orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun recordDiscoverSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2 || !_config.value.personalizationEnabled) return
        try {
            val updated = (listOf(clean) + _config.value.recentDiscoverSearches.filter { !it.equals(clean, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            persistStringList("recent_discover_searches", updated)
            _config.value = _config.value.copy(recentDiscoverSearches = updated)
        } catch (_: Exception) {}
    }

    fun clearDiscoverRecentSearches() {
        try {
            prefs.edit().remove("recent_discover_searches").apply()
            _config.value = _config.value.copy(recentDiscoverSearches = emptyList())
        } catch (_: Exception) {}
    }

    fun recordDmSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2 || !_config.value.personalizationEnabled) return
        try {
            val updated = (listOf(clean) + _config.value.recentDmSearches.filter { !it.equals(clean, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            persistStringList("recent_dm_searches", updated)
            _config.value = _config.value.copy(recentDmSearches = updated)
        } catch (_: Exception) {}
    }

    fun clearDmRecentSearches() {
        try {
            prefs.edit().remove("recent_dm_searches").apply()
            _config.value = _config.value.copy(recentDmSearches = emptyList())
        } catch (_: Exception) {}
    }

    fun setLastDiscoverSearchQuery(query: String) {
        if (!_config.value.personalizationEnabled) return
        try {
            prefs.edit().putString("last_discover_search_query", query).apply()
            _config.value = _config.value.copy(lastDiscoverSearchQuery = query)
        } catch (_: Exception) {}
    }

    fun setLastDmSearchState(query: String, folderTab: String) {
        if (!_config.value.personalizationEnabled) return
        try {
            prefs.edit()
                .putString("last_dm_search_query", query)
                .putString("last_dm_folder_tab", folderTab)
                .apply()
            _config.value = _config.value.copy(
                lastDmSearchQuery = query,
                lastDmFolderTab = folderTab
            )
        } catch (_: Exception) {}
    }

    /**
     * Clears all locally stored personalization history, adaptive memory, and saved custom presets,
     * restoring safe factory defaults while preserving the user's personalization opt-in/opt-out choice.
     */
    fun clearPersonalizationData() {
        val keepEnabled = _config.value.personalizationEnabled
        prefs.edit().clear().putBoolean("personalization_enabled", keepEnabled).apply()
        _config.value = PersonalizationConfig(
            customThemes = getDefaultCustomThemes(),
            personalizationEnabled = keepEnabled
        )
    }
}
