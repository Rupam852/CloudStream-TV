package com.cloudstream.tv.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DriveRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "CloudStreamTVPrefs"
        private const val KEY_DRIVE_LINKS = "saved_drive_links"
        private const val KEY_RECENTLY_VIEWED = "recently_viewed_files"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_GRID_VIEW = "grid_view_enabled"
        private const val KEY_API_KEY = "google_api_key"
        private const val KEY_LAST_FOLDER_ID = "last_selected_folder_id"
        private const val KEY_OAUTH_ACCESS_TOKEN = "oauth_access_token"
        private const val KEY_OAUTH_REFRESH_TOKEN = "oauth_refresh_token"
        private const val KEY_OAUTH_EXPIRY = "oauth_expiry"
        private const val KEY_OAUTH_EMAIL = "oauth_email"
        private const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        private const val KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret"
    }

    // --- Saved Folder Links ---
    fun getSavedLinks(): List<DriveLink> {
        val json = prefs.getString(KEY_DRIVE_LINKS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DriveLink>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLink(link: DriveLink) {
        val current = getSavedLinks().toMutableList()
        // Remove duplicate ID if exists
        current.removeAll { it.id == link.id }
        current.add(0, link)
        prefs.edit().putString(KEY_DRIVE_LINKS, gson.toJson(current)).apply()
    }

    fun deleteLink(id: String) {
        val current = getSavedLinks().toMutableList()
        current.removeAll { it.id == id }
        prefs.edit().putString(KEY_DRIVE_LINKS, gson.toJson(current)).apply()
        
        // Clear active folder ID if it was deleted
        if (getLastSelectedFolderId() == id) {
            setLastSelectedFolderId(null)
        }
    }

    // --- Recently Viewed ---
    fun getRecentlyViewed(): List<DriveFile> {
        val json = prefs.getString(KEY_RECENTLY_VIEWED, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DriveFile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToRecentlyViewed(file: DriveFile) {
        val current = getRecentlyViewed().toMutableList()
        // Remove if already exists to push to front
        current.removeAll { it.id == file.id }
        current.add(0, file)
        
        // Cap list size to 20
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        
        prefs.edit().putString(KEY_RECENTLY_VIEWED, gson.toJson(current)).apply()
    }

    fun clearRecentlyViewed() {
        prefs.edit().remove(KEY_RECENTLY_VIEWED).apply()
    }

    // --- Preferences & Settings ---
    fun isDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, true) // default true
    }

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun isGridView(): Boolean {
        return prefs.getBoolean(KEY_GRID_VIEW, false) // default false
    }

    fun setGridView(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_VIEW, enabled).apply()
    }

    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }

    fun setApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            prefs.edit().remove(KEY_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
        }
    }

    fun getLastSelectedFolderId(): String? {
        val links = getSavedLinks()
        val savedId = prefs.getString(KEY_LAST_FOLDER_ID, null)
        
        // Fallback to first link if the saved ID is not in links list
        if (savedId != null && links.any { it.id == savedId }) {
            return savedId
        }
        return links.firstOrNull()?.id
    }

    fun setLastSelectedFolderId(id: String?) {
        if (id == null) {
            prefs.edit().remove(KEY_LAST_FOLDER_ID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_FOLDER_ID, id).apply()
        }
    }

    // --- OAuth 2.0 Token Management ---
    suspend fun getAccessToken(): String? {
        val accessToken = prefs.getString(KEY_OAUTH_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_OAUTH_REFRESH_TOKEN, null)
        val expiry = prefs.getLong(KEY_OAUTH_EXPIRY, 0L)
        
        if (accessToken == null) return null
        
        // Refresh token if it will expire within 5 minutes (300,000 ms)
        val isExpired = System.currentTimeMillis() + 300000 > expiry
        
        if (isExpired && refreshToken != null) {
            val clientId = getOAuthClientId()
            val clientSecret = getOAuthClientSecret()
            val tokenResponse = com.cloudstream.tv.network.GoogleDriveClient.refreshAccessToken(clientId, clientSecret, refreshToken)
            if (tokenResponse != null && tokenResponse.access_token != null) {
                val newAccessToken = tokenResponse.access_token
                val newExpiry = System.currentTimeMillis() + (tokenResponse.expires_in ?: 3600) * 1000
                saveOAuthTokens(newAccessToken, refreshToken, newExpiry, getOAuthEmail())
                return newAccessToken
            } else {
                clearOAuthTokens()
                return null
            }
        }
        
        return accessToken
    }

    fun saveOAuthTokens(accessToken: String, refreshToken: String?, expiry: Long, email: String?) {
        val editor = prefs.edit()
            .putString(KEY_OAUTH_ACCESS_TOKEN, accessToken)
            .putLong(KEY_OAUTH_EXPIRY, expiry)
        if (refreshToken != null) {
            editor.putString(KEY_OAUTH_REFRESH_TOKEN, refreshToken)
        }
        if (email != null) {
            editor.putString(KEY_OAUTH_EMAIL, email)
        }
        editor.apply()
    }

    fun clearOAuthTokens() {
        prefs.edit()
            .remove(KEY_OAUTH_ACCESS_TOKEN)
            .remove(KEY_OAUTH_REFRESH_TOKEN)
            .remove(KEY_OAUTH_EXPIRY)
            .remove(KEY_OAUTH_EMAIL)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_OAUTH_REFRESH_TOKEN, null) != null
    }

    fun getOAuthEmail(): String? {
        return prefs.getString(KEY_OAUTH_EMAIL, null)
    }

    fun getOAuthClientId(): String {
        return com.cloudstream.tv.network.GoogleDriveClient.DEFAULT_CLIENT_ID
    }

    fun setOAuthClientId(clientId: String?) {
        // No-op: Custom credential configuration is disabled.
    }

    fun getOAuthClientSecret(): String {
        return com.cloudstream.tv.network.GoogleDriveClient.DEFAULT_CLIENT_SECRET
    }

    fun setOAuthClientSecret(secret: String?) {
        // No-op: Custom credential configuration is disabled.
    }
}
