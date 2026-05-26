package com.noxwizard.resonix.lyrics.engine

import android.util.LruCache
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.LyricsEntity
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.SyncType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsCacheManager @Inject constructor(
    private val databaseDao: MusicDatabase,
) {
    private val memoryCache = LruCache<String, LyricsDocument>(10)

    private fun LyricsDocument.toJson(): String = JSONObject().apply {
        put("rawText", rawText)
        put("providerName", providerName)
        put("providerCategory", providerCategory.name)
        put("syncType", syncType.name)
        put("copyright", copyright ?: JSONObject.NULL)
        // lines are not serialized here — rawText is sufficient for re-parse on cold start
    }.toString()

    private fun String.toDocument(): LyricsDocument? = runCatching {
        val obj = JSONObject(this)
        LyricsDocument(
            rawText = LyricsPayloadSanitizer.sanitize(obj.getString("rawText")),
            lines = emptyList(), // will be re-resolved if needed; cache hit returns from memory
            providerName = obj.getString("providerName"),
            providerCategory = LyricsProviderCategory.valueOf(obj.getString("providerCategory")),
            syncType = SyncType.valueOf(obj.getString("syncType")),
            copyright = if (obj.isNull("copyright")) null else obj.getString("copyright"),
        )
    }.getOrNull()

    suspend fun getLyrics(trackId: String): LyricsDocument? = withContext(Dispatchers.IO) {
        // 1. Check memory cache
        memoryCache.get(trackId)?.let { return@withContext it }

        // 2. Check database
        val entity = databaseDao.lyrics(trackId).firstOrNull() ?: return@withContext null
        if (entity.lyrics == LyricsEntity.LYRICS_NOT_FOUND) {
            return@withContext null
        }

        // Deserialize
        val document = entity.lyrics.toDocument()
        if (document != null) {
            if (document.rawText.trimStart().startsWith("{\"rawText\":")) {
                databaseDao.delete(LyricsEntity(id = trackId, lyrics = ""))
                return@withContext null
            }
            memoryCache.put(trackId, document)
            return@withContext document
        }
        return@withContext null
    }

    suspend fun putLyrics(trackId: String, document: LyricsDocument) = withContext(Dispatchers.IO) {
        // 1. Update memory cache
        memoryCache.put(trackId, document)

        // 2. Update database
        val json = document.toJson()
        databaseDao.upsert(LyricsEntity(id = trackId, lyrics = json))
    }

    suspend fun markNotFound(trackId: String) = withContext(Dispatchers.IO) {
        databaseDao.upsert(LyricsEntity(id = trackId, lyrics = LyricsEntity.LYRICS_NOT_FOUND))
    }
}
