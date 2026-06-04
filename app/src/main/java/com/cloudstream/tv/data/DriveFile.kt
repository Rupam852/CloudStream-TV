package com.cloudstream.tv.data

import java.io.Serializable

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val isFolder: Boolean = false,
    val thumbnailUrl: String? = null,
    val timestamp: Long? = null
) : Serializable {
    val streamUrl: String
        get() = if (id.startsWith("http://") || id.startsWith("https://")) id else "https://drive.google.com/uc?id=$id&export=download"

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    val isImage: Boolean
        get() = mimeType.startsWith("image/")
}
