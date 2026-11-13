package app.buildx.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Avatar image processing:
 * - Single-read raw bytes to prevent exhausted stream / double read bugs
 * - Decodes software ARGB_8888 bitmap with subsampling (longest edge <= 1440)
 * - Honors EXIF orientation (rotation & flips) via ByteArrayInputStream
 * - Crops square and scales to 512x512
 * - Compresses to JPEG (88% quality, <300 KB)
 */
object AvatarImageProcessor {

    private const val DEFAULT_TARGET_SIZE = 512
    private const val DEFAULT_JPEG_QUALITY = 88
    private const val MAX_FILE_SIZE_BYTES = 300 * 1024 // 300 KB

    /**
     * Decode image dimensions and compute power-of-2 sample size to prevent OOM.
     */
    fun calculateInSampleSize(origWidth: Int, origHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var width = origWidth
        var height = origHeight
        while (width / 2 >= reqWidth || height / 2 >= reqHeight) {
            inSampleSize *= 2
            width /= 2
            height /= 2
        }
        return inSampleSize
    }

    /**
     * Read EXIF orientation matrix from raw bytes.
     */
    fun getExifOrientationMatrix(bytes: ByteArray): Matrix {
        val matrix = Matrix()
        try {
            ByteArrayInputStream(bytes).use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                }
            }
        } catch (_: Throwable) {
            // Identity matrix on error
        }
        return matrix
    }

    /**
     * Read EXIF orientation matrix from Uri stream (reads stream once into bytes).
     */
    fun getExifOrientationMatrix(context: Context, uri: Uri): Matrix {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        } ?: return Matrix()
        return getExifOrientationMatrix(bytes)
    }

    /**
     * Decode photo safely from in-memory ByteArray into software ARGB_8888 Bitmap:
     * 1. Measures bounds using inJustDecodeBounds = true
     * 2. Computes inSampleSize so longest edge <= maxDimension (default 1440)
     * 3. Decodes into software ARGB_8888 bitmap with inMutable = true
     * 4. Applies EXIF orientation Matrix so the bitmap is upright
     */
    fun decodeSubsampledBitmap(
        rawBytes: ByteArray,
        maxDimension: Int = 1440
    ): Bitmap? {
        try {
            if (rawBytes.isEmpty()) return null

            // 1. Measure dimensions
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute sample size so longest edge <= maxDimension
            val maxEdge = maxOf(origWidth, origHeight)
            var sampleSize = 1
            while (maxEdge / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // 3. Decode software ARGB_8888 bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: return null

            // 4. Apply EXIF orientation
            val matrix = getExifOrientationMatrix(rawBytes)
            return if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (oriented != decoded) {
                    decoded.recycle()
                }
                oriented
            } else {
                decoded
            }
        } catch (_: Throwable) {
            return null
        }
    }

    /**
     * Decode photo from Uri: reads stream once into ByteArray, then decodes safely.
     */
    fun decodeSubsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1440
    ): Bitmap? {
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        } ?: return null
        return decodeSubsampledBitmap(rawBytes, maxDimension)
    }

    /**
     * Crop given square region and scale to 512x512 JPEG (88% quality, <300 KB).
     * Strictly coerces bounds to prevent IllegalArgumentException: x + width must be <= bitmap.width().
     */
    fun cropAndCompressAvatar(
        source: Bitmap,
        cropLeft: Int,
        cropTop: Int,
        cropSize: Int,
        targetSize: Int = DEFAULT_TARGET_SIZE,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): ByteArray {
        val safeLeft = cropLeft.coerceIn(0, maxOf(0, source.width - 1))
        val safeTop = cropTop.coerceIn(0, maxOf(0, source.height - 1))
        val maxAvailable = minOf(source.width - safeLeft, source.height - safeTop)
        val safeSize = cropSize.coerceIn(1, maxAvailable)

        val cropped = Bitmap.createBitmap(source, safeLeft, safeTop, safeSize, safeSize)
        val scaled = if (safeSize != targetSize) {
            val result = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
            if (result != cropped) {
                cropped.recycle()
            }
            result
        } else {
            cropped
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        var bytes = stream.toByteArray()

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            val fallbackStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, fallbackStream)
            bytes = fallbackStream.toByteArray()
        }

        if (scaled != source) {
            scaled.recycle()
        }

        return bytes
    }

    /**
     * Center crop and compress from URI directly (reads stream once into bytes).
     */
    fun centerCropAndCompress(
        context: Context,
        uri: Uri,
        targetSize: Int = DEFAULT_TARGET_SIZE,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): ByteArray? {
        val bitmap = decodeSubsampledBitmap(context, uri) ?: return null
        val minDim = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - minDim) / 2
        val top = (bitmap.height - minDim) / 2
        val bytes = cropAndCompressAvatar(bitmap, left, top, minDim, targetSize, quality)
        bitmap.recycle()
        return bytes
    }

    /**
     * Persist cropped bytes to temporary cache file for optimistic UI rendering in Coil.
     */
    fun saveToTempCache(context: Context, bytes: ByteArray): Uri? {
        return try {
            val tempFile = File(context.cacheDir, "avatar_cache_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            Uri.fromFile(tempFile)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Processes a user-selected gallery image into an EXIF-oriented, screen-friendly
     * JPEG byte array (longest edge <= 1920px, <= ~1.4 MB) for chat wallpapers.
     */
    fun processWallpaperJpeg(
        context: Context,
        uri: Uri,
        maxEdge: Int = 1920,
        quality: Int = 86,
        maxBytes: Int = 1_400_000
    ): ByteArray? {
        val decoded = decodeSubsampledBitmap(context, uri, maxDimension = maxEdge) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val scaled = if (longest > maxEdge && longest > 0) {
            val ratio = maxEdge.toFloat() / longest.toFloat()
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (resized != decoded) decoded.recycle()
            resized
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        var bytes = out.toByteArray()
        if (bytes.size > maxBytes) {
            val fallback = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 74, fallback)
            bytes = fallback.toByteArray()
        }
        scaled.recycle()
        return bytes
    }

    /**
     * Persists a local copy of a processed wallpaper inside app filesDir/wallpapers
     * for instant offline/cold-start loading before network sync completes.
     */
    fun saveWallpaperLocally(context: Context, key: String, jpegBytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }
            val file = File(dir, "wp_${safeKey}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                fos.write(jpegBytes)
                fos.flush()
            }
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
