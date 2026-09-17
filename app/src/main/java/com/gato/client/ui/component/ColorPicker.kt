package com.gato.client.ui.component

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Full-control color editor: hue / saturation / value / alpha sliders, a live
 * preview swatch and a hex field. Used by the interface personalization editor
 * for every theme slot.
 */
@Composable
fun ColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = FloatArray(3).also {
        AndroidColor.RGBToHSV(
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt(),
            it
        )
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    var hexInput by remember {
        mutableStateOf(Integer.toHexString(initialColor.toArgb()).padStart(8, '0').uppercase())
    }
    var hexError by remember { mutableStateOf(false) }

    fun currentColor(): Color {
        val c = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        return Color((c and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24))
    }

    fun pushColor() = onColorChange(currentColor())

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // live preview
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(currentColor(), RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            )
            Text(
                "#${Integer.toHexString(currentColor().toArgb()).padStart(8, '0').uppercase()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // hue slider (rainbow)
        Slider(
            value = hue,
            onValueChange = { hue = it; pushColor() },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
        )

        // saturation slider (white -> full hue)
        Slider(
            value = saturation,
            onValueChange = { saturation = it; pushColor() },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White,
                            Color.fromHsv(hue, 1f, value)
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
        )

        // value slider (black -> full hue/sat)
        Slider(
            value = value,
            onValueChange = { value = it; pushColor() },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black, Color.fromHsv(hue, saturation, 1f))
                    ),
                    RoundedCornerShape(8.dp)
                )
        )

        // alpha slider (transparent -> full color)
        Slider(
            value = alpha,
            onValueChange = { alpha = it; pushColor() },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            currentColor().copy(alpha = 0f),
                            currentColor().copy(alpha = 1f)
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
        )

        // hex input
        OutlinedTextField(
            value = hexInput,
            onValueChange = { text ->
                hexInput = text
                val clean = text.removePrefix("#").trim()
                val argb = when (clean.length) {
                    6 -> runCatching { java.lang.Long.parseLong("FF$clean", 16).toInt() }.getOrNull()
                    8 -> runCatching { java.lang.Long.parseLong(clean, 16).toInt() }.getOrNull()
                    else -> null
                }
                if (argb != null) {
                    hexError = false
                    val c = Color(argb)
                    val hsv = FloatArray(3).also {
                        AndroidColor.RGBToHSV(
                            (c.red * 255).toInt(),
                            (c.green * 255).toInt(),
                            (c.blue * 255).toInt(),
                            it
                        )
                    }
                    hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; alpha = c.alpha
                    onColorChange(c)
                } else {
                    hexError = true
                }
            },
            label = { Text("Hex (AARRGGBB)") },
            isError = hexError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Color.Companion.fromHsv(hue: Float, saturation: Float, value: Float): Color {
    val c = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return Color((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)
}
