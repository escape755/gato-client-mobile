package com.gato.client.game.module.visual

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.protocol.bedrock.data.camera.CameraEase
import org.cloudburstmc.protocol.bedrock.data.camera.CameraFovInstruction
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket

/**
 * CustomFOV — sets the client's field of view through the camera instruction
 * system (CameraInstructionPacket.fovInstruction), which is not capped like
 * the vanilla FOV slider: the default here (130) is already above the
 * standard 110 maximum.
 *
 * Works client-bound over the relay: the relay instructs the real Minecraft
 * app to apply the FOV, so it behaves on any server. The instruction is
 * re-applied on world join / dimension change (the camera resets then) and
 * cleared when the module is disabled (returns to the vanilla slider value).
 */
class CustomFovModule : Module("CustomFOV", ModuleCategory.Visual) {

    // --- Settings: FOV above the vanilla slider cap by default ---
    private var fov by floatValue("FOV", 130f, 1f..360f)
    private var easeTime by floatValue("Ease Time", 0f, 0f..5f)

    override fun onEnabled() {
        super.onEnabled()
        if (isSessionCreated) sendFov(fov)
    }

    override fun onDisabled() {
        super.onDisabled()
        if (isSessionCreated) clearFov()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isSessionCreated) return
        val packet = interceptablePacket.packet as? BedrockPacket ?: return

        // the camera resets on world join / dimension change — re-assert
        if (!isEnabled) return
        when (packet) {
            is StartGamePacket -> sendFov(fov)
            is ChangeDimensionPacket -> sendFov(fov)
        }
    }

    private fun sendFov(value: Float) {
        session.clientBound(CameraInstructionPacket().apply {
            fovInstruction = CameraFovInstruction(
                value,
                easeTime,
                if (easeTime > 0f) CameraEase.LINEAR else null,
                false
            )
        })
    }

    private fun clearFov() {
        session.clientBound(CameraInstructionPacket().apply {
            fovInstruction = CameraFovInstruction(0f, 0f, null, true)
        })
    }
}
