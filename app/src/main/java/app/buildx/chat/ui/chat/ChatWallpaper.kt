package app.buildx.chat.ui.chat

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.buildx.chat.R
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.WallpaperCategory
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import coil.transform.Transformation
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class ChatWallpaperPreset(
    val id: String,
    val title: String,
    val category: WallpaperCategory,
    val lightGradient: List<Color>,
    val darkGradient: List<Color>,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val style: WallpaperArtStyle
) {
    fun swatchColors(isDark: Boolean): List<Color> = if (isDark) darkGradient else lightGradient
}

enum class WallpaperArtStyle {
    CLEAN_DEFAULT,
    SKY_CLOUDS,
    PAPER_WEAVE,
    SLATE_STARFIELD,
    FROSTED_BOKEH,
    HEARTS_DRIFT,
    PETALS_SWEEP,
    SUNSET_RIBBONS,
    SILK_FOLDS,
    GOLDEN_RAYS,
    AURORA_WAVES,
    DESERT_DUNES,
    RAIN_NOIR,
    COSMIC_ORBITS,
    PASTEL_ARCHES,
    ALPINE_PEAKS,
    FOREST_CANOPY,
    OCEAN_CAUSTICS,
    SAKURA_BRANCH,
    LIQUID_BLOBS,
    MATCHA_CONTOURS,
    LAVA_VEINS,
    PEARL_PRISM,
    DOODLE_MOSAIC,
    TOPO_RINGS
}

object ChatWallpaperCatalog {

    val defaultPreset = ChatWallpaperPreset(
        id = "default",
        title = "Clean Default",
        category = WallpaperCategory.SIGNATURE,
        lightGradient = listOf(Color(0xFFFFFFFF), Color(0xFFF6F7F8)),
        darkGradient = listOf(Color(0xFF0C0D10), Color(0xFF14171D)),
        accentPrimary = Color(0xFF0064E0),
        accentSecondary = Color(0xFF94A3B8),
        style = WallpaperArtStyle.CLEAN_DEFAULT
    )

    /**
     * 24 original hand-crafted procedural & vector Canvas wallpapers across 6 categories.
     * Signature (4) + Romance (5) + Cinema (5) + Nature (4) + Abstract (4) + Patterns (2) = 24.
     */
    val artisticPresets: List<ChatWallpaperPreset> = listOf(
        // --- SIGNATURE (4) ---
        ChatWallpaperPreset(
            id = "classic_sky",
            title = "Classic Sky",
            category = WallpaperCategory.SIGNATURE,
            lightGradient = listOf(Color(0xFFE7F2FF), Color(0xFFD0E7FF), Color(0xFFF2F8FF)),
            darkGradient = listOf(Color(0xFF091529), Color(0xFF0F2647), Color(0xFF07111F)),
            accentPrimary = Color(0xFF4BA3FF),
            accentSecondary = Color(0xFFB5DCFF),
            style = WallpaperArtStyle.SKY_CLOUDS
        ),
        ChatWallpaperPreset(
            id = "paper_mist",
            title = "Paper Mist",
            category = WallpaperCategory.SIGNATURE,
            lightGradient = listOf(Color(0xFFFBF9F5), Color(0xFFF2EDE4), Color(0xFFFAF7F0)),
            darkGradient = listOf(Color(0xFF151618), Color(0xFF1D1F23), Color(0xFF111215)),
            accentPrimary = Color(0xFFB8A99A),
            accentSecondary = Color(0xFFE2DCD0),
            style = WallpaperArtStyle.PAPER_WEAVE
        ),
        ChatWallpaperPreset(
            id = "slate_noir",
            title = "Slate Noir",
            category = WallpaperCategory.SIGNATURE,
            lightGradient = listOf(Color(0xFFE8ECF2), Color(0xFFD6DDE8), Color(0xFFF1F4F8)),
            darkGradient = listOf(Color(0xFF0B0E13), Color(0xFF161B24), Color(0xFF07090D)),
            accentPrimary = Color(0xFF8AA4C8),
            accentSecondary = Color(0xFF475569),
            style = WallpaperArtStyle.SLATE_STARFIELD
        ),
        ChatWallpaperPreset(
            id = "frosted_glass",
            title = "Frosted Glass",
            category = WallpaperCategory.SIGNATURE,
            lightGradient = listOf(Color(0xFFEDF4FC), Color(0xFFE9ECFB), Color(0xFFF7FAFF)),
            darkGradient = listOf(Color(0xFF111827), Color(0xFF1B1F36), Color(0xFF0D111C)),
            accentPrimary = Color(0xFF82B1FF),
            accentSecondary = Color(0xFFC5B4FF),
            style = WallpaperArtStyle.FROSTED_BOKEH
        ),

        // --- ROMANCE (5) ---
        ChatWallpaperPreset(
            id = "velvet_rose",
            title = "Velvet Rose",
            category = WallpaperCategory.ROMANCE,
            lightGradient = listOf(Color(0xFFFFEFF3), Color(0xFFFCDCE5), Color(0xFFFFF5F8)),
            darkGradient = listOf(Color(0xFF230A14), Color(0xFF3E1022), Color(0xFF17060D)),
            accentPrimary = Color(0xFFFF4D79),
            accentSecondary = Color(0xFFFFA6BE),
            style = WallpaperArtStyle.HEARTS_DRIFT
        ),
        ChatWallpaperPreset(
            id = "blush_petals",
            title = "Blush Petals",
            category = WallpaperCategory.ROMANCE,
            lightGradient = listOf(Color(0xFFFFF1F2), Color(0xFFFCE7F3), Color(0xFFFFF8F6)),
            darkGradient = listOf(Color(0xFF24111B), Color(0xFF361726), Color(0xFF1A0B14)),
            accentPrimary = Color(0xFFF472B6),
            accentSecondary = Color(0xFFFDA4AF),
            style = WallpaperArtStyle.PETALS_SWEEP
        ),
        ChatWallpaperPreset(
            id = "sunset_embrace",
            title = "Sunset Embrace",
            category = WallpaperCategory.ROMANCE,
            lightGradient = listOf(Color(0xFFFFEFE6), Color(0xFFFDD6C8), Color(0xFFFDE8D7)),
            darkGradient = listOf(Color(0xFF281214), Color(0xFF421C1A), Color(0xFF1B0C10)),
            accentPrimary = Color(0xFFFF7A59),
            accentSecondary = Color(0xFFFFBE98),
            style = WallpaperArtStyle.SUNSET_RIBBONS
        ),
        ChatWallpaperPreset(
            id = "crimson_silk",
            title = "Crimson Silk",
            category = WallpaperCategory.ROMANCE,
            lightGradient = listOf(Color(0xFFFBE9EE), Color(0xFFF3C6D3), Color(0xFFFCEEF2)),
            darkGradient = listOf(Color(0xFF280812), Color(0xFF4B0F20), Color(0xFF19040A)),
            accentPrimary = Color(0xFFE11D48),
            accentSecondary = Color(0xFFF43F5E),
            style = WallpaperArtStyle.SILK_FOLDS
        ),
        ChatWallpaperPreset(
            id = "golden_hour_love",
            title = "Golden Hour",
            category = WallpaperCategory.ROMANCE,
            lightGradient = listOf(Color(0xFFFFF6E5), Color(0xFFFCE3B4), Color(0xFFFFF9EE)),
            darkGradient = listOf(Color(0xFF24190C), Color(0xFF3D2812), Color(0xFF181007)),
            accentPrimary = Color(0xFFF59E0B),
            accentSecondary = Color(0xFFFCD34D),
            style = WallpaperArtStyle.GOLDEN_RAYS
        ),

        // --- CINEMA (5) ---
        ChatWallpaperPreset(
            id = "aurora_midnight",
            title = "Midnight Aurora",
            category = WallpaperCategory.CINEMA,
            lightGradient = listOf(Color(0xFFE3F7F4), Color(0xFFE7EAFF), Color(0xFFF4EBFF)),
            darkGradient = listOf(Color(0xFF060E1A), Color(0xFF0B2330), Color(0xFF1C0F2E)),
            accentPrimary = Color(0xFF00F5D4),
            accentSecondary = Color(0xFF9B5DE5),
            style = WallpaperArtStyle.AURORA_WAVES
        ),
        ChatWallpaperPreset(
            id = "dune_sands",
            title = "Desert Dunes",
            category = WallpaperCategory.CINEMA,
            lightGradient = listOf(Color(0xFFF8EDE0), Color(0xFFEDD0B0), Color(0xFFE2B587)),
            darkGradient = listOf(Color(0xFF1C130C), Color(0xFF332114), Color(0xFF472C18)),
            accentPrimary = Color(0xFFD97706),
            accentSecondary = Color(0xFFFBBF24),
            style = WallpaperArtStyle.DESERT_DUNES
        ),
        ChatWallpaperPreset(
            id = "gotham_rain",
            title = "Rainy Noir",
            category = WallpaperCategory.CINEMA,
            lightGradient = listOf(Color(0xFFDFE6EE), Color(0xFFC5D0DE), Color(0xFFEBEFF5)),
            darkGradient = listOf(Color(0xFF080B10), Color(0xFF121A24), Color(0xFF05070A)),
            accentPrimary = Color(0xFF38BDF8),
            accentSecondary = Color(0xFFF59E0B),
            style = WallpaperArtStyle.RAIN_NOIR
        ),
        ChatWallpaperPreset(
            id = "interstellar_drift",
            title = "Cosmic Drift",
            category = WallpaperCategory.CINEMA,
            lightGradient = listOf(Color(0xFFE5EAF8), Color(0xFFD2DBF2), Color(0xFFF1EFFC)),
            darkGradient = listOf(Color(0xFF050712), Color(0xFF0C1226), Color(0xFF150E24)),
            accentPrimary = Color(0xFFFDE68A),
            accentSecondary = Color(0xFF60A5FA),
            style = WallpaperArtStyle.COSMIC_ORBITS
        ),
        ChatWallpaperPreset(
            id = "pastel_hotel",
            title = "Grand Pastel",
            category = WallpaperCategory.CINEMA,
            lightGradient = listOf(Color(0xFFFDECF0), Color(0xFFEBF5EE), Color(0xFFFFF7E2)),
            darkGradient = listOf(Color(0xFF21161C), Color(0xFF182420), Color(0xFF231E15)),
            accentPrimary = Color(0xFFE89CA9),
            accentSecondary = Color(0xFF8BB89F),
            style = WallpaperArtStyle.PASTEL_ARCHES
        ),

        // --- NATURE (4) ---
        ChatWallpaperPreset(
            id = "alpine_dawn",
            title = "Alpine Dawn",
            category = WallpaperCategory.NATURE,
            lightGradient = listOf(Color(0xFFE6F0FA), Color(0xFFDCE6F7), Color(0xFFFCE8EC)),
            darkGradient = listOf(Color(0xFF0A1424), Color(0xFF12233B), Color(0xFF1D1E30)),
            accentPrimary = Color(0xFF60A5FA),
            accentSecondary = Color(0xFF93C5FD),
            style = WallpaperArtStyle.ALPINE_PEAKS
        ),
        ChatWallpaperPreset(
            id = "forest_canopy",
            title = "Forest Canopy",
            category = WallpaperCategory.NATURE,
            lightGradient = listOf(Color(0xFFE5F4EB), Color(0xFFCCE7D7), Color(0xFFF0F9F3)),
            darkGradient = listOf(Color(0xFF071911), Color(0xFF0E2B1E), Color(0xFF06130D)),
            accentPrimary = Color(0xFF34D399),
            accentSecondary = Color(0xFF6EE7B7),
            style = WallpaperArtStyle.FOREST_CANOPY
        ),
        ChatWallpaperPreset(
            id = "ocean_depths",
            title = "Ocean Depths",
            category = WallpaperCategory.NATURE,
            lightGradient = listOf(Color(0xFFE0F7FA), Color(0xFFBAE6FD), Color(0xFFCCFBF1)),
            darkGradient = listOf(Color(0xFF04151F), Color(0xFF082A3B), Color(0xFF031018)),
            accentPrimary = Color(0xFF22D3EE),
            accentSecondary = Color(0xFF38BDF8),
            style = WallpaperArtStyle.OCEAN_CAUSTICS
        ),
        ChatWallpaperPreset(
            id = "sakura_breeze",
            title = "Sakura Breeze",
            category = WallpaperCategory.NATURE,
            lightGradient = listOf(Color(0xFFFFF4F6), Color(0xFFFCE2EB), Color(0xFFF4ECFA)),
            darkGradient = listOf(Color(0xFF1D1018), Color(0xFF2B1723), Color(0xFF191224)),
            accentPrimary = Color(0xFFF9A8D4),
            accentSecondary = Color(0xFFFDA4AF),
            style = WallpaperArtStyle.SAKURA_BRANCH
        ),

        // --- ABSTRACT (4) ---
        ChatWallpaperPreset(
            id = "liquid_mercury",
            title = "Liquid Mercury",
            category = WallpaperCategory.ABSTRACT,
            lightGradient = listOf(Color(0xFFECEFF3), Color(0xFFD8DEE6), Color(0xFFF6F8FA)),
            darkGradient = listOf(Color(0xFF101318), Color(0xFF1D222B), Color(0xFF090B0F)),
            accentPrimary = Color(0xFF94A3B8),
            accentSecondary = Color(0xFFCBD5E1),
            style = WallpaperArtStyle.LIQUID_BLOBS
        ),
        ChatWallpaperPreset(
            id = "matcha_sage",
            title = "Matcha Sage",
            category = WallpaperCategory.ABSTRACT,
            lightGradient = listOf(Color(0xFFF1F7ED), Color(0xFFDDEBD5), Color(0xFFF7FBF3)),
            darkGradient = listOf(Color(0xFF0F1911), Color(0xFF1A2B1D), Color(0xFF0A120B)),
            accentPrimary = Color(0xFF84CC16),
            accentSecondary = Color(0xFF86EFAC),
            style = WallpaperArtStyle.MATCHA_CONTOURS
        ),
        ChatWallpaperPreset(
            id = "lava_ember",
            title = "Lava Ember",
            category = WallpaperCategory.ABSTRACT,
            lightGradient = listOf(Color(0xFFFFEFEA), Color(0xFFFED7CC), Color(0xFFFCE7D6)),
            darkGradient = listOf(Color(0xFF1B0907), Color(0xFF36110B), Color(0xFF120605)),
            accentPrimary = Color(0xFFFF5722),
            accentSecondary = Color(0xFFFFAB40),
            style = WallpaperArtStyle.LAVA_VEINS
        ),
        ChatWallpaperPreset(
            id = "iridescent_pearl",
            title = "Iridescent Pearl",
            category = WallpaperCategory.ABSTRACT,
            lightGradient = listOf(Color(0xFFEAFBF8), Color(0xFFEDE9FE), Color(0xFFFFF4EA)),
            darkGradient = listOf(Color(0xFF0D1D1E), Color(0xFF1C172B), Color(0xFF1E1914)),
            accentPrimary = Color(0xFFA78BFA),
            accentSecondary = Color(0xFF5EEAD4),
            style = WallpaperArtStyle.PEARL_PRISM
        ),

        // --- PATTERNS (2) ---
        ChatWallpaperPreset(
            id = "telegram_doodle",
            title = "Telegram Breeze",
            category = WallpaperCategory.PATTERNS,
            lightGradient = listOf(Color(0xFFDCEFD8), Color(0xFFCDE7CC), Color(0xFFE5F2DB)),
            darkGradient = listOf(Color(0xFF0B191D), Color(0xFF10242A), Color(0xFF081216)),
            accentPrimary = Color(0xFF4F8A64),
            accentSecondary = Color(0xFF7CB48C),
            style = WallpaperArtStyle.DOODLE_MOSAIC
        ),
        ChatWallpaperPreset(
            id = "nordic_topo",
            title = "Topo Contours",
            category = WallpaperCategory.PATTERNS,
            lightGradient = listOf(Color(0xFFF3F0EA), Color(0xFFE8E2D7), Color(0xFFF8F6F2)),
            darkGradient = listOf(Color(0xFF111316), Color(0xFF1A1E23), Color(0xFF0D0F12)),
            accentPrimary = Color(0xFF8C7A65),
            accentSecondary = Color(0xFFB5A48E),
            style = WallpaperArtStyle.TOPO_RINGS
        )
    )

    val allSelectablePresets: List<ChatWallpaperPreset> = listOf(defaultPreset) + artisticPresets

    fun findById(id: String?): ChatWallpaperPreset {
        val clean = id?.trim()?.lowercase().orEmpty()
        return allSelectablePresets.firstOrNull { it.id == clean } ?: defaultPreset
    }

    @StringRes
    fun categoryLabelRes(category: WallpaperCategory): Int = when (category) {
        WallpaperCategory.SIGNATURE -> R.string.dm_wallpaper_cat_signature
        WallpaperCategory.ROMANCE -> R.string.dm_wallpaper_cat_romance
        WallpaperCategory.CINEMA -> R.string.dm_wallpaper_cat_cinema
        WallpaperCategory.NATURE -> R.string.dm_wallpaper_cat_nature
        WallpaperCategory.ABSTRACT -> R.string.dm_wallpaper_cat_abstract
        WallpaperCategory.PATTERNS -> R.string.dm_wallpaper_cat_patterns
    }
}

fun ChatWallpaperConfig.displayTitle(customLabel: String): String = when {
    isCustom -> customLabel
    else -> ChatWallpaperCatalog.findById(wallpaperId).title
}

/**
 * Renders the active chat wallpaper behind messages or inside live previews.
 * Supports built-in procedural Canvas art, user gallery images, adaptive Blur
 * (Modifier.blur on Android 12+ and downscaled box-blur bitmap fallback on older APIs),
 * and customizable black dimming scrim (0f..0.70f).
 */
@Composable
fun ChatWallpaperLayer(
    config: ChatWallpaperConfig,
    isDark: Boolean,
    fallbackBackground: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dimAlpha = config.dim.coerceIn(0f, 0.70f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackBackground)
    ) {
        if (config.isCustom && !config.customUrl.isNullOrBlank()) {
            val rawPath = config.customUrl
            val modelData: Any = remember(rawPath) {
                if (rawPath.startsWith("/")) File(rawPath) else rawPath
            }
            val blurModifier = if (config.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(16.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
            } else {
                Modifier
            }
            val imageRequest = remember(modelData, config.blur, context) {
                ImageRequest.Builder(context)
                    .data(modelData)
                    .crossfade(true)
                    .apply {
                        if (config.blur && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            transformations(FastBoxBlurTransformation(radius = 16))
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        } else {
            val preset = ChatWallpaperCatalog.findById(config.wallpaperId)
            if (preset.id != "default") {
                val blurModifier = if (config.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(14.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                }
                ChatWallpaperArtwork(
                    preset = preset,
                    isDark = isDark,
                    softened = config.blur && Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurModifier)
                )
            }
        }

        if (dimAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }
    }
}

@Composable
fun ChatWallpaperArtwork(
    preset: ChatWallpaperPreset,
    isDark: Boolean,
    softened: Boolean = false,
    modifier: Modifier = Modifier
) {
    val stops = preset.swatchColors(isDark)
    val accent1 = preset.accentPrimary
    val accent2 = preset.accentSecondary
    val inkAlpha = if (softened) 0.55f else 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawRect(
            brush = Brush.linearGradient(
                colors = stops,
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        when (preset.style) {
            WallpaperArtStyle.CLEAN_DEFAULT -> Unit

            WallpaperArtStyle.SKY_CLOUDS -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = (if (isDark) 0.08f else 0.65f) * inkAlpha), Color.Transparent)
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.22f, h * 0.24f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent1.copy(alpha = (if (isDark) 0.22f else 0.16f) * inkAlpha), Color.Transparent)
                    ),
                    radius = w * 0.65f,
                    center = Offset(w * 0.85f, h * 0.72f)
                )
                drawSoftWave(w, h, 0.78f, accent2.copy(alpha = (if (isDark) 0.10f else 0.45f) * inkAlpha), amplitude = h * 0.04f)
            }

            WallpaperArtStyle.PAPER_WEAVE -> {
                val lineColor = accent1.copy(alpha = (if (isDark) 0.06f else 0.11f) * inkAlpha)
                val step = (w / 14f).coerceAtLeast(18f)
                var x = -h
                while (x < w + h) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x + h * 0.4f, h), strokeWidth = 1f)
                    x += step
                }
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = (if (isDark) 0.06f else 0.35f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.6f,
                    center = Offset(w * 0.5f, h * 0.42f)
                )
            }

            WallpaperArtStyle.SLATE_STARFIELD -> {
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, accent1.copy(alpha = 0.14f * inkAlpha), Color.Transparent),
                        start = Offset(0f, h * 0.2f),
                        end = Offset(w, h * 0.8f)
                    ),
                    start = Offset(-w * 0.1f, h * 0.15f),
                    end = Offset(w * 1.1f, h * 0.78f),
                    strokeWidth = w * 0.32f
                )
                drawConstellationSpecks(w, h, Color.White.copy(alpha = (if (isDark) 0.26f else 0.42f) * inkAlpha), seed = 17)
            }

            WallpaperArtStyle.FROSTED_BOKEH -> {
                val circles = listOf(
                    Triple(0.18f, 0.22f, 0.32f),
                    Triple(0.78f, 0.32f, 0.38f),
                    Triple(0.45f, 0.66f, 0.42f),
                    Triple(0.14f, 0.82f, 0.26f)
                )
                circles.forEachIndexed { idx, (cx, cy, r) ->
                    val col = if (idx % 2 == 0) accent1 else accent2
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(col.copy(alpha = (if (isDark) 0.18f else 0.24f) * inkAlpha), Color.Transparent)
                        ),
                        radius = w * r,
                        center = Offset(w * cx, h * cy)
                    )
                }
            }

            WallpaperArtStyle.HEARTS_DRIFT -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent1.copy(alpha = (if (isDark) 0.24f else 0.18f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.72f,
                    center = Offset(w * 0.78f, h * 0.25f)
                )
                val hearts = listOf(
                    Quad(0.16f, 0.20f, 0.08f, -14f),
                    Quad(0.82f, 0.18f, 0.055f, 12f),
                    Quad(0.54f, 0.46f, 0.10f, -6f),
                    Quad(0.24f, 0.70f, 0.065f, 16f),
                    Quad(0.76f, 0.82f, 0.075f, -10f),
                    Quad(0.42f, 0.24f, 0.042f, 8f)
                )
                hearts.forEachIndexed { i, hSpec ->
                    val tint = (if (i % 2 == 0) accent1 else accent2).copy(alpha = (if (isDark) 0.22f else 0.28f) * inkAlpha)
                    drawVectorHeart(
                        center = Offset(w * hSpec.x, h * hSpec.y),
                        sizePx = w * hSpec.scale,
                        rotationDeg = hSpec.deg,
                        color = tint,
                        filled = i % 3 == 0
                    )
                }
            }

            WallpaperArtStyle.PETALS_SWEEP -> {
                drawSoftWave(w, h, 0.35f, accent1.copy(alpha = (if (isDark) 0.12f else 0.18f) * inkAlpha), amplitude = h * 0.07f)
                drawSoftWave(w, h, 0.72f, accent2.copy(alpha = (if (isDark) 0.10f else 0.22f) * inkAlpha), amplitude = h * 0.06f)
                for (i in 0..8) {
                    val px = w * ((0.11f * i + 0.08f) % 0.92f)
                    val py = h * ((0.13f * i + 0.12f) % 0.88f)
                    drawPetal(Offset(px, py), w * (0.045f + (i % 3) * 0.015f), (i * 37f) % 360f, accent1.copy(alpha = (if (isDark) 0.24f else 0.30f) * inkAlpha))
                }
            }

            WallpaperArtStyle.SUNSET_RIBBONS -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = (if (isDark) 0.22f else 0.50f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.65f,
                    center = Offset(w * 0.5f, h * 0.34f)
                )
                drawSilkRibbon(w, h, accent1.copy(alpha = (if (isDark) 0.20f else 0.24f) * inkAlpha), yShift = 0.45f)
                drawSilkRibbon(w, h, accent2.copy(alpha = (if (isDark) 0.16f else 0.28f) * inkAlpha), yShift = 0.62f)
            }

            WallpaperArtStyle.SILK_FOLDS -> {
                for (i in 0..3) {
                    val y = 0.18f + i * 0.20f
                    drawSilkRibbon(w, h, accent1.copy(alpha = ((0.18f - i * 0.02f) * if (isDark) 1f else 0.85f) * inkAlpha), yShift = y)
                }
            }

            WallpaperArtStyle.GOLDEN_RAYS -> {
                val origin = Offset(w * 0.85f, h * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = (if (isDark) 0.22f else 0.48f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.78f,
                    center = origin
                )
                for (i in 0..5) {
                    val endX = w * (1f - i * 0.22f)
                    drawLine(
                        brush = Brush.linearGradient(listOf(accent1.copy(alpha = 0.18f * inkAlpha), Color.Transparent), origin, Offset(endX, h)),
                        start = origin,
                        end = Offset(endX, h),
                        strokeWidth = w * 0.09f
                    )
                }
                drawConstellationSpecks(w, h, accent2.copy(alpha = 0.45f * inkAlpha), seed = 29)
            }

            WallpaperArtStyle.AURORA_WAVES -> {
                drawSilkRibbon(w, h, accent1.copy(alpha = (if (isDark) 0.28f else 0.22f) * inkAlpha), yShift = 0.28f)
                drawSilkRibbon(w, h, accent2.copy(alpha = (if (isDark) 0.26f else 0.18f) * inkAlpha), yShift = 0.46f)
                drawSilkRibbon(w, h, accent1.copy(alpha = (if (isDark) 0.16f else 0.14f) * inkAlpha), yShift = 0.68f)
                drawConstellationSpecks(w, h, Color.White.copy(alpha = (if (isDark) 0.34f else 0.22f) * inkAlpha), seed = 7)
            }

            WallpaperArtStyle.DESERT_DUNES -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = (if (isDark) 0.22f else 0.42f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.42f,
                    center = Offset(w * 0.74f, h * 0.22f)
                )
                drawDuneLayer(w, h, 0.56f, accent2.copy(alpha = (if (isDark) 0.18f else 0.30f) * inkAlpha))
                drawDuneLayer(w, h, 0.70f, accent1.copy(alpha = (if (isDark) 0.24f else 0.35f) * inkAlpha))
                drawDuneLayer(w, h, 0.84f, accent1.copy(alpha = (if (isDark) 0.34f else 0.48f) * inkAlpha))
            }

            WallpaperArtStyle.RAIN_NOIR -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent1.copy(alpha = 0.16f * inkAlpha), Color.Transparent)),
                    radius = w * 0.45f,
                    center = Offset(w * 0.2f, h * 0.28f)
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = 0.14f * inkAlpha), Color.Transparent)),
                    radius = w * 0.40f,
                    center = Offset(w * 0.8f, h * 0.68f)
                )
                val rainCol = Color.White.copy(alpha = (if (isDark) 0.16f else 0.35f) * inkAlpha)
                for (i in 0..22) {
                    val sx = w * ((i * 0.083f + 0.03f) % 1.0f)
                    val sy = h * ((i * 0.137f) % 0.92f)
                    val len = h * (0.045f + (i % 3) * 0.025f)
                    drawLine(rainCol, Offset(sx, sy), Offset(sx - len * 0.18f, sy + len), strokeWidth = 1.6f, cap = StrokeCap.Round)
                }
            }

            WallpaperArtStyle.COSMIC_ORBITS -> {
                val c = Offset(w * 0.5f, h * 0.48f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent1.copy(alpha = (if (isDark) 0.20f else 0.24f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.45f,
                    center = c
                )
                for (i in 1..4) {
                    rotate(degrees = -18f + i * 11f, pivot = c) {
                        drawOval(
                            color = (if (i % 2 == 0) accent1 else accent2).copy(alpha = 0.22f * inkAlpha),
                            topLeft = Offset(c.x - w * (0.20f + i * 0.12f), c.y - h * (0.08f + i * 0.04f)),
                            size = Size(w * (0.40f + i * 0.24f), h * (0.16f + i * 0.08f)),
                            style = Stroke(width = 1.4f)
                        )
                    }
                }
                drawConstellationSpecks(w, h, Color.White.copy(alpha = (if (isDark) 0.48f else 0.25f) * inkAlpha), seed = 43)
            }

            WallpaperArtStyle.PASTEL_ARCHES -> {
                val frameColor = accent1.copy(alpha = 0.26f * inkAlpha)
                val lineColor = accent2.copy(alpha = 0.28f * inkAlpha)
                val archMargin = w * 0.14f
                drawRoundRect(
                    color = frameColor,
                    topLeft = Offset(archMargin, h * 0.14f),
                    size = Size(w - archMargin * 2, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.36f, w * 0.36f),
                    style = Stroke(width = 2.2f)
                )
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(archMargin + 14f, h * 0.14f + 14f),
                    size = Size(w - (archMargin + 14f) * 2, h * 0.72f - 28f),
                    cornerRadius = CornerRadius(w * 0.34f, w * 0.34f),
                    style = Stroke(width = 1.2f)
                )
            }

            WallpaperArtStyle.ALPINE_PEAKS -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White.copy(alpha = (if (isDark) 0.12f else 0.55f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.35f,
                    center = Offset(w * 0.72f, h * 0.25f)
                )
                drawMountainSilhouette(w, h, 0.58f, accent2.copy(alpha = (if (isDark) 0.16f else 0.28f) * inkAlpha))
                drawMountainSilhouette(w, h, 0.70f, accent1.copy(alpha = (if (isDark) 0.24f else 0.36f) * inkAlpha), shiftX = 0.18f)
            }

            WallpaperArtStyle.FOREST_CANOPY -> {
                for (i in 0..3) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf((if (i % 2 == 0) accent1 else accent2).copy(alpha = (if (isDark) 0.18f else 0.24f) * inkAlpha), Color.Transparent)
                        ),
                        radius = w * (0.40f + i * 0.08f),
                        center = Offset(w * (0.2f + i * 0.22f), h * (0.18f + (i % 2) * 0.35f))
                    )
                }
                drawSoftWave(w, h, 0.76f, accent1.copy(alpha = (if (isDark) 0.14f else 0.20f) * inkAlpha), amplitude = h * 0.06f)
            }

            WallpaperArtStyle.OCEAN_CAUSTICS -> {
                for (i in 0..4) {
                    drawSoftWave(
                        w,
                        h,
                        baseFraction = 0.22f + i * 0.16f,
                        color = (if (i % 2 == 0) accent1 else accent2).copy(alpha = (if (isDark) 0.15f else 0.22f) * inkAlpha),
                        amplitude = h * (0.03f + (i % 2) * 0.02f)
                    )
                }
            }

            WallpaperArtStyle.SAKURA_BRANCH -> {
                val branchPath = Path().apply {
                    moveTo(0f, h * 0.26f)
                    cubicTo(w * 0.28f, h * 0.24f, w * 0.45f, h * 0.32f, w * 0.68f, h * 0.22f)
                    moveTo(w * 0.32f, h * 0.27f)
                    cubicTo(w * 0.42f, h * 0.35f, w * 0.56f, h * 0.40f, w * 0.74f, h * 0.38f)
                }
                drawPath(
                    path = branchPath,
                    color = accent2.copy(alpha = (if (isDark) 0.28f else 0.38f) * inkAlpha),
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                )
                for (i in 0..9) {
                    val bx = w * ((0.14f + i * 0.09f) % 0.94f)
                    val by = h * ((0.18f + (i * 0.11f)) % 0.84f)
                    drawPetal(Offset(bx, by), w * 0.035f, i * 42f, accent1.copy(alpha = (if (isDark) 0.28f else 0.42f) * inkAlpha))
                }
            }

            WallpaperArtStyle.LIQUID_BLOBS -> {
                val blobs = listOf(
                    Triple(0.24f, 0.28f, 0.38f),
                    Triple(0.76f, 0.52f, 0.44f),
                    Triple(0.32f, 0.80f, 0.35f)
                )
                blobs.forEachIndexed { i, (cx, cy, r) ->
                    val col = if (i % 2 == 0) accent1 else accent2
                    drawCircle(
                        brush = Brush.radialGradient(listOf(col.copy(alpha = (if (isDark) 0.22f else 0.32f) * inkAlpha), Color.Transparent)),
                        radius = w * r,
                        center = Offset(w * cx, h * cy)
                    )
                }
            }

            WallpaperArtStyle.MATCHA_CONTOURS -> {
                drawDuneLayer(w, h, 0.36f, accent2.copy(alpha = (if (isDark) 0.14f else 0.28f) * inkAlpha))
                drawDuneLayer(w, h, 0.64f, accent1.copy(alpha = (if (isDark) 0.16f else 0.22f) * inkAlpha))
            }

            WallpaperArtStyle.LAVA_VEINS -> {
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent1.copy(alpha = (if (isDark) 0.28f else 0.22f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.55f,
                    center = Offset(w * 0.30f, h * 0.32f)
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent2.copy(alpha = (if (isDark) 0.22f else 0.24f) * inkAlpha), Color.Transparent)),
                    radius = w * 0.50f,
                    center = Offset(w * 0.78f, h * 0.70f)
                )
                drawSilkRibbon(w, h, accent1.copy(alpha = 0.22f * inkAlpha), yShift = 0.50f)
            }

            WallpaperArtStyle.PEARL_PRISM -> {
                drawSilkRibbon(w, h, accent2.copy(alpha = 0.24f * inkAlpha), yShift = 0.32f)
                drawSilkRibbon(w, h, accent1.copy(alpha = 0.22f * inkAlpha), yShift = 0.58f)
            }

            WallpaperArtStyle.DOODLE_MOSAIC -> {
                val motifColor = accent1.copy(alpha = (if (isDark) 0.28f else 0.24f) * inkAlpha)
                val cols = 5
                val rows = 8
                val cellW = w / cols
                val cellH = h / rows
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val cx = (c + 0.5f) * cellW + (if (r % 2 == 1) cellW * 0.22f else -cellW * 0.12f)
                        val cy = (r + 0.5f) * cellH
                        val kind = (r * 3 + c) % 4
                        val motifSize = minOf(cellW, cellH) * 0.30f
                        when (kind) {
                            0 -> drawDoodleStar(Offset(cx, cy), motifSize, motifColor)
                            1 -> drawVectorHeart(Offset(cx, cy), motifSize, ((r + c) % 3 - 1) * 14f, motifColor, filled = false)
                            2 -> drawCircle(motifColor, radius = motifSize * 0.55f, center = Offset(cx, cy), style = Stroke(width = 1.6f))
                            else -> drawDiamondOutline(Offset(cx, cy), motifSize * 0.72f, motifColor)
                        }
                    }
                }
            }

            WallpaperArtStyle.TOPO_RINGS -> {
                val lineColor = accent1.copy(alpha = (if (isDark) 0.22f else 0.24f) * inkAlpha)
                val centers = listOf(Offset(w * 0.25f, h * 0.28f), Offset(w * 0.78f, h * 0.72f))
                centers.forEach { center ->
                    for (ring in 1..6) {
                        drawOval(
                            color = lineColor,
                            topLeft = Offset(center.x - w * (ring * 0.09f), center.y - h * (ring * 0.055f)),
                            size = Size(w * (ring * 0.18f), h * (ring * 0.11f)),
                            style = Stroke(width = 1.3f)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad(val x: Float, val y: Float, val scale: Float, val deg: Float)

private fun DrawScope.drawSoftWave(
    w: Float,
    h: Float,
    baseFraction: Float,
    color: Color,
    amplitude: Float
) {
    val y0 = h * baseFraction
    val path = Path().apply {
        moveTo(0f, y0)
        cubicTo(w * 0.28f, y0 - amplitude, w * 0.65f, y0 + amplitude, w, y0 - amplitude * 0.35f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawSilkRibbon(w: Float, h: Float, color: Color, yShift: Float) {
    val y = h * yShift
    val path = Path().apply {
        moveTo(-w * 0.1f, y + h * 0.06f)
        cubicTo(w * 0.3f, y - h * 0.12f, w * 0.68f, y + h * 0.14f, w * 1.1f, y - h * 0.05f)
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(listOf(color, Color.Transparent), y - h * 0.12f, y + h * 0.16f),
        style = Stroke(width = h * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawDuneLayer(w: Float, h: Float, topFraction: Float, color: Color) {
    val baseY = h * topFraction
    val path = Path().apply {
        moveTo(0f, baseY + h * 0.03f)
        cubicTo(w * 0.32f, baseY - h * 0.06f, w * 0.72f, baseY + h * 0.07f, w, baseY - h * 0.02f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawMountainSilhouette(
    w: Float,
    h: Float,
    baseFraction: Float,
    color: Color,
    shiftX: Float = 0f
) {
    val y = h * baseFraction
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, y + h * 0.08f)
        lineTo(w * (0.24f + shiftX * 0.4f), y - h * 0.07f)
        lineTo(w * (0.48f + shiftX * 0.2f), y + h * 0.03f)
        lineTo(w * (0.76f - shiftX * 0.3f), y - h * 0.10f)
        lineTo(w, y + h * 0.05f)
        lineTo(w, h)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawVectorHeart(
    center: Offset,
    sizePx: Float,
    rotationDeg: Float,
    color: Color,
    filled: Boolean
) {
    rotate(degrees = rotationDeg, pivot = center) {
        val r = sizePx * 0.5f
        val path = Path().apply {
            moveTo(center.x, center.y + r * 0.75f)
            cubicTo(
                center.x - r * 1.15f,
                center.y + r * 0.1f,
                center.x - r * 1.05f,
                center.y - r * 0.9f,
                center.x,
                center.y - r * 0.3f
            )
            cubicTo(
                center.x + r * 1.05f,
                center.y - r * 0.9f,
                center.x + r * 1.15f,
                center.y + r * 0.1f,
                center.x,
                center.y + r * 0.75f
            )
            close()
        }
        if (filled) {
            drawPath(path = path, color = color)
        } else {
            drawPath(path = path, color = color, style = Stroke(width = 1.7f, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawPetal(center: Offset, radius: Float, deg: Float, color: Color) {
    rotate(degrees = deg, pivot = center) {
        val path = Path().apply {
            moveTo(center.x, center.y - radius)
            cubicTo(
                center.x + radius * 0.75f,
                center.y - radius * 0.3f,
                center.x + radius * 0.65f,
                center.y + radius * 0.7f,
                center.x,
                center.y + radius
            )
            cubicTo(
                center.x - radius * 0.65f,
                center.y + radius * 0.7f,
                center.x - radius * 0.75f,
                center.y - radius * 0.3f,
                center.x,
                center.y - radius
            )
            close()
        }
        drawPath(path = path, color = color)
    }
}

private fun DrawScope.drawDoodleStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = (i * 45.0) * PI / 180.0
        val r = if (i % 2 == 0) radius else radius * 0.42f
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawDiamondOutline(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.8f, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.8f, center.y)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawConstellationSpecks(w: Float, h: Float, color: Color, seed: Int) {
    for (i in 0..28) {
        val fx = (((i * 37 + seed * 13) % 97) / 97f).coerceIn(0.03f, 0.97f)
        val fy = (((i * 53 + seed * 29) % 97) / 97f).coerceIn(0.03f, 0.97f)
        val radius = 1.2f + (i % 3) * 0.9f
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(w * fx, h * fy)
        )
    }
}

/**
 * Software fast box-blur Coil transformation for Android 8-11 devices
 * where RenderEffect-based Modifier.blur is unavailable.
 */
private class FastBoxBlurTransformation(
    private val radius: Int = 16
) : Transformation {
    override val cacheKey: String = "buildx_wallpaper_blur_$radius"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val downscaleW = (input.width / 8).coerceIn(48, 220)
        val downscaleH = (input.height / 8).coerceIn(48, 320)
        val small = Bitmap.createScaledBitmap(input, downscaleW, downscaleH, true)
        val blurredSmall = boxBlur(small, passes = 2)
        val result = Bitmap.createScaledBitmap(blurredSmall, input.width.coerceAtLeast(1), input.height.coerceAtLeast(1), true)
        if (small != input) small.recycle()
        if (blurredSmall != small && blurredSmall != result) blurredSmall.recycle()
        return result
    }

    private fun boxBlur(source: Bitmap, passes: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pix = IntArray(w * h)
        source.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val window = (radius / 4).coerceIn(2, 6)
        repeat(passes) {
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    val startX = (x - window).coerceAtLeast(0)
                    val endX = (x + window).coerceAtMost(w - 1)
                    for (kx in startX..endX) {
                        val c = pix[rowOffset + kx]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        count++
                    }
                    tmp[rowOffset + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    val startY = (y - window).coerceAtLeast(0)
                    val endY = (y + window).coerceAtMost(h - 1)
                    for (ky in startY..endY) {
                        val c = tmp[ky * w + x]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        count++
                    }
                    pix[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }
}
