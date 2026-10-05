package com.cloudstream.tv.network

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object VideoCacheManager {
    private const val TAG = "VideoCacheManager"
    private var simpleCache: SimpleCache? = null
    
    // 350 MB disk cache limit for seamless video buffering without overloading TV storage
    private const val MAX_CACHE_BYTES: Long = 350L * 1024L * 1024L

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.applicationContext.cacheDir, "cloudstream_video_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            Log.d(TAG, "Initialized ExoPlayer SimpleCache in ${cacheDir.absolutePath} (Cap: 350MB)")
        }
        return simpleCache!!
    }

    /**
     * Builds a caching DataSource.Factory around any upstream factory (e.g. OkHttpDataSource).
     * This automatically writes downloaded chunks to disk and reads from cache when seeking.
     */
    fun buildCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Optional utility to clear video cache on low storage conditions.
     */
    @Synchronized
    fun clearCache() {
        try {
            simpleCache?.keys?.forEach { key ->
                simpleCache?.removeResource(key)
            }
            Log.i(TAG, "Video cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing video cache", e)
        }
    }
}
