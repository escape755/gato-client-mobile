package com.gato.client.game.module.misc

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of the GatoClient (PC) Timer module.
 *
 * PC reference: Misc/Timer.cpp — writes the engine timescale
 * (minecraftTimer/minecraftRenderTimer = TPS) directly from memory.
 *
 * Relay mapping: the game's clock cannot be touched from outside the process.
 * The packet-level equivalent implemented here throttles the server-bound
 * PlayerAuthInputPacket stream: with TPS < 20 a fraction (TPS/20) of the
 * auth inputs is dropped, so the server observes the player moving at the
 * scaled rate (slow-down direction). TPS > 20 (the PC default, speed-up)
 * cannot be synthesized over the relay without fabricating positions
 * (that is the Speed module's domain) — full passthrough in that case.
 */
class TimerModule : Module("Timer", ModuleCategory.Misc) {

    // --- Setting: same name, default and range as the PC module ---
    private var tps by intValue("TPS", 24, 0..30)

    private var accumulator = 0f

    override fun onEnabled() {
        super.onEnabled()
        accumulator = 0f
    }

    override fun onDisabled() {
        super.onDisabled()
        accumulator = 0f // restore: full passthrough (PC equivalent: timer back to 20.0)
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return

        val packet = interceptablePacket.packet as? PlayerAuthInputPacket ?: return

        if (tps >= 20) return // speed-up / normal: nothing the relay can do (see class comment)

        // forward exactly tps/20 of the packets (accumulator keeps the fraction)
        accumulator += tps / 20f
        if (accumulator >= 1f) {
            accumulator -= 1f
        } else {
            interceptablePacket.intercept() // drop this auth input
        }
    }
}
