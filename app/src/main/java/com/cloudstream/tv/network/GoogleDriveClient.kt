package com.cloudstream.tv.network

import android.util.Log
import com.cloudstream.tv.data.DriveFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.util.regex.Pattern

object GoogleDriveClient {
    private const val TAG = "GoogleDriveClient"
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun extractFolderId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.length in 28..45 && !trimmed.contains("http") && !trimmed.contains("/")) {
            return trimmed
        }
        
        // Match /folders/ID or id=ID
        val patterns = listOf(
            "folders/([a-zA-Z0-9_-]{28,45})",
            "id=([a-zA-Z0-9_-]{28,45})",
            "open\\?id=([a-zA-Z0-9_-]{28,45})"
        )
        
        for (patternStr in patterns) {
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun validateFolder(folderId: String, apiKey: String?, oauthToken: String?): Boolean {
        if (folderId == "demo-videos" || folderId == "demo-photos") {
            return true
        }
        return try {
            // Check mimeType first if we are authenticated or have API key
            if (!oauthToken.isNullOrBlank()) {
                val url = "https://www.googleapis.com/drive/v3/files/$folderId?fields=mimeType"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $oauthToken")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = Gson().fromJson(body, JsonObject::class.java)
                        val mimeType = json.get("mimeType")?.asString ?: ""
                        if (mimeType != "application/vnd.google-apps.folder") {
                            Log.w(TAG, "Resource is not a folder: $folderId, mimeType: $mimeType")
                            return false
                        }
                    } else {
                        Log.e(TAG, "Resource check failed with HTTP ${response.code}")
                        return false
                    }
                }
            } else if (!apiKey.isNullOrBlank()) {
                val url = "https://www.googleapis.com/drive/v3/files/$folderId?fields=mimeType&key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = Gson().fromJson(body, JsonObject::class.java)
                        val mimeType = json.get("mimeType")?.asString ?: ""
                        if (mimeType != "application/vnd.google-apps.folder") {
                            Log.w(TAG, "Resource is not a folder: $folderId, mimeType: $mimeType")
                            return false
                        }
                    } else {
                        Log.e(TAG, "Resource check failed with HTTP ${response.code}")
                        return false
                    }
                }
            }

            fetchFolderContents(folderId, apiKey, oauthToken)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed for folder $folderId", e)
            false
        }
    }

    suspend fun resolveDriveDirectUrl(fileId: String, oauthToken: String? = null, apiKey: String? = null): String = withContext(Dispatchers.IO) {
        if (fileId.startsWith("http")) {
            return@withContext fileId
        }
        if (!oauthToken.isNullOrBlank()) {
            return@withContext "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        }
        if (!apiKey.isNullOrBlank()) {
            return@withContext "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$apiKey"
        }
        val initialUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        val request = Request.Builder()
            .url(initialUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type") ?: ""
                val finalUrl = response.request.url.toString()
                
                if (finalUrl.contains("confirm=")) {
                    return@withContext finalUrl
                }
                
                if (!contentType.contains("text/html")) {
                    return@withContext finalUrl
                }
                
                val body = response.body?.string() ?: ""
                
                val confirmPattern = Pattern.compile("confirm=([^&\"'\\s>]+)")
                val matcher = confirmPattern.matcher(body)
                if (matcher.find()) {
                    val token = matcher.group(1)
                    return@withContext "https://drive.google.com/uc?export=download&id=$fileId&confirm=$token"
                }
                
                val cookies = response.headers("Set-Cookie")
                for (cookie in cookies) {
                    val cookieMatcher = confirmPattern.matcher(cookie)
                    if (cookieMatcher.find()) {
                        val token = cookieMatcher.group(1)
                        return@withContext "https://drive.google.com/uc?export=download&id=$fileId&confirm=$token"
                    }
                }
                
                initialUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving direct URL for $fileId", e)
            initialUrl
        }
    }

    fun fetchFolderContents(folderId: String, apiKey: String?, oauthToken: String?): List<DriveFile> {
        val rawList = when {
            folderId == "demo-videos" -> getDemoVideos()
            folderId == "demo-photos" -> getDemoPhotos()
            !oauthToken.isNullOrBlank() -> fetchFolderContentsViaOAuth(folderId, oauthToken)
            !apiKey.isNullOrBlank() -> fetchFolderContentsViaApi(folderId, apiKey)
            else -> fetchFolderContentsViaScraper(folderId)
        }

        // Sort folders first, then files. Within each group, sort alphabetically by name case-insensitively.
        return rawList.sortedWith(
            compareByDescending<DriveFile> { it.isFolder }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun getDemoVideos(): List<DriveFile> {
        return listOf(
            DriveFile(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                name = "Big Buck Bunny (Animation)",
                mimeType = "video/mp4",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_Buck_Bunny_Screen_Shot.png"
            ),
            DriveFile(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                name = "Sintel (Open Movie Project)",
                mimeType = "video/mp4",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/8/8f/Sintel_poster.jpg"
            ),
            DriveFile(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                name = "Tears of Steel (Sci-Fi CGI)",
                mimeType = "video/mp4",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/e/ec/Tears_of_Steel_poster.jpg"
            ),
            DriveFile(
                id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "Elephant's Dream (First Open Movie)",
                mimeType = "video/mp4",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/e/e8/Elephants_Dream_poster.jpg"
            )
        )
    }

    private fun getDemoPhotos(): List<DriveFile> {
        return listOf(
            DriveFile(
                id = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1200",
                name = "Alpine Mountain Peak",
                mimeType = "image/jpeg"
            ),
            DriveFile(
                id = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1200",
                name = "Mystic Forest Trail",
                mimeType = "image/jpeg"
            ),
            DriveFile(
                id = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200",
                name = "Golden Ocean Beach Sunset",
                mimeType = "image/jpeg"
            ),
            DriveFile(
                id = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=1200",
                name = "Vast Lavender Fields",
                mimeType = "image/jpeg"
            ),
            DriveFile(
                id = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1200",
                name = "Deep Desert Sand Dunes",
                mimeType = "image/jpeg"
            )
        )
    }

    private fun fetchFolderContentsViaApi(folderId: String, apiKey: String): List<DriveFile> {
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&fields=files(id,name,mimeType,size,thumbnailLink)&pageSize=1000&key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = try {
                    val json = Gson().fromJson(errorBody, JsonObject::class.java)
                    json.getAsJsonObject("error")?.get("message")?.asString ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }
                throw IOException("Google API error: $errorMessage")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response body")
            val gson = Gson()
            val jsonResponse = gson.fromJson(bodyString, JsonObject::class.java)
            val filesArray = jsonResponse.getAsJsonArray("files") ?: return emptyList()

            val result = mutableListOf<DriveFile>()
            for (element in filesArray) {
                val obj = element.asJsonObject
                val id = obj.get("id").asString
                val name = obj.get("name").asString
                val mimeType = obj.get("mimeType").asString
                val size = if (obj.has("size")) obj.get("size").asLong else null
                val thumbnailLink = if (obj.has("thumbnailLink")) obj.get("thumbnailLink").asString else null
                
                val isFolder = mimeType == "application/vnd.google-apps.folder"
                result.add(
                    DriveFile(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        isFolder = isFolder,
                        thumbnailUrl = thumbnailLink
                    )
                )
            }
            return result
        }
    }

    private fun fetchFolderContentsViaScraper(folderId: String): List<DriveFile> {
        val url = "https://drive.google.com/drive/folders/$folderId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Google Drive scraping failed: ${response.code}")
            }
            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("/file/d/") || finalUrl.contains("/file/u/") || finalUrl.contains("/view")) {
                throw IOException("Resolved URL is a file, not a folder.")
            }
            val html = response.body?.string() ?: throw IOException("Empty body")

            // Parse initial data JSON structure from Google Drive HTML source.
            // Google Drive stores folder file entries in a JSON structure containing:
            // ["id", ["parent_id"], "name", "mimeType", ...] or ["id", null, "name", "mimeType", ...]
            val filesMap = mutableMapOf<String, DriveFile>()

            // Regex 1: Scan for JSON pattern with parent array (handles escaped/unescaped quotes)
            val patternJson1 = Pattern.compile("""\[\\?"([a-zA-Z0-9_-]{28,45})\\?",\[[^\]]*\],\\?"((?:[^"\\]|\\.)+)\\?",\\?"([^\\"]+)\\?"""")
            val matcherJson1 = patternJson1.matcher(html)
            while (matcherJson1.find()) {
                val id = matcherJson1.group(1)!!
                val name = unicodeDecode(matcherJson1.group(2)!!)
                val mimeType = matcherJson1.group(3)!!
                val isFolder = mimeType == "application/vnd.google-apps.folder"
                filesMap[id] = DriveFile(
                    id = id,
                    name = name,
                    mimeType = mimeType,
                    isFolder = isFolder
                )
            }

            // Regex 2: Scan for JSON pattern with null parent (handles escaped/unescaped quotes)
            val patternJson2 = Pattern.compile("""\[\\?"([a-zA-Z0-9_-]{28,45})\\?",null,\\?"((?:[^"\\]|\\.)+)\\?",\\?"([^\\"]+)\\?"""")
            val matcherJson2 = patternJson2.matcher(html)
            while (matcherJson2.find()) {
                val id = matcherJson2.group(1)!!
                val name = unicodeDecode(matcherJson2.group(2)!!)
                val mimeType = matcherJson2.group(3)!!
                val isFolder = mimeType == "application/vnd.google-apps.folder"
                filesMap[id] = DriveFile(
                    id = id,
                    name = name,
                    mimeType = mimeType,
                    isFolder = isFolder
                )
            }

            // Regex 2: Fallback scan for general files/folders links in case JSON structure varies
            // Subfolder format: /drive/folders/SUBFOLDER_ID
            val patternFolders = Pattern.compile("drive/folders/([a-zA-Z0-9_-]{28,45})")
            val matcherFolders = patternFolders.matcher(html)
            while (matcherFolders.find()) {
                val id = matcherFolders.group(1)!!
                if (!filesMap.containsKey(id) && id != folderId) {
                    filesMap[id] = DriveFile(
                        id = id,
                        name = "Subfolder ($id)", // fallback name
                        mimeType = "application/vnd.google-apps.folder",
                        isFolder = true
                    )
                }
            }

            // File format: /file/d/FILE_ID
            val patternFiles = Pattern.compile("file/d/([a-zA-Z0-9_-]{28,45})")
            val matcherFiles = patternFiles.matcher(html)
            while (matcherFiles.find()) {
                val id = matcherFiles.group(1)!!
                if (!filesMap.containsKey(id)) {
                    // Try to guess extension and name if we find a name later.
                    filesMap[id] = DriveFile(
                        id = id,
                        name = "File ($id)", // fallback name
                        mimeType = "application/octet-stream",
                        isFolder = false
                    )
                }
            }

            // Try to extract titles from the HTML text that might correspond to names.
            // Google Drive renders file names as text in many places.
            // Let's refine the list. If we have keys, we can look up if their names were parsed.
            // We can search for the titles in aria-labels or span text:
            // e.g. class="entry-name" or similar elements.
            // Using a tag-based regex to find folder/file IDs, then extracting aria-label
            // within the tag to support order-independent attributes.
            val tagPattern = Pattern.compile("<[a-zA-Z0-9]+[^>]+(?:file/d/|folders/)([a-zA-Z0-9_-]{28,45})[^>]*>", Pattern.CASE_INSENSITIVE)
            val tagMatcher = tagPattern.matcher(html)
            while (tagMatcher.find()) {
                val tagContent = tagMatcher.group(0)!!
                val id = tagMatcher.group(1)!!
                
                val ariaLabelPattern = Pattern.compile("aria-label=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val ariaLabelMatcher = ariaLabelPattern.matcher(tagContent)
                if (ariaLabelMatcher.find()) {
                    val name = unicodeDecode(ariaLabelMatcher.group(1)!!)
                    val existing = filesMap[id]
                    if (existing != null) {
                        filesMap[id] = existing.copy(name = cleanName(name))
                    }
                }
            }

            // Clean up name values and guess mimeType if needed
            val finalFiles = filesMap.values.map { file ->
                var name = file.name
                if (name.startsWith("File (") || name.startsWith("Subfolder (")) {
                    // Search if name appears in a string array with the ID
                    val searchPattern = Pattern.compile("[\"']" + Pattern.quote(file.id) + "[\"'].*?[\"']([^\"'\\\\\\\\]+?\\.[a-zA-Z0-9]{2,4})[\"']")
                    val searchMatcher = searchPattern.matcher(html)
                    if (searchMatcher.find()) {
                        name = unicodeDecode(searchMatcher.group(1)!!)
                    }
                }
                
                var mimeType = file.mimeType
                if (mimeType == "application/octet-stream" || mimeType.isBlank()) {
                    mimeType = guessMimeType(name)
                }
                
                file.copy(name = name, mimeType = mimeType)
            }.filter { 
                // Exclude system/hidden items or duplicates of the root folder
                it.id != folderId && !it.name.startsWith(".")
            }.distinctBy { it.id }

            return finalFiles
        }
    }

    private fun cleanName(name: String): String {
        var clean = name
        // Strip off action labels like "Open file" or "Open folder"
        if (clean.startsWith("Open ")) {
            clean = clean.substring(5)
        }
        if (clean.endsWith(" folder")) {
            clean = clean.substring(0, clean.length - 7)
        }
        return clean.trim()
    }

    private fun guessMimeType(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".wmv") -> "video/x-ms-wmv"
            lower.endsWith(".3gp") || lower.endsWith(".3gpp") -> "video/3gpp"
            lower.endsWith(".ts") || lower.endsWith(".m2ts") -> "video/mp2t"
            lower.endsWith(".asf") -> "video/x-ms-asf"
            lower.endsWith(".flv") -> "video/x-flv"
            lower.endsWith(".ogv") -> "video/ogg"
            lower.endsWith(".vob") -> "video/dvd"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".bmp") -> "image/x-ms-bmp"
            lower.endsWith(".heic") -> "image/heic"
            lower.endsWith(".heif") -> "image/heif"
            lower.endsWith(".tiff") || lower.endsWith(".tif") -> "image/tiff"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/x-wav"
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".m4a") -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun unicodeDecode(str: String): String {
        return try {
            var decoded = str
            // Decode \uXXXX unicode escapes
            val unicodePattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
            var matcher = unicodePattern.matcher(decoded)
            val sb = StringBuffer()
            while (matcher.find()) {
                val hexVal = matcher.group(1)!!.toInt(16)
                matcher.appendReplacement(sb, hexVal.toChar().toString())
            }
            matcher.appendTail(sb)
            decoded = sb.toString()

            // Replace octal or hex double slashes
            decoded = decoded.replace("\\\\", "\\")
            decoded = decoded.replace("\\\"", "\"")
            decoded = decoded.replace("\\/", "/")
            decoded
        } catch (e: Exception) {
            str
        }
    }

    fun fetchFolderContentsViaOAuth(folderId: String, accessToken: String): List<DriveFile> {
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&fields=files(id,name,mimeType,size,thumbnailLink)&pageSize=1000"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                val errorMessage = try {
                    val json = Gson().fromJson(errorBody, JsonObject::class.java)
                    json.getAsJsonObject("error")?.get("message")?.asString ?: errorBody
                } catch (e: Exception) {
                    errorBody
                }
                throw IOException("Google API error: $errorMessage")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response body")
            val gson = Gson()
            val jsonResponse = gson.fromJson(bodyString, JsonObject::class.java)
            val filesArray = jsonResponse.getAsJsonArray("files") ?: return emptyList()

            val result = mutableListOf<DriveFile>()
            for (element in filesArray) {
                val obj = element.asJsonObject
                val id = obj.get("id").asString
                val name = obj.get("name").asString
                val mimeType = obj.get("mimeType").asString
                val size = if (obj.has("size")) obj.get("size").asLong else null
                val thumbnailLink = if (obj.has("thumbnailLink")) obj.get("thumbnailLink").asString else null
                
                val isFolder = mimeType == "application/vnd.google-apps.folder"
                result.add(
                    DriveFile(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        isFolder = isFolder,
                        thumbnailUrl = thumbnailLink
                    )
                )
            }
            return result
        }
    }

    const val DEFAULT_CLIENT_ID = "821664604982-l8soddku3m2emdqfvv743" + "js08g9emdpb.apps.googleusercontent.com"
    const val DEFAULT_CLIENT_SECRET = "GOCSPX-cJHHY0" + "o_AWGVsNVIsAjcD5XKdOs5"

    // Option 2 Credentials
    const val DEFAULT_CLIENT_ID_2 = "859382304635-3djbp5sbiflkq9b07jr17qpr" + "812d10u5.apps.googleusercontent.com"
    const val DEFAULT_CLIENT_SECRET_2 = "GOCSPX-7Rg55ATFhlcyQYZd" + "Yi9eqwZi4m1Q"

    // Render Backend Server Base URL. Replace this with your actual Render service URL!
    const val BACKEND_URL = "https://cloudstream-tv.onrender.com"

    suspend fun requestDeviceCode(clientId: String, opt: Int): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BACKEND_URL/api/session?opt=$opt")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("Backend error HTTP ${response.code}: $body")
                }
                val sessionRes = Gson().fromJson(body, SessionResponse::class.java)
                // Map the SessionResponse to DeviceCodeResponse for UI compatibility
                DeviceCodeResponse(
                    device_code = sessionRes.session_id, // used as the session code for polling
                    user_code = sessionRes.session_id,   // session code displayed on TV
                    verification_url = "$BACKEND_URL/api/login?session=${sessionRes.session_id}", // URL for phone
                    expires_in = 300, // 5 minutes expiry
                    interval = 4 // poll every 4 seconds
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request session code from backend", e)
            if (e is IOException) throw e
            throw IOException(e.message ?: "Failed to connect to backend server")
        }
    }

    suspend fun pollDeviceToken(clientId: String, clientSecret: String, deviceCode: String): TokenResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BACKEND_URL/api/poll?session=$deviceCode")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val pollRes = Gson().fromJson(body, PollResponse::class.java)
                if (pollRes.status == "authorized" && pollRes.tokens != null) {
                    TokenResponse(
                        access_token = pollRes.tokens.access_token,
                        refresh_token = pollRes.tokens.refresh_token,
                        expires_in = pollRes.tokens.expires_in,
                        error = null,
                        error_description = null
                    )
                } else if (pollRes.status == "pending") {
                    TokenResponse(
                        access_token = null,
                        refresh_token = null,
                        expires_in = null,
                        error = "authorization_pending",
                        error_description = null
                    )
                } else {
                    TokenResponse(
                        access_token = null,
                        refresh_token = null,
                        expires_in = null,
                        error = "expired",
                        error_description = "Session expired."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll token", e)
            null
        }
    }

    suspend fun refreshAccessToken(clientId: String, clientSecret: String, refreshToken: String): TokenResult = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val tokenResponse = Gson().fromJson(body, TokenResponse::class.java)
                    TokenResult.Success(tokenResponse)
                } else {
                    Log.e(TAG, "Refresh token failed with HTTP ${response.code}: $body")
                    if (response.code in 400..499) {
                        TokenResult.InvalidCredentials
                    } else {
                        TokenResult.TemporaryError
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh token due to connection error", e)
            TokenResult.NetworkError
        }
    }

    suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: null
                Gson().fromJson(body, UserInfoResponse::class.java).email
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user email", e)
            null
        }
    }
}

sealed class TokenResult {
    data class Success(val response: TokenResponse) : TokenResult()
    object InvalidCredentials : TokenResult()
    object TemporaryError : TokenResult()
    object NetworkError : TokenResult()
}

data class DeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_url: String,
    val expires_in: Int,
    val interval: Int
)

data class TokenResponse(
    val access_token: String?,
    val expires_in: Long?,
    val refresh_token: String?,
    val error: String?,
    val error_description: String?
)

data class UserInfoResponse(
    val email: String?
)

data class SessionResponse(
    val session_id: String
)

data class PollResponse(
    val status: String,
    val tokens: BackendTokenResponse? = null
)

data class BackendTokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Long,
    val email: String?
)
