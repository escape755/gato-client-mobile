package com.gato.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material3 schemes built entirely from the user-configurable [ColorTheme]
 * slots — the same values drive the app and the click gui (both consume the
 * MaterialTheme), so one edit restyles everything.
 */
private fun customScheme() = darkColorScheme(
    // Accents
    primary = ColorTheme.accent.value,
    onPrimary = ColorTheme.onColor(ColorTheme.accent.value),
    primaryContainer = ColorTheme.lighten(ColorTheme.accent.value, 0.25f),
    onPrimaryContainer = ColorTheme.texto.value,
    secondary = ColorTheme.accentSoft.value,
    onSecondary = ColorTheme.onColor(ColorTheme.accentSoft.value),
    secondaryContainer = ColorTheme.lighten(ColorTheme.accentSoft.value, 0.2f),
    onSecondaryContainer = ColorTheme.texto.value,
    tertiary = ColorTheme.borde.value,
    onTertiary = ColorTheme.onColor(ColorTheme.borde.value),

    // Surfaces — Fondo / Panel
    background = ColorTheme.fondo.value,
    onBackground = ColorTheme.texto.value,
    surface = ColorTheme.panel.value,
    onSurface = ColorTheme.texto.value,

    // Panel-derived containers
    surfaceVariant = ColorTheme.mix(ColorTheme.panel.value, ColorTheme.texto.value, 0.15f),
    onSurfaceVariant = ColorTheme.textoSec.value,
    surfaceContainerLowest = ColorTheme.darken(ColorTheme.fondo.value, 0.35f),
    surfaceContainerLow = ColorTheme.darken(ColorTheme.panel.value, 0.2f),
    surfaceContainer = ColorTheme.panel.value,
    surfaceContainerHigh = ColorTheme.resaltado.value,
    surfaceContainerHighest = ColorTheme.lighten(ColorTheme.panel.value, 0.12f),
    surfaceBright = ColorTheme.lighten(ColorTheme.panel.value, 0.2f),
    surfaceDim = ColorTheme.darken(ColorTheme.panel.value, 0.35f),
    inverseSurface = ColorTheme.texto.value,
    inverseOnSurface = ColorTheme.panel.value,
    inversePrimary = ColorTheme.darken(ColorTheme.accent.value, 0.25f),

    // Structure — Borde
    outline = ColorTheme.borde.value,
    outlineVariant = ColorTheme.darken(ColorTheme.borde.value, 0.2f)
)

@Composable
fun GatoClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    ColorTheme.load(context)

    // The user-configured scheme drives everything (app + overlay) regardless of
    // the system light/dark setting — the look belongs to the user's config.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> customScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
