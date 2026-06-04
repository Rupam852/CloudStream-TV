package com.cloudstream.tv.data

import java.io.Serializable

data class DriveLink(
    val id: String,
    val name: String,
    val url: String,
    val dateAdded: Long = System.currentTimeMillis()
) : Serializable
