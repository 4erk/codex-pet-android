package com.fourerk.codexpet.settings

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.codexPetDataStore by preferencesDataStore(name = "codex_pet_settings")

class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    private object Keys {
        val sourcePackage = stringPreferencesKey("source_package")
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val petVisible = booleanPreferencesKey("pet_visible")
        val autoStart = booleanPreferencesKey("auto_start")
        val snapEnabled = booleanPreferencesKey("snap_enabled")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val animationSpeed = floatPreferencesKey("animation_speed")
        val petSizeDp = intPreferencesKey("pet_size_dp")
        val portraitX = intPreferencesKey("portrait_x")
        val portraitY = intPreferencesKey("portrait_y")
        val landscapeX = intPreferencesKey("landscape_x")
        val landscapeY = intPreferencesKey("landscape_y")
        val completedVisibleSeconds = intPreferencesKey("completed_visible_seconds")
        val panelPinned = booleanPreferencesKey("panel_pinned")
        val autoTaskBubblesEnabled = booleanPreferencesKey("auto_task_bubbles_enabled")
        val attentionBubblesEnabled = booleanPreferencesKey("attention_bubbles_enabled")
        val chatMessageBubblesEnabled = booleanPreferencesKey("chat_message_bubbles_enabled")
        val completionBubblesEnabled = booleanPreferencesKey("completion_bubbles_enabled")
        val longPressAction = stringPreferencesKey("long_press_action")
        val lastPetHash = stringPreferencesKey("last_pet_hash")
        val lastPetUpdatedAt = longPreferencesKey("last_pet_updated_at")
        val lastPetAssetSource = stringPreferencesKey("last_pet_asset_source")
        val lastPetSourcePackage = stringPreferencesKey("last_pet_source_package")
    }

    val updates = context.codexPetDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map(::mapSettings)

    val settings: StateFlow<AppSettings> = updates
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun readCurrent(): AppSettings = updates.first()

    suspend fun setSourcePackage(value: String) = update(Keys.sourcePackage, sanitizePackage(value))
    suspend fun setOverlayEnabled(value: Boolean) = update(Keys.overlayEnabled, value)
    suspend fun setPetVisible(value: Boolean) = update(Keys.petVisible, value)
    suspend fun setAutoStart(value: Boolean) = update(Keys.autoStart, value)
    suspend fun setSnapEnabled(value: Boolean) = update(Keys.snapEnabled, value)
    suspend fun setAnimationsEnabled(value: Boolean) = update(Keys.animationsEnabled, value)
    suspend fun setAnimationSpeed(value: Float) = update(Keys.animationSpeed, value.coerceIn(0.5f, 2f))
    suspend fun setPetSizeDp(value: Int) = update(Keys.petSizeDp, value.coerceIn(48, 160))
    suspend fun setCompletedVisibleSeconds(value: Int) = update(Keys.completedVisibleSeconds, value.coerceIn(0, 30))
    suspend fun setPanelPinned(value: Boolean) = update(Keys.panelPinned, value)
    suspend fun setAutoTaskBubblesEnabled(value: Boolean) = update(Keys.autoTaskBubblesEnabled, value)
    suspend fun setAttentionBubblesEnabled(value: Boolean) = update(Keys.attentionBubblesEnabled, value)
    suspend fun setChatMessageBubblesEnabled(value: Boolean) = update(Keys.chatMessageBubblesEnabled, value)
    suspend fun setCompletionBubblesEnabled(value: Boolean) = update(Keys.completionBubblesEnabled, value)
    suspend fun setLongPressAction(value: LongPressAction) = update(Keys.longPressAction, value.name)

    suspend fun savePosition(orientation: Int, x: Int, y: Int) {
        context.codexPetDataStore.edit { preferences ->
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                preferences[Keys.landscapeX] = x
                preferences[Keys.landscapeY] = y
            } else {
                preferences[Keys.portraitX] = x
                preferences[Keys.portraitY] = y
            }
        }
    }

    suspend fun updatePetMetadata(
        hash: String,
        updatedAt: Long,
        assetSource: String,
        sourcePackage: String?,
    ) {
        context.codexPetDataStore.edit { preferences ->
            preferences[Keys.lastPetHash] = hash
            preferences[Keys.lastPetUpdatedAt] = updatedAt
            preferences[Keys.lastPetAssetSource] = assetSource
            if (sourcePackage == null) {
                preferences.remove(Keys.lastPetSourcePackage)
            } else {
                preferences[Keys.lastPetSourcePackage] = sourcePackage
            }
        }
    }

    private suspend fun <T> update(key: Preferences.Key<T>, value: T) {
        context.codexPetDataStore.edit { it[key] = value }
    }

    private fun mapSettings(preferences: Preferences): AppSettings = AppSettings(
        sourcePackage = preferences[Keys.sourcePackage] ?: DEFAULT_CHATGPT_PACKAGE,
        overlayEnabled = preferences[Keys.overlayEnabled] ?: false,
        petVisible = preferences[Keys.petVisible] ?: true,
        autoStart = preferences[Keys.autoStart] ?: false,
        snapEnabled = preferences[Keys.snapEnabled] ?: true,
        animationsEnabled = preferences[Keys.animationsEnabled] ?: true,
        animationSpeed = preferences[Keys.animationSpeed] ?: 1f,
        petSizeDp = preferences[Keys.petSizeDp] ?: 72,
        portraitX = preferences[Keys.portraitX] ?: -1,
        portraitY = preferences[Keys.portraitY] ?: -1,
        landscapeX = preferences[Keys.landscapeX] ?: -1,
        landscapeY = preferences[Keys.landscapeY] ?: -1,
        completedVisibleSeconds = preferences[Keys.completedVisibleSeconds] ?: 5,
        panelPinned = preferences[Keys.panelPinned] ?: false,
        autoTaskBubblesEnabled = preferences[Keys.autoTaskBubblesEnabled] ?: true,
        attentionBubblesEnabled = preferences[Keys.attentionBubblesEnabled] ?: true,
        chatMessageBubblesEnabled = preferences[Keys.chatMessageBubblesEnabled] ?: true,
        completionBubblesEnabled = preferences[Keys.completionBubblesEnabled] ?: true,
        longPressAction = preferences[Keys.longPressAction]
            ?.let { runCatching { LongPressAction.valueOf(it) }.getOrNull() }
            ?: LongPressAction.MENU,
        lastPetHash = preferences[Keys.lastPetHash],
        lastPetUpdatedAt = preferences[Keys.lastPetUpdatedAt],
        lastPetAssetSource = preferences[Keys.lastPetAssetSource],
        lastPetSourcePackage = preferences[Keys.lastPetSourcePackage],
    )

    private fun sanitizePackage(value: String): String {
        val trimmed = value.trim()
        return if (PACKAGE_PATTERN.matches(trimmed)) trimmed else DEFAULT_CHATGPT_PACKAGE
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
