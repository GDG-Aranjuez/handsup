package es.gdgaranjuez.handsup.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import es.gdgaranjuez.handsup.domain.Branding
import es.gdgaranjuez.handsup.domain.isHexColor
import es.gdgaranjuez.handsup.domain.onColor
import es.gdgaranjuez.handsup.domain.readableOn

fun hex(value: String): Color {
    val h = value.removePrefix("#")
    return Color(
        red = h.substring(0, 2).toInt(16) / 255f,
        green = h.substring(2, 4).toInt(16) / 255f,
        blue = h.substring(4, 6).toInt(16) / 255f,
    )
}

/** Colours the components need beyond Material's scheme, mirroring the web tokens. */
@Immutable
data class Tokens(
    val brand: Color,
    val onBrand: Color,
    val brandText: Color,
    val accentText: Color,
    val surface2: Color,
    val line: Color,
    val muted: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val live: Color,
)

val LocalTokens = staticCompositionLocalOf<Tokens> { error("no tokens") }

private const val LIGHT_SURFACE = "#ffffff"
private const val DARK_SURFACE = "#14141a"

@Composable
fun HandsupTheme(branding: Branding, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val primaryHex = if (isHexColor(branding.primary)) branding.primary else Branding.DEFAULT_PRIMARY
    val accentHex = if (isHexColor(branding.accent)) branding.accent else Branding.DEFAULT_ACCENT
    val surface = if (dark) DARK_SURFACE else LIGHT_SURFACE
    val surface2 = if (dark) "#1d1d26" else "#f4f4f7"
    val ink = if (dark) "#f3f3f7" else "#14141a"
    val tokens = Tokens(
        brand = hex(primaryHex),
        onBrand = hex(onColor(primaryHex)),
        brandText = hex(readableOn(primaryHex, surface)),
        accentText = hex(readableOn(accentHex, surface)),
        surface2 = hex(surface2),
        line = hex(if (dark) "#2d2d3a" else "#e3e3ea"),
        muted = hex(if (dark) "#a6a6b5" else "#5d5d6b"),
        ok = hex(if (dark) "#55d08b" else "#17794a"),
        warn = hex(if (dark) "#f0b95a" else "#8a5a00"),
        danger = hex(if (dark) "#ff8a85" else "#c0262d"),
        live = hex("#d6282f"),
    )
    val scheme: ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = tokens.brand,
        onPrimary = tokens.onBrand,
        secondary = hex(accentHex),
        onSecondary = hex(onColor(accentHex)),
        background = hex(surface),
        onBackground = hex(ink),
        surface = hex(surface),
        onSurface = hex(ink),
        surfaceVariant = tokens.surface2,
        onSurfaceVariant = tokens.muted,
        outline = tokens.line,
        outlineVariant = tokens.line,
        error = tokens.danger,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}

val tokens: Tokens @Composable get() = LocalTokens.current
