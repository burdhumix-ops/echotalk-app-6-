package co.znxdnd.echotalk.ui.components

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.znxdnd.echotalk.audio.SoundEffectManager
import co.znxdnd.echotalk.data.personalization.*
import co.znxdnd.echotalk.data.personalization.SemanticColorRoles.Companion.toHex
import co.znxdnd.echotalk.data.repository.AppTheme
import co.znxdnd.echotalk.data.repository.EchoTalkRepository
import co.znxdnd.echotalk.ui.theme.ThemeManager
import co.znxdnd.echotalk.utils.EchoHapticManager
import co.znxdnd.echotalk.utils.PoliteSoundSynthesizer
import java.util.UUID

/**
 * Controlled Personalization Screen View.
 * Implements strict customization boundaries, max 10 custom themes,
 * 8 curated fonts + 2 custom fonts, WCAG contrast protection,
 * 3 densities, 3 corner radii, 3 animation levels, 5 chat presets,
 * 4 sound profiles, 2 haptic levels, and 3 performance modes.
 */
@Composable
fun PersonalizationSettingsView(
    repository: EchoTalkRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val personalizationManager = remember { PersonalizationManager.getInstance(context) }
    val config by personalizationManager.config.collectAsState()
    val activeTheme by repository.theme.collectAsState()

    var activeSubSection by remember { mutableStateOf("themes") }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showCreateThemeDialog by remember { mutableStateOf(false) }
    var showRenameDialogForTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showImportFontDialog by remember { mutableStateOf<Int?>(null) } // slotIndex: 1 or 2
    var fontNameInput by remember { mutableStateOf("") }
    var fontFileNameInput by remember { mutableStateOf("") }

    val sections = listOf(
        "themes" to "Themes & Colors",
        "typography" to "Typography",
        "display" to "Display & Motion",
        "chat" to "Chat Appearance",
        "audio" to "Sound & Haptics",
        "performance" to "Performance"
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Horizontal Sub-section Tabs
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEach { (id, label) ->
                val isSelected = activeSubSection == id
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            activeSubSection = id
                        }
                        .testTag("personalization_tab_$id"),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            when (activeSubSection) {
                "themes" -> ThemesAndColorsSection(
                    config = config,
                    activeTheme = activeTheme,
                    onSelectBuiltInTheme = { theme ->
                        personalizationManager.setActiveCustomTheme(null)
                        repository.updateTheme(theme)
                    },
                    onSelectCustomTheme = { customTheme ->
                        personalizationManager.setActiveCustomTheme(customTheme.id)
                    },
                    onCreateTheme = {
                        if (config.customThemes.size >= PersonalizationManager.MAX_CUSTOM_THEMES) {
                            Toast.makeText(context, "Maximum 10 custom themes limit reached", Toast.LENGTH_SHORT).show()
                        } else {
                            showCreateThemeDialog = true
                        }
                    },
                    onDuplicateTheme = { theme ->
                        val success = personalizationManager.duplicateCustomTheme(theme.id)
                        if (!success) {
                            Toast.makeText(context, "Cannot duplicate: 10 theme limit reached", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Duplicated ${theme.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRenameTheme = { theme ->
                        showRenameDialogForTheme = theme
                        renameInput = theme.name
                    },
                    onEditTheme = { theme ->
                        editingTheme = theme
                    },
                    onDeleteTheme = { theme ->
                        personalizationManager.deleteCustomTheme(theme.id)
                        Toast.makeText(context, "Deleted ${theme.name}", Toast.LENGTH_SHORT).show()
                    },
                    onRestoreDefaults = {
                        personalizationManager.restoreDefaultCustomThemes()
                        Toast.makeText(context, "Restored default custom themes", Toast.LENGTH_SHORT).show()
                    }
                )

                "typography" -> TypographySection(
                    config = config,
                    onSelectCuratedFont = { font ->
                        personalizationManager.setCuratedFont(font)
                    },
                    onOpenImportFont = { slot ->
                        showImportFontDialog = slot
                        fontNameInput = ""
                        fontFileNameInput = ""
                    },
                    onRemoveImportedFont = { slot ->
                        personalizationManager.removeImportedFont(slot)
                    },
                    onSetTextScale = { scale ->
                        personalizationManager.setInterfaceTextScale(scale)
                    },
                    onSetMessageTextSize = { sizeSp ->
                        personalizationManager.setMessageTextSizeSp(sizeSp)
                    }
                )

                "display" -> DisplayAndMotionSection(
                    config = config,
                    onSetDensity = { density ->
                        personalizationManager.setDensity(density)
                    },
                    onSetCornerRadius = { radius ->
                        personalizationManager.setCornerRadius(radius)
                    },
                    onSetAnimationLevel = { level ->
                        personalizationManager.setAnimationLevel(level)
                    }
                )

                "chat" -> ChatAppearanceSection(
                    config = config,
                    onSelectPreset = { preset ->
                        personalizationManager.setChatAppearancePreset(preset)
                    },
                    onSetDmBubbleColor = { color ->
                        personalizationManager.setPersonalDmBubbleColor(color)
                    }
                )

                "audio" -> AudioAndHapticsSection(
                    config = config,
                    onToggleSound = { personalizationManager.setSoundEffectsEnabled(it) },
                    onSetSoundProfile = { personalizationManager.setSoundProfile(it) },
                    onToggleHaptics = { personalizationManager.setHapticFeedbackEnabled(it) },
                    onSetHapticIntensity = { personalizationManager.setHapticIntensity(it) }
                )

                "performance" -> PerformanceSection(
                    config = config,
                    onSetMode = { personalizationManager.setPerformanceMode(it) }
                )
            }
        }
    }

    // Dialog: Create or Edit Custom Theme
    if (showCreateThemeDialog || editingTheme != null) {
        val targetTheme = editingTheme ?: CustomTheme(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = "My Theme ${config.customThemes.size + 1}",
            colors = ContrastProtector.protectPalette(SemanticColorRoles.defaultPalette()),
            isBuiltIn = false,
            isDark = true
        )

        ThemeEditorDialog(
            theme = targetTheme,
            onDismiss = {
                showCreateThemeDialog = false
                editingTheme = null
            },
            onSave = { updatedTheme ->
                val saved = personalizationManager.saveCustomTheme(updatedTheme)
                if (saved) {
                    personalizationManager.setActiveCustomTheme(updatedTheme.id)
                    Toast.makeText(context, "Saved theme: ${updatedTheme.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not save: 10 theme limit reached", Toast.LENGTH_SHORT).show()
                }
                showCreateThemeDialog = false
                editingTheme = null
            }
        )
    }

    // Dialog: Rename Custom Theme
    if (showRenameDialogForTheme != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialogForTheme = null },
            title = { Text("Rename Theme", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = showRenameDialogForTheme
                        if (current != null && renameInput.isNotBlank()) {
                            personalizationManager.renameCustomTheme(current.id, renameInput)
                        }
                        showRenameDialogForTheme = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForTheme = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Import Custom Font (Max 2 Slots, TTF/OTF only)
    if (showImportFontDialog != null) {
        val slot = showImportFontDialog!!
        AlertDialog(
            onDismissRequest = { showImportFontDialog = null },
            title = { Text("Import Font (Slot $slot/2)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Import a local custom typeface. Only valid .ttf and .otf font files are supported to prevent layout distortion.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = fontNameInput,
                        onValueChange = { fontNameInput = it },
                        label = { Text("Font Family Name") },
                        placeholder = { Text("e.g. SF Pro Display") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fontFileNameInput,
                        onValueChange = { fontFileNameInput = it },
                        label = { Text("File Name / Path (.ttf or .otf)") },
                        placeholder = { Text("font_custom.ttf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = fontNameInput.trim()
                        val file = fontFileNameInput.trim()
                        if (name.isBlank() || file.isBlank()) {
                            Toast.makeText(context, "Please enter both font name and file", Toast.LENGTH_SHORT).show()
                        } else if (!file.endsWith(".ttf", ignoreCase = true) && !file.endsWith(".otf", ignoreCase = true)) {
                            Toast.makeText(context, "Only .ttf and .otf font formats are supported", Toast.LENGTH_SHORT).show()
                        } else {
                            personalizationManager.importCustomFont(slot, name, file)
                            Toast.makeText(context, "Font imported into Slot $slot", Toast.LENGTH_SHORT).show()
                            showImportFontDialog = null
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportFontDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Themes & Colors Section: Built-in Themes + Max 10 Custom Themes with CRUD & Live Preview.
 */
@Composable
private fun ThemesAndColorsSection(
    config: PersonalizationConfig,
    activeTheme: AppTheme,
    onSelectBuiltInTheme: (AppTheme) -> Unit,
    onSelectCustomTheme: (CustomTheme) -> Unit,
    onCreateTheme: () -> Unit,
    onDuplicateTheme: (CustomTheme) -> Unit,
    onRenameTheme: (CustomTheme) -> Unit,
    onEditTheme: (CustomTheme) -> Unit,
    onDeleteTheme: (CustomTheme) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    val isCustomActive = config.activeCustomThemeId != null

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Built-in Themes Gallery
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Built-in Presets (9)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Permanent • Untouchable",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(AppTheme.entries.toList()) { theme ->
            val isSelected = !isCustomActive && activeTheme == theme
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectBuiltInTheme(theme) }
                    .testTag("builtin_theme_${theme.name}"),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = theme.displayName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = theme.description,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Custom Themes Header with 10 themes count constraint
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Custom Saved Themes",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${config.customThemes.size} of ${PersonalizationManager.MAX_CUSTOM_THEMES} maximum saved",
                        fontSize = 10.5.sp,
                        color = if (config.customThemes.size >= PersonalizationManager.MAX_CUSTOM_THEMES) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onRestoreDefaults,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Restore Default", fontSize = 10.5.sp)
                    }

                    Button(
                        onClick = onCreateTheme,
                        enabled = config.customThemes.size < PersonalizationManager.MAX_CUSTOM_THEMES,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("create_custom_theme_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (config.customThemes.size >= PersonalizationManager.MAX_CUSTOM_THEMES) "Limit Reached" else "New Theme",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Custom Themes List
        items(config.customThemes) { customTheme ->
            val isSelected = isCustomActive && config.activeCustomThemeId == customTheme.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCustomTheme(customTheme) }
                    .testTag("custom_theme_${customTheme.id}"),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Palette preview dots (Primary, Secondary, Accent, Background)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(customTheme.colors.primary))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(customTheme.colors.secondary))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(customTheme.colors.accent))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(customTheme.colors.mainBackground).border(0.5.dp, Color.White, CircleShape))
                            }
                            Text(
                                text = customTheme.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                    Text("ACTIVE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }

                        // Contrast Protection Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                Text("Contrast Pass", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Controls: Edit, Duplicate, Rename, Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onEditTheme(customTheme) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = "Edit Palette", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDuplicateTheme(customTheme) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onRenameTheme(customTheme) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteTheme(customTheme) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Typography Section: 8 Curated Fonts + Max 2 Imported Custom Fonts + Separate Scaling.
 */
@Composable
private fun TypographySection(
    config: PersonalizationConfig,
    onSelectCuratedFont: (CuratedFont) -> Unit,
    onOpenImportFont: (Int) -> Unit,
    onRemoveImportedFont: (Int) -> Unit,
    onSetTextScale: (Float) -> Unit,
    onSetMessageTextSize: (Float) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Curated Built-in Fonts (8)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 8 Curated Fonts Grid
        items(CuratedFont.entries.toList()) { font ->
            val isSelected = config.activeCuratedFont == font
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectCuratedFont(font) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = font.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font.fontFamily,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "The quick brown fox jumps over the lazy dog",
                            fontSize = 10.5.sp,
                            fontFamily = font.fontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Imported Fonts Slots (Max 2)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Imported Custom Fonts (Max 2)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${config.customFontSlots.size}/2 Imported",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        listOf(1, 2).forEach { slotIndex ->
            item {
                val slot = config.customFontSlots.find { it.slotIndex == slotIndex }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (slot != null) "Slot $slotIndex: ${slot.name}" else "Slot $slotIndex: [Empty Slot]",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (slot != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (slot != null) slot.filePath else "Supports TTF and OTF files only",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (slot != null) {
                            IconButton(onClick = { onRemoveImportedFont(slotIndex) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onOpenImportFont(slotIndex) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Import", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Interface Text Scale Controls (0.85x to 1.25x)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "General Interface Text Scaling (${(config.interfaceTextScale * 100).toInt()}%)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("85%" to 0.85f, "100%" to 1.0f, "115%" to 1.15f, "125%" to 1.25f).forEach { (label, scale) ->
                    val isSelected = (config.interfaceTextScale - scale).let { it >= -0.01f && it <= 0.01f }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetTextScale(scale) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Message Text Size Controls (Separate from General UI)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Message Bubble Text Size (${config.messageTextSizeSp.toInt()}sp)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Small (13sp)" to 13f, "Normal (14sp)" to 14f, "Large (16sp)" to 16f, "XL (18sp)" to 18f).forEach { (label, size) ->
                    val isSelected = config.messageTextSizeSp == size
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetMessageTextSize(size) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = label,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display & Motion Section: Density, Corner Radius, and Animation Levels.
 */
@Composable
private fun DisplayAndMotionSection(
    config: PersonalizationConfig,
    onSetDensity: (UiDensity) -> Unit,
    onSetCornerRadius: (CornerRadiusPreset) -> Unit,
    onSetAnimationLevel: (AnimationLevel) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Density Presets
        item {
            Text("Layout Density", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Adjusts padding and spacing density across lists and cards", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UiDensity.entries.forEach { density ->
                    val isSelected = config.density == density
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSetDensity(density) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = density.displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(density.paddingMultiplier * 100).toInt()}% padding",
                                fontSize = 9.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Corner Radius Presets
        item {
            Text("Corner Radius", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Select edge curve radius applied across cards, buttons, and bubbles", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CornerRadiusPreset.entries.forEach { corner ->
                    val isSelected = config.cornerRadius == corner
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(corner.radiusDp))
                            .clickable { onSetCornerRadius(corner) },
                        shape = RoundedCornerShape(corner.radiusDp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = corner.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Animation Levels
        item {
            Text("Motion & Animation Levels", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Controls transition durations and spring physics", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimationLevel.entries.forEach { anim ->
                    val isSelected = config.animationLevel == anim
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSetAnimationLevel(anim) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = anim.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chat Appearance Section: 5 Presets, Personal DM Bubble, Strict Separation from Public Rooms.
 */
@Composable
private fun ChatAppearanceSection(
    config: PersonalizationConfig,
    onSelectPreset: (ChatAppearancePreset) -> Unit,
    onSetDmBubbleColor: (Color) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Strict Separation Notice Banner
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Public room messages maintain neutral theme styling. Custom DM bubble colors apply strictly to your personal direct messages.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 5 Ready-made Chat Appearance Presets
        item {
            Text("Chat Appearance Presets (5)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        items(ChatAppearancePreset.entries.toList()) { preset ->
            val isSelected = config.chatAppearancePreset == preset
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectPreset(preset) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preset.dmBubbleColor))
                        Text(
                            text = preset.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Custom Personal DM Bubble Color Picker
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Personal DM Bubble Color", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Choose outgoing message bubble color for private 1-on-1 chats", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            val curatedBubbleColors = listOf(
                Color(0xFF6366F1), // Indigo
                Color(0xFF2AABEE), // Telegram Sky
                Color(0xFF10B981), // Emerald
                Color(0xFFE85D9E), // Rose Blush
                Color(0xFF8B5CF6), // Purple Obsidian
                Color(0xFFF97316), // Warm Orange
                Color(0xFF14B8A6), // Teal
                Color(0xFFE11D48)  // Crimson
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                curatedBubbleColors.forEach { color ->
                    val isSelected = config.personalDmBubbleColor == color
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (isSelected) 2.5.dp else 0.dp, Color.White, CircleShape)
                            .clickable { onSetDmBubbleColor(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sound & Haptics Section: 4 Sound Profiles + 2 Haptic Intensity Levels.
 */
@Composable
private fun AudioAndHapticsSection(
    config: PersonalizationConfig,
    onToggleSound: (Boolean) -> Unit,
    onSetSoundProfile: (SoundProfile) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onSetHapticIntensity: (HapticIntensity) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // UI Sound Effects Master Switch
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("UI Sound Effects", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("Enable acoustic feedback for taps, sends, and moves", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.soundEffectsEnabled,
                        onCheckedChange = onToggleSound
                    )
                }
            }
        }

        // 4 Sound Profiles
        item {
            Text("Sound Profile (4)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        items(SoundProfile.entries.toList()) { profile ->
            val isSelected = config.soundProfile == profile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onSetSoundProfile(profile)
                        PoliteSoundSynthesizer.playPopSound()
                    },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = profile.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            onSetSoundProfile(profile)
                            PoliteSoundSynthesizer.playPoliteSendSound()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Preview Tone", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Haptic Feedback Master Switch
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("Vibrational haptic motor responses on gestures", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.hapticFeedbackEnabled,
                        onCheckedChange = onToggleHaptics
                    )
                }
            }
        }

        // 2 Haptic Intensity Levels
        item {
            Text("Haptic Intensity", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HapticIntensity.entries.forEach { intensity ->
                    val isSelected = config.hapticIntensity == intensity
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSetHapticIntensity(intensity)
                                if (intensity == HapticIntensity.CRISP_GENTLE) {
                                    EchoHapticManager.getInstance(context).performPixelTick(view)
                                } else {
                                    EchoHapticManager.getInstance(context).performPixelHeavyClick(view)
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = intensity.displayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Performance Modes Section: Automatic, Balanced, and Battery Saver.
 */
@Composable
private fun PerformanceSection(
    config: PersonalizationConfig,
    onSetMode: (PerformanceMode) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Performance Profiles (3)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Optimizes frame rates, wave visualizers, and battery usage",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(PerformanceMode.entries.toList()) { mode ->
            val isSelected = config.performanceMode == mode
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSetMode(mode) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mode.description,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Comprehensive Custom Theme Editor Dialog.
 * Allows editing the 12 permitted semantic color roles with real-time WCAG contrast validation.
 */
@Composable
fun ThemeEditorDialog(
    theme: CustomTheme,
    onDismiss: () -> Unit,
    onSave: (CustomTheme) -> Unit
) {
    var name by remember { mutableStateOf(theme.name) }
    var primary by remember { mutableStateOf(theme.colors.primary) }
    var secondary by remember { mutableStateOf(theme.colors.secondary) }
    var accent by remember { mutableStateOf(theme.colors.accent) }
    var mainBg by remember { mutableStateOf(theme.colors.mainBackground) }
    var secBg by remember { mutableStateOf(theme.colors.secondaryBackground) }
    var cardSurface by remember { mutableStateOf(theme.colors.cardSurface) }
    var primaryText by remember { mutableStateOf(theme.colors.primaryText) }
    var secondaryText by remember { mutableStateOf(theme.colors.secondaryText) }
    var borders by remember { mutableStateOf(theme.colors.bordersDividers) }
    var highlight by remember { mutableStateOf(theme.colors.selectionHighlight) }
    var dmBubble by remember { mutableStateOf(theme.colors.personalDmBubble) }
    var publicAccent by remember { mutableStateOf(theme.colors.publicRoomAccent) }
    var isDark by remember { mutableStateOf(theme.isDark) }

    // Live contrast calculation
    val textContrastRatio = remember(primaryText, mainBg) {
        ContrastProtector.getContrastRatio(primaryText, mainBg)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Custom Theme Editor", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("12 Semantic Color Roles • Contrast Protected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                // Theme Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview & Contrast Status Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = mainBg,
                    border = BorderStroke(1.dp, borders),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Preview: $name",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (textContrastRatio >= 4.0f) Color(0xFF10B981) else Color(0xFFF59E0B)
                            ) {
                                Text(
                                    text = String.format("WCAG: %.1f:1 %s", textContrastRatio, if (textContrastRatio >= 4.0f) "✓ PASS" else "AUTO-SAFE"),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Sample Surface Card with DM bubble and Public Room Accent
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cardSurface,
                            border = BorderStroke(0.5.dp, borders),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Card Surface", fontSize = 10.sp, color = secondaryText)
                                Surface(shape = RoundedCornerShape(6.dp), color = dmBubble) {
                                    Text("DM Bubble", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = publicAccent.copy(alpha = 0.2f)) {
                                    Text("Public Accent", fontSize = 9.sp, color = publicAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                // 12 Semantic Color Roles Grid
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colorRoles = listOf(
                        "1. Primary Accent" to primary,
                        "2. Secondary Color" to secondary,
                        "3. Vibrant Accent" to accent,
                        "4. Main Background" to mainBg,
                        "5. Secondary Background" to secBg,
                        "6. Card / Surface" to cardSurface,
                        "7. Primary Text" to primaryText,
                        "8. Secondary Text" to secondaryText,
                        "9. Borders / Dividers" to borders,
                        "10. Selection / Highlight" to highlight,
                        "11. Personal DM Bubble" to dmBubble,
                        "12. Public Room Accent" to publicAccent
                    )

                    colorRoles.forEachIndexed { index, (roleName, currentColor) ->
                        item {
                            ColorRoleRow(
                                roleName = roleName,
                                currentColor = currentColor,
                                onColorSelected = { newColor ->
                                    when (index) {
                                        0 -> primary = newColor
                                        1 -> secondary = newColor
                                        2 -> accent = newColor
                                        3 -> mainBg = newColor
                                        4 -> secBg = newColor
                                        5 -> cardSurface = newColor
                                        6 -> primaryText = newColor
                                        7 -> secondaryText = newColor
                                        8 -> borders = newColor
                                        9 -> highlight = newColor
                                        10 -> dmBubble = newColor
                                        11 -> publicAccent = newColor
                                    }
                                }
                            )
                        }
                    }
                }

                // Save Action Button
                Button(
                    onClick = {
                        val rawColors = SemanticColorRoles(
                            primary = primary,
                            secondary = secondary,
                            accent = accent,
                            mainBackground = mainBg,
                            secondaryBackground = secBg,
                            cardSurface = cardSurface,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            bordersDividers = borders,
                            selectionHighlight = highlight,
                            personalDmBubble = dmBubble,
                            publicRoomAccent = publicAccent
                        )
                        val protectedColors = ContrastProtector.protectPalette(rawColors)
                        onSave(
                            theme.copy(
                                name = name.trim().ifBlank { "Custom Theme" },
                                colors = protectedColors,
                                isDark = isDark
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_custom_theme_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Custom Theme", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Compact row to choose from curated harmonious swatches for each semantic role.
 */
@Composable
private fun ColorRoleRow(
    roleName: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val swatches = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF38BDF8), // Sky Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFE85D9E), // Rose
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Violet
        Color(0xFF0F172A), // Slate 900
        Color(0xFF1E293B), // Slate 800
        Color(0xFFF8FAFC), // Slate 50
        Color(0xFF94A3B8)  // Slate 400
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = roleName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(currentColor).border(0.5.dp, Color.White, CircleShape))
                    Text(text = with(SemanticColorRoles) { currentColor.toHex() }, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Quick swatch palette
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                swatches.forEach { color ->
                    val isSelected = currentColor == color
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f), CircleShape)
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}
