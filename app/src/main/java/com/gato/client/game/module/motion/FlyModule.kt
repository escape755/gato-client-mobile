package com.gato.client.game.module.motion

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of the GatoClient (PC) Fly module — exact behavior and settings.
 *
 * PC reference: Client/Managers/ModuleManager/Modules/Category/Movement/Fly.cpp
 * + Utils/Minecraft/FlyUtil.h (irregularBob).
 *
 * The PC module rewrites the player's stateVector velocity every game tick and
 * calls lerpMotion for the resulting vector. Over the relay the equivalent is
 * injecting a SetEntityMotionPacket client-bound on every PlayerAuthInputPacket
 * (one per client tick): the real client integrates that motion, its next auth
 * input carries the moved position, and the server sees the same trajectory.
 */
class FlyModule : Module("Fly", ModuleCategory.Motion) {

    // --- Settings: same names, defaults and ranges as the PC module ---
    private var hSpeed by floatValue("HSpeed", 1.0f, 0.2f..3.0f)
    private var vSpeed by floatValue("VSpeed", 0.5f, 0.2f..3.0f)
    private var glide by floatValue("Glide", 0.0f, -0.15f..0.0f)
    private var dynamic by boolValue("Dynamic", false)
    private var hDynamic by floatValue("X Dynamic", 1.0f, 0.5f..2.0f)
    private var vDynamic by floatValue("Y Dynamic", 1.0f, 0.5f..2.0f)
    private var antiKick by boolValue("AntiKick", true)
    private var kickInterval by floatValue("Kick Interval", 1.2f, 0.5f..3.0f)
    private var kickAmount by floatValue("Kick Amount", 0.06f, 0.01f..0.3f)

    init {
        // Visibility rules copied from the PC registerSetting lambdas
        getValue("Glide")?.visibleIf = { !antiKick }
        getValue("X Dynamic")?.visibleIf = { dynamic }
        getValue("Y Dynamic")?.visibleIf = { dynamic }
        getValue("Kick Interval")?.visibleIf = { antiKick }
        getValue("Kick Amount")?.visibleIf = { antiKick }
    }

    private var kickTimer = 0f

    /**
     * Bob senoidal con fase modulada y amplitud ligeramente variable: el
     * patron de un jugador real nunca es un seno limpio con periodo fijo.
     * (FlyUtil::irregularBob, identical constants.)
     */
    private fun irregularBob(phaseTime: Float, amount: Float, interval: Float): Float {
        val ph = (phaseTime / interval) * 6.2831853f + 0.35f * sin(phaseTime * 0.73f)
        val amp = amount * (1f + 0.25f * sin(phaseTime * 0.19f))
        return sin(ph) * amp
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return

        val packet = interceptablePacket.packet as? PlayerAuthInputPacket ?: return

        // Touch input flags — mobile equivalents of WASD / Space / Shift
        val input = packet.inputData
        val isForward = input.contains(PlayerAuthInputData.UP)
        val isBackward = input.contains(PlayerAuthInputData.DOWN)
        val isLeft = input.contains(PlayerAuthInputData.LEFT)
        val isRight = input.contains(PlayerAuthInputData.RIGHT)
        val isUp = input.contains(PlayerAuthInputData.JUMPING)
        val isDown = input.contains(PlayerAuthInputData.SNEAKING)

        // velocity = Vec3(0, 0, 0) — computed from scratch every tick
        var velocityY = 0f

        if (antiKick && !isUp && !isDown) {
            kickTimer += 1f / 20f
            velocityY = irregularBob(kickTimer, kickAmount, kickInterval)
        } else {
            velocityY -= -glide
        }

        var currentHSpeed = hSpeed
        var currentVSpeed = vSpeed

        if (dynamic && isUp) {
            currentHSpeed = hDynamic
            currentVSpeed = vDynamic
        }

        if (isUp) velocityY += currentVSpeed
        if (isDown) velocityY -= currentVSpeed

        val moveX = (if (isRight) 1 else 0) + (if (isLeft) -1 else 0)
        val moveY = (if (isForward) 1 else 0) + (if (isBackward) -1 else 0)

        val motionVec: Vector3f = if (moveX != 0 || moveY != 0) {
            var yaw = packet.rotation.y
            val angleRad = atan2(moveX.toFloat(), moveY.toFloat())
            val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
            yaw += angleDeg

            val calcYaw = Math.toRadians((yaw + 90f).toDouble()).toFloat()
            // lerpMotion(Vec3(cos(calcYaw) * hSpeed, velocity.y, sin(calcYaw) * hSpeed))
            Vector3f.from(cos(calcYaw) * currentHSpeed, velocityY, sin(calcYaw) * currentHSpeed)
        } else {
            // lerpMotion(Vec3(0, velocity.y, 0)) — and the always-on stateVector
            // zeroing keeps the player hovering when idle (0, bob/glide, 0)
            Vector3f.from(0f, velocityY, 0f)
        }

        session.clientBound(SetEntityMotionPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            motion = motionVec
        })
    }
}
