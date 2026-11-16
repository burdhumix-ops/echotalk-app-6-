package co.znxdnd.echotalk.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses and downscales user-selected images on Dispatchers.IO before uploading to
 * Supabase Free Storage (`profile-images` bucket, 1 MB strict limit).
 * Targets <= 720px longest side and <= 300 KB payload to protect free-tier egress & storage.
 */
object ImageCompressor {

    const val MAX_DIMENSION_PX = 720
    const val TARGET_MAX_BYTES = 300 * 1024 // 300 KB
    const val HARD_MAX_BYTES = 1024 * 1024 - 1024 // < 1 MB bucket cap

    private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    data class CompressedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val extension: String,
        val width: Int,
        val height: Int
    )

    fun isSupportedMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return true // Some URIs omit MIME until decoded
        return ALLOWED_MIME_TYPES.contains(mimeType.lowercase())
    }

    fun calculateTargetDimensions(width: Int, height: Int, maxDim: Int = MAX_DIMENSION_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return maxDim to maxDim
        val longest = max(width, height)
        if (longest <= maxDim) return width to height
        val scale = maxDim.toFloat() / longest.toFloat()
        val newW = (width * scale).roundToInt().coerceAtLeast(1)
        val newH = (height * scale).roundToInt().coerceAtLeast(1)
        return newW to newH
    }

    suspend fun compressUri(context: Context, uri: Uri): Result<CompressedImage> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri)
            if (!isSupportedMimeType(detectedMime)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported image format ($detectedMime). Please choose a JPEG, PNG, or WebP image.")
                )
            }

            // Pass 1: Read bounds only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open selected image."))

            val srcW = boundsOptions.outWidth
            val srcH = boundsOptions.outHeight
            if (srcW <= 0 || srcH <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Selected file is not a valid image."))
            }

            var inSampleSize = 1
            while ((srcW / inSampleSize) > MAX_DIMENSION_PX * 2 || (srcH / inSampleSize) > MAX_DIMENSION_PX * 2) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Failed to decode image."))

            val (targetW, targetH) = calculateTargetDimensions(decoded.width, decoded.height, MAX_DIMENSION_PX)
            val scaled = if (decoded.width != targetW || decoded.height != targetH) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val outputBytes = compressBitmapToLimit(scaled)
            val finalW = scaled.width
            val finalH = scaled.height
            scaled.recycle()

            if (outputBytes.size > HARD_MAX_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("Compressed image still exceeds 1 MB limit.")
                )
            }

            Result.success(
                CompressedImage(
                    bytes = outputBytes,
                    mimeType = "image/jpeg",
                    extension = "jpg",
                    width = finalW,
                    height = finalH
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun compressBitmapToLimit(
        bitmap: Bitmap,
        targetMaxBytes: Int = TARGET_MAX_BYTES
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        var quality = 86
        do {
            baos.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            quality -= 8
        } while (baos.size() > targetMaxBytes && quality >= 30)
        return baos.toByteArray()
    }
}
