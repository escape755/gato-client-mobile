package com.gato.client.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.content.edit
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Fully user-configurable color theme for the whole client (app + click gui).
 *
 * Every slot is a named color the user can edit from Settings (HSV/hex picker),
 * persisted automatically, and exportable/importable as JSON so a configured
 * theme can be shared as text.
 */
object ColorTheme {

    class Slot(val key: String, val label: String, val default: Color) {
        var value by mutableStateOf(default)

        val argb: Int get() = value.toArgb()
    }

    // ===== slots (label shown in the editor) =====
    val accent = Slot("accent", "Primario", Color(249, 168, 212))
    val accentSoft = Slot("accentSoft", "Secundario", Color(252, 208, 232))
    val fondo = Slot("fondo", "Fondo", Color(20, 12, 32))
    val panel = Slot("panel", "Panel", Color(36, 22, 58))
    val borde = Slot("borde", "Borde", Color(88, 45, 150))
    val texto = Slot("texto", "Texto", Color(245, 235, 255))
    val textoSec = Slot("textoSec", "Texto secundario", Color(200, 170, 230))
    val moduloOn = Slot("moduloOn", "Módulo encendido", Color(168, 85, 247, 190))
    val moduloOff = Slot("moduloOff", "Módulo apagado", Color(60, 40, 90, 160))
    val resaltado = Slot("resaltado", "Resaltado / hover", Color(52, 33, 84))

    val all: List<Slot> = listOf(
        accent, accentSoft, fondo, panel, borde,
        texto, textoSec, moduloOn, moduloOff, resaltado
    )

    private const val PREFS_NAME = "theme_settings"
    private var prefs: SharedPreferences? = null
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefs ?: return
        all.forEach { slot ->
            val saved = p.getInt(slot.key, Int.MIN_VALUE)
            if (saved != Int.MIN_VALUE) slot.value = Color(saved)
        }
    }

    fun setSlot(slot: Slot, color: Color) {
        slot.value = color
        prefs?.edit { putInt(slot.key, color.toArgb()) }
    }

    fun resetAll() {
        all.forEach { setSlot(it, it.default) }
    }

    // ===== config export / import (shareable as text) =====

    fun exportJson(): String {
        val json = JsonObject()
        json.addProperty("_type", "gato_theme")
        json.addProperty("_version", 1)
        all.forEach { slot -> json.addProperty(slot.key, slot.argb) }
        return json.toString()
    }

    /** @return the number of slots applied, or -1 if the config is not a valid theme. */
    fun importJson(text: String): Int {
        return runCatching {
            val json = JsonParser.parseString(text).asJsonObject
            if (json.has("_type") && json.get("_type").asString != "gato_theme") return -1
            var applied = 0
            all.forEach { slot ->
                if (json.has(slot.key)) {
                    setSlot(slot, Color(json.get(slot.key).asInt))
                    applied++
                }
            }
            applied
        }.getOrDefault(-1)
    }

    // ===== color helpers =====

    fun lighten(color: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        fun ch(c: Float) = c + (1f - c) * f
        return Color(ch(color.red), ch(color.green), ch(color.blue), color.alpha)
    }

    fun darken(color: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(color.red * (1f - f), color.green * (1f - f), color.blue * (1f - f), color.alpha)
    }

    fun mix(a: Color, b: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            a.red + (b.red - a.red) * f,
            a.green + (b.green - a.green) * f,
            a.blue + (b.blue - a.blue) * f,
            a.alpha + (b.alpha - a.alpha) * f
        )
    }

    fun onColor(background: Color): Color =
        if (background.luminance() > 0.5f) Color(20, 12, 32, 255) else Color(245, 235, 255, 255)
}
