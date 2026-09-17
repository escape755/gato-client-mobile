package com.gato.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Port of the GatoClient (PC) Colors module palette
 * (Client/Managers/ModuleManager/Modules/Category/Client/Colors.h/.cpp).
 *
 * RGBA values are kept verbatim (r, g, b, a) so the mobile client uses the
 * exact same palette as the PC client.
 */
object GatoColors {

    // --- Accent colors (Colors.h defaults) ---
    val mainColor = Color(168, 85, 247, 190)
    val primaryColor = Color(168, 85, 247, 175)
    val secondColor = Color(249, 168, 212, 175)

    // --- GUI surface colors (Colors.h defaults) ---
    val backgroundColor = Color(20, 12, 32, 255)      // Fondo
    val panelColor = Color(36, 22, 58, 225)           // Panel
    val borderColor = Color(88, 45, 150, 200)         // Borde
    val guiTextColor = Color(245, 235, 255, 255)      // Texto
    val mutedTextColor = Color(200, 170, 230, 200)    // Texto Secundario
    val hoverTintColor = Color(168, 85, 247, 120)     // Resaltado
    val moduleOnColor = Color(168, 85, 247, 190)      // Modulo Encendido
    val moduleOffColor = Color(60, 40, 90, 160)       // Modulo Apagado
    val bubbleColor = Color(160, 210, 255, 255)       // Burbuja

    // --- Palette presets (Colors.cpp kPalettes) ---
    data class PalettePreset(
        val name: String,
        val primary: Color,
        val secondary: Color
    )

    val palettes: List<PalettePreset> = listOf(
        PalettePreset("Gato", Color(168, 85, 247, 175), Color(249, 168, 212, 175)),
        PalettePreset("Oceano", Color(115, 145, 255, 175), Color(85, 115, 235, 175)),
        PalettePreset("Lila", Color(140, 108, 255, 175), Color(104, 78, 210, 175)),
        PalettePreset("Esmeralda", Color(60, 200, 120, 175), Color(34, 150, 88, 175)),
        PalettePreset("Atardecer", Color(255, 138, 76, 175), Color(214, 70, 110, 175)),
        PalettePreset("Lava", Color(255, 80, 60, 175), Color(180, 30, 40, 175)),
        PalettePreset("Caramelo", Color(255, 110, 180, 175), Color(190, 60, 160, 175)),
        PalettePreset("Hielo", Color(130, 215, 240, 175), Color(80, 160, 205, 175)),
        PalettePreset("Neon", Color(60, 255, 170, 175), Color(150, 60, 255, 175)),
        PalettePreset("Monocromo", Color(200, 200, 200, 175), Color(90, 90, 90, 175)),
    )
}
