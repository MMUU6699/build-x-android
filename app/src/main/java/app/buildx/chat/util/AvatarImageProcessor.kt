package app.buildx.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Avatar image processing:
 * - Decodes with subsampling to prevent OOM on 12MP+ photos
 * - Honors EXIF orientation (rotation & flips)
 * - Crops square and scales to 512x512
 * - Compresses to JPEG (~85 quality, stripped metadata, <300 KB)
 */
object AvatarImageProcessor {

    private const val DEFAULT_TARGET_SIZE = 512
    private const val DEFAULT_JPEG_QUALITY = 85
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
     * Read EXIF orientation matrix from Uri stream.
     */
    fun getExifOrientationMatrix(context: Context, uri: Uri): Matrix {
        val matrix = Matrix()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
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
     * Decode photo with subsampling and orientation applied.
     * Prevents OOM by keeping max dimension <= maxDimension (default 1920).
     */
    fun decodeSubsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1920
    ): Bitmap? {
        try {
            // 1. Measure dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Compute sample size
            val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDimension, maxDimension)

            // 3. Decode bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Apply EXIF orientation
            val matrix = getExifOrientationMatrix(context, uri)
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
     * Crop given square region and scale to 512x512 JPEG (~85 quality, stripped metadata, <300 KB).
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
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, fallbackStream)
            bytes = fallbackStream.toByteArray()
        }

        if (scaled != source) {
            scaled.recycle()
        }

        return bytes
    }

    /**
     * Center crop and compress from URI directly.
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
}
