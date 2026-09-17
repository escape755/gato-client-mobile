package com.gato.client.util

import android.content.Context
import com.gato.relay.util.MINECRAFT_GAME_VERSION
import com.gato.relay.util.SUPPORTED_VERSIONS
import com.gato.relay.util.advertisedVersionFor

/**
 * Resolves the Minecraft version the relay must speak/advertise: the settings
 * override ("mc_version_override") or the version installed on this device,
 * snapped to the nearest supported protocol.
 */
object McVersionResolver {

    fun detected(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo("com.mojang.minecraftpe", 0)
        info.versionName?.let { v ->
            val parts = v.split(".")
            if (parts.size >= 3) parts.take(3).joinToString(".") else v
        }
    }.getOrNull()

    fun resolved(context: Context): String {
        val override = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("mc_version_override", "auto") ?: "auto"
        if (override != "auto" && SUPPORTED_VERSIONS.containsKey(override)) return override

        val detected = detected(context)
        return advertisedVersionFor(detected)
            ?: detected?.takeIf { SUPPORTED_VERSIONS.containsKey(it) }
            ?: MINECRAFT_GAME_VERSION
    }
}
