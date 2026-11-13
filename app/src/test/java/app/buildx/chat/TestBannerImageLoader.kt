package app.buildx.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers

/**
 * Deterministic synchronous Coil ImageLoader for Robolectric/Roborazzi screenshot tests.
 * Serves atmospheric synthetic bitmaps for banner and avatar test URIs with zero network I/O.
 */
object TestBannerImageLoader {

    const val TEST_BANNER_URL = "https://test.buildx.chat/banner_ocean.png"
    const val TEST_AVATAR_URL = "https://test.buildx.chat/avatar_sample.png"

    private val bannerBitmap: Bitmap by lazy { createAtmosphericBannerBitmap() }
    private val avatarBitmap: Bitmap by lazy { createAvatarBitmap() }

    fun install() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val loader = ImageLoader.Builder(context)
            .dispatcher(Dispatchers.Unconfined)
            .allowHardware(false)
            .components {
                add(Interceptor { chain ->
                    val dataStr = chain.request.data?.toString().orEmpty()
                    val bmp = when (dataStr) {
                        TEST_BANNER_URL -> bannerBitmap
                        TEST_AVATAR_URL -> avatarBitmap
                        else -> null
                    }
                    if (bmp != null) {
                        SuccessResult(
                            drawable = BitmapDrawable(context.resources, bmp),
                            request = chain.request,
                            dataSource = DataSource.MEMORY
                        )
                    } else {
                        chain.proceed(chain.request)
                    }
                })
            }
            .build()
        Coil.setImageLoader(loader)
    }

    fun reset() {
        Coil.reset()
    }

    private fun createAtmosphericBannerBitmap(): Bitmap {
        val width = 822
        val height = 472
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep oceanic abyssal zenith to slate horizon
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFF0A2438.toInt(), 0xFF144266.toInt(), 0xFF2B6790.toInt(), 0xFF5185A8.toInt()),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Soft diffused atmospheric glow center-top to test scrim readability and 7-stop dissolve
        paint.shader = RadialGradient(
            width * 0.52f, height * 0.34f, width * 0.42f,
            intArrayOf(0x66A8D5F2.toInt(), 0x225185A8.toInt(), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Subtle silhouette curve near mid-banner to make CenterCrop + fade visually inspectable
        paint.shader = null
        paint.color = 0x55081928.toInt()
        canvas.drawCircle(width * 0.52f, height * 0.42f, 54f, paint)
        return bmp
    }

    private fun createAvatarBitmap(): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFF0E2942.toInt(), 0xFF1F4F78.toInt(), 0xFF3E7BA8.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = 0xAA07131E.toInt()
        canvas.drawCircle(size * 0.5f, size * 0.46f, 44f, paint)
        return bmp
    }
}
