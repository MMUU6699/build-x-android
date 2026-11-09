package app.buildx.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance

/**
 * Color tokens traced to video frames in docs/design/SPEC.md and docs/design/tokens.json.
 * Light theme values are [OBSERVED]; Dark theme values are [INFERRED] as a coherent dark surface system.
 */
@Immutable
data class BuildXColors(
    val isDark: Boolean,
    val pageBackground: Color,
    val surfaceMuted: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val primaryBlue: Color,
    val onPrimaryBlue: Color,
    val destructiveRed: Color,
    val hairlineBorder: Color,
    val grabHandle: Color,
    val scrimColor: Color,
    val markPurpleStart: Color,
    val markVioletMid: Color,
    val markBlueEnd: Color,
    val composerShadowColor: Color,
    val headerHaloShadowColor: Color,
    val stopHaloBackground: Color
)

val LightBuildXColors = BuildXColors(
    isDark = false,
    pageBackground = Color(0xFFFFFFFF),        // [OBSERVED] f_0001
    surfaceMuted = Color(0xFFF1F3F0),          // [OBSERVED] f_0001 user bubble, sheets, settings bg
    cardBackground = Color(0xFFFFFFFF),        // [OBSERVED] f_0001 composer, settings cards
    primaryText = Color(0xFF0A0A0A),           // [OBSERVED] f_0001 near-black text
    secondaryText = Color(0xFF6E7274),         // [OBSERVED] f_0001 secondary/label gray
    primaryBlue = Color(0xFF0063D3),           // [OBSERVED] f_0090 stop button, links, primary CTA
    onPrimaryBlue = Color(0xFFFFFFFF),         // [OBSERVED] f_0065 white arrow/text on blue
    destructiveRed = Color(0xFFC3192E),        // [OBSERVED] f_1980 Log out crimson red
    hairlineBorder = Color(0xFFE5E7E5),        // [OBSERVED] f_0001 1dp hairline border on inner controls
    grabHandle = Color(0xFFDADCD9),            // [OBSERVED] f_0510 sheet grab handle
    scrimColor = Color(0x4D000000),            // [OBSERVED] f_0510 30% black scrim (#B2B2B2 over white)
    markPurpleStart = Color(0xFFC24DFB),       // [OBSERVED] brand mark gradient start
    markVioletMid = Color(0xFF8842FA),         // [OBSERVED] brand mark gradient mid
    markBlueEnd = Color(0xFF4B3EF6),           // [OBSERVED] brand mark gradient end
    composerShadowColor = Color(0x16000000),   // [OBSERVED] ~8.5% black diffuse shadow
    headerHaloShadowColor = Color(0x0D000000), // [OBSERVED] ~5% black faint halo
    stopHaloBackground = Color(0xFFE8F0FE)     // [OBSERVED] f_0090 36dp light halo around stop square
)

val DarkBuildXColors = BuildXColors(
    isDark = true,
    pageBackground = Color(0xFF0E1012),        // [INFERRED] dark page background
    surfaceMuted = Color(0xFF1A1D20),          // [INFERRED] dark user bubble / sheet / settings bg
    cardBackground = Color(0xFF16191C),        // [INFERRED] dark elevated card surface
    primaryText = Color(0xFFF3F5F6),           // [INFERRED] dark near-white primary text
    secondaryText = Color(0xFF9BA0A5),         // [INFERRED] dark secondary label gray
    primaryBlue = Color(0xFF3B8BF7),           // [INFERRED] accessible primary blue on dark
    onPrimaryBlue = Color(0xFFFFFFFF),         // [INFERRED]
    destructiveRed = Color(0xFFFF5A6E),        // [INFERRED] accessible crimson red on dark
    hairlineBorder = Color(0xFF2C3136),        // [INFERRED] dark 1dp hairline border
    grabHandle = Color(0xFF3E444B),            // [INFERRED] dark grab handle
    scrimColor = Color(0x73000000),            // [INFERRED] 45% black scrim in dark mode
    markPurpleStart = Color(0xFFC24DFB),       // [OBSERVED]
    markVioletMid = Color(0xFF8842FA),         // [OBSERVED]
    markBlueEnd = Color(0xFF4B3EF6),           // [OBSERVED]
    composerShadowColor = Color(0x44000000),   // [INFERRED]
    headerHaloShadowColor = Color(0x28000000), // [INFERRED]
    stopHaloBackground = Color(0xFF1E324D)     // [INFERRED]
)

val LocalBuildXColors = staticCompositionLocalOf { LightBuildXColors }

/**
 * Bundled open-license fonts: Plus Jakarta Sans (Latin geometric grotesque) + Noto Sans Arabic.
 */
val BuildXFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.noto_sans_arabic, FontWeight.Normal),
    Font(R.font.noto_sans_arabic, FontWeight.Medium),
    Font(R.font.noto_sans_arabic, FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic, FontWeight.Bold)
)

val BuildXTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

object BuildXTheme {
    val colors: BuildXColors
        @Composable
        get() = LocalBuildXColors.current
}

@Composable
fun BuildXTheme(
    appearance: AppAppearance = AppAppearance.AUTOMATIC,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appearance) {
        AppAppearance.AUTOMATIC -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    val buildXColors = if (useDark) DarkBuildXColors else LightBuildXColors

    val materialColorScheme = if (useDark) {
        darkColorScheme(
            primary = buildXColors.primaryBlue,
            onPrimary = buildXColors.onPrimaryBlue,
            background = buildXColors.pageBackground,
            onBackground = buildXColors.primaryText,
            surface = buildXColors.cardBackground,
            onSurface = buildXColors.primaryText,
            surfaceVariant = buildXColors.surfaceMuted,
            onSurfaceVariant = buildXColors.secondaryText,
            error = buildXColors.destructiveRed
        )
    } else {
        lightColorScheme(
            primary = buildXColors.primaryBlue,
            onPrimary = buildXColors.onPrimaryBlue,
            background = buildXColors.pageBackground,
            onBackground = buildXColors.primaryText,
            surface = buildXColors.cardBackground,
            onSurface = buildXColors.primaryText,
            surfaceVariant = buildXColors.surfaceMuted,
            onSurfaceVariant = buildXColors.secondaryText,
            error = buildXColors.destructiveRed
        )
    }

    CompositionLocalProvider(LocalBuildXColors provides buildXColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = BuildXTypography,
            content = content
        )
    }
}

/**
 * Custom diffuse soft shadow modifier matching the reference recording's non-Material elevation look.
 */
fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp
): Modifier = this.drawBehind {
    val shadowColorArgb = color.toArgb()
    val transparentArgb = color.copy(alpha = 0f).toArgb()
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparentArgb
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColorArgb
        )
        canvas.drawRoundRect(
            0f,
            0f,
            size.width,
            size.height,
            cornerRadius.toPx(),
            cornerRadius.toPx(),
            paint
        )
    }
}

/**
 * Resolves text direction from the first strong directional character in [text].
 * Fixes the reference video defect where Arabic was left-aligned:
 * Arabic/Hebrew characters return true (RTL), Latin/Cyrillic return false (LTR).
 */
fun isRtlText(text: String): Boolean {
    for (ch in text) {
        val dir = Character.getDirectionality(ch)
        when (dir) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
        }
    }
    return false
}
