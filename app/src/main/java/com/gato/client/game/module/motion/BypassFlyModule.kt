package com.gato.client.game.module.motion

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the GatoClient (PC) BypassFly module — exact behavior and settings.
 *
 * PC reference: Client/Managers/ModuleManager/Modules/Category/Movement/BypassFly.cpp
 *
 * Like the PC module (pure velocity + lerpMotion per tick, no packets of its own),
 * the relay equivalent injects a client-bound SetEntityMotionPacket on every
 * PlayerAuthInputPacket (one per client tick) — same mapping as FlyModule.
 *
 * Deviation: the PC 5x5x5 BlockSource scan (water ids 8/9 first pass, solid
 * non-air second pass) requires voxel access the relay does not have; the
 * auth input's HORIZONTAL_COLLISION / AUTO_JUMPING_IN_WATER flags are used
 * as the per-tick "touching terrain/water" signal that caps speed to 2.0.
 */
class BypassFlyModule : Module("BypassFly", ModuleCategory.Motion) {

    // --- Settings: same names, defaults and ranges as the PC module ---
    private var initialH by floatValue("Initial Horizontal", 2.42f, 0.1f..3.0f)
    private var initialV by floatValue("Initial Vertical", 1.62f, 0.1f..3.0f)
    private var initBypH by floatValue("Initial Bypass H", 1.1585f, 0.1f..3.0f)
    private var initBypV by floatValue("Initial Bypass V", 1.086f, 0.1f..3.0f)
    private var finalH by floatValue("Final Horizont", 2.18f, 0.1f..3.0f)
    private var finalV by floatValue("Final Vertical", 1.42f, 0.1f..3.0f)
    private var finalBypH by floatValue("Final Bypass H", 1.09f, 0.1f..3.0f)
    private var finalBypV by floatValue("Final Bypass V", 1.05f, 0.1f..3.0f)
    private var glide by floatValue("Glide", -0.1266f, -0.2f..0.2f)
    private var releaseDelay by floatValue("Release Delay", 0.09f, 0.0f..0.5f)
    private var descentSpeed by floatValue("Descent Speed", 4.2997f, 0.1f..5.0f)
    private var descentH by floatValue("Descent Horizontal", 1.1f, 0.1f..3.0f)
    private var descensoAlternado by boolValue("Descenso Alternado", true)

    private var bypassPhase = false
    private var phaseStartNs = 0L
    private var altTick = 0

    override fun onEnabled() {
        super.onEnabled()
        bypassPhase = false
    }

    override fun onDisabled() {
        super.onDisabled()
        bypassPhase = false
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return

        val packet = interceptablePacket.packet as? PlayerAuthInputPacket ?: return

        val input = packet.inputData
        val pos = packet.position

        // 1) Initial->Final blend by distance to the world origin (5.5M blocks)
        var t = sqrt(pos.z * pos.z + pos.x * pos.x) / 5500000f
        if (t > 1f) t = 1f
        val bypH = initBypH + (finalBypH - initBypH) * t
        val normH = initialH + (finalH - initialH) * t
        val normV = initialV + (finalV - initialV) * t
        val bypV = initBypV + (finalBypV - initBypV) * t

        // 2) Input flags — mobile equivalents of WASD / Space / Shift
        val w = input.contains(PlayerAuthInputData.UP)
        val a = input.contains(PlayerAuthInputData.LEFT)
        val s = input.contains(PlayerAuthInputData.DOWN)
        val d = input.contains(PlayerAuthInputData.RIGHT)
        val space = input.contains(PlayerAuthInputData.JUMPING)
        val shift = input.contains(PlayerAuthInputData.SNEAKING)
        val moving = w || a || s || d

        // 3) Bypass phase machine + Release Delay (persists after release)
        var bypassActive = false
        val now = System.nanoTime()
        if (space && moving) {
            bypassPhase = true
            phaseStartNs = now
            bypassActive = true
        } else if (bypassPhase) {
            bypassActive = true
            val secs = (now - phaseStartNs) / 1e9f
            if (secs >= releaseDelay) {
                bypassPhase = false
                bypassActive = false
            }
        }

        // 4) Vertical: written always (with or without WASD)
        val vy: Float = if (shift) {
            -descentSpeed
        } else {
            glide + (if (space) (if (bypassActive) bypV else normV) else 0f)
        }

        if (!moving) {
            // velocity = (0, vy, 0) on the stateVector; no lerpMotion on PC
            sendMotion(0f, vy, 0f)
            return
        }

        // 5) Direction: yaw + discrete WASD offset (0/±45/±90/±135/180); +90 = dx cos / dz sin
        val yaw = packet.rotation.y
        val off = if (w) {
            (if (a) -45f else (if (d) 45f else 0f))
        } else if (s) {
            (if (a) -135f else (if (d) 135f else 180f))
        } else {
            (if (a) -90f else (if (d) 90f else 0f))
        }
        val rad = Math.toRadians((yaw + off + 90f).toDouble())

        // 6) Terrain proximity — substitute for the PC 5x5x5 block scan
        val hit = input.contains(PlayerAuthInputData.HORIZONTAL_COLLISION) ||
                input.contains(PlayerAuthInputData.AUTO_JUMPING_IN_WATER)

        // 7) Horizontal speed: dedicated value while descending (server drag), capped near terrain
        val speed = when {
            shift -> descentH
            hit -> 2.0f
            bypassActive -> bypH
            else -> normH
        }

        // 8) Apply — alternating descent keeps each packet under the server speed limit
        if (shift && descensoAlternado) {
            if (altTick++ % 2 == 0) {
                sendMotion(cos(rad).toFloat() * speed, glide, sin(rad).toFloat() * speed)
            } else {
                sendMotion(0f, -descentSpeed, 0f)
            }
            return
        }
        sendMotion(cos(rad).toFloat() * speed, vy, sin(rad).toFloat() * speed)
    }

    private fun sendMotion(x: Float, y: Float, z: Float) {
        session.clientBound(SetEntityMotionPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            motion = Vector3f.from(x, y, z)
        })
    }
}
