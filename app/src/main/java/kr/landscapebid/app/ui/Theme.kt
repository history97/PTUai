package kr.landscapebid.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 웹 화면(index.html)의 CSS 변수와 같은 색. 밝은 모드 / 어두운 모드. */
class Palette(
    val bg: Color, val paper: Color, val ink: Color, val muted: Color, val line: Color,
    val moss: Color, val mossSoft: Color, val ochre: Color, val ochreSoft: Color,
    val brick: Color, val brickSoft: Color, val onMoss: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFEEF1EC), paper = Color(0xFFFFFFFF), ink = Color(0xFF1D2B25), muted = Color(0xFF5B6862), line = Color(0xFFD3DBD5),
    moss = Color(0xFF2E6B4F), mossSoft = Color(0xFFDCE9E1), ochre = Color(0xFFA97A1F), ochreSoft = Color(0xFFF4E7C8),
    brick = Color(0xFFA5452F), brickSoft = Color(0xFFF3DDD6), onMoss = Color.White,
)
val DarkPalette = Palette(
    bg = Color(0xFF131915), paper = Color(0xFF1B231F), ink = Color(0xFFE4EBE6), muted = Color(0xFF98A69E), line = Color(0xFF314039),
    moss = Color(0xFF72B791), mossSoft = Color(0xFF223A2E), ochre = Color(0xFFD8AA4F), ochreSoft = Color(0xFF3A3120),
    brick = Color(0xFFDD8470), brickSoft = Color(0xFF40251F), onMoss = Color(0xFF0F1A14),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** 어디서든 P.moss, P.ink 처럼 쓴다 */
val P: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** 사정율 차트의 최빈(빨강) / 평균(파랑) 표시 색 (웹 MK_COL) */
val MarkMode = Color(0xFFC0392B)
val MarkMean = Color(0xFF2F6FB0)

@Composable
fun LandscapeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val pal = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = pal.moss, onPrimary = pal.onMoss, background = pal.bg, surface = pal.paper, onSurface = pal.ink, onBackground = pal.ink)
    } else {
        lightColorScheme(primary = pal.moss, onPrimary = pal.onMoss, background = pal.bg, surface = pal.paper, onSurface = pal.ink, onBackground = pal.ink)
    }
    CompositionLocalProvider(LocalPalette provides pal) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
