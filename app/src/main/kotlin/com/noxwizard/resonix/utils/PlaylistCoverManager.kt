package com.noxwizard.resonix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility class for managing playlist custom cover images
 */
object PlaylistCoverManager {
    private const val COVER_DIR = "playlist_covers"
    private const val COVER_SIZE = 512
    
    /**
     * Get the directory for storing playlist covers
     */
    private fun getCoverDirectory(context: Context): File {
        val dir = File(context.filesDir, COVER_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Save a cropped image as playlist cover
     * @param context Application context
     * @param sourceUri URI of the cropped image
     * @param playlistId Playlist ID (used for filename)
     * @return File path to the saved cover, or null if failed
     */
    fun saveCover(context: Context, sourceUri: Uri, playlistId: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) return null
            
            // Scale to consistent size
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, COVER_SIZE, COVER_SIZE, true)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }
            
            // Generate unique filename
            val filename = "cover_${playlistId}_${UUID.randomUUID()}.jpg"
            val coverFile = File(getCoverDirectory(context), filename)
            
            // Delete old covers for this playlist
            deleteOldCovers(context, playlistId)
            
            // Save new cover
            FileOutputStream(coverFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scaledBitmap.recycle()
            
            coverFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Delete old covers for a specific playlist
     */
    private fun deleteOldCovers(context: Context, playlistId: String) {
        val coverDir = getCoverDirectory(context)
        coverDir.listFiles()?.filter { 
            it.name.startsWith("cover_${playlistId}_") 
        }?.forEach { 
            it.delete() 
        }
    }
    
    /**
     * Delete cover for a specific playlist
     */
    fun deleteCover(context: Context, coverPath: String?) {
        if (coverPath.isNullOrEmpty()) return
        try {
            File(coverPath).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Check if a cover file exists
     */
    fun coverExists(coverPath: String?): Boolean {
        if (coverPath.isNullOrEmpty()) return false
        return File(coverPath).exists()
    }
}
