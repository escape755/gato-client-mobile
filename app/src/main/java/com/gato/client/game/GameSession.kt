package com.gato.client.game

import com.gato.client.application.AppContext
import com.gato.client.game.entity.LocalPlayer
import com.gato.client.game.world.Level
import com.gato.relay.GatoRelaySession
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

@Suppress("MemberVisibilityCanBePrivate")
class GameSession(val gatoRelaySession: GatoRelaySession) : ComposedPacketHandler {

    val localPlayer = LocalPlayer(this)

    val level = Level(this)

    private val versionName by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContext.instance.packageManager.getPackageInfo(
            AppContext.instance.packageName, 0
        ).versionName
    }

    fun clientBound(packet: BedrockPacket) {
        gatoRelaySession.clientBound(packet)
    }

    fun serverBound(packet: BedrockPacket) {
        gatoRelaySession.serverBound(packet)
    }

    override fun beforePacketBound(packet: BedrockPacket): Boolean {
        localPlayer.onPacketBound(packet)
        level.onPacketBound(packet)

        val interceptablePacket = InterceptablePacket(packet)

        for (module in ModuleManager.modules) {
            module.beforePacketBound(interceptablePacket)
        }

        if (interceptablePacket.isIntercepted) {
            return true
        }

       // displayClientMessage("[Gato Client Mobile] $versionName", TextPacket.Type.TIP)

        return false
    }

    override fun afterPacketBound(packet: BedrockPacket) {
        for (module in ModuleManager.modules) {
            module.afterPacketBound(packet)
        }
    }

    override fun onDisconnect(reason: String) {
        localPlayer.onDisconnect()
        level.onDisconnect()

        for (module in ModuleManager.modules) {
            module.onDisconnect(reason)
        }
    }

    fun displayClientMessage(message: String, type: TextPacket.Type = TextPacket.Type.RAW) {
        val textPacket = TextPacket()
        textPacket.type = type
        textPacket.isNeedsTranslation = false
        textPacket.sourceName = ""
        textPacket.setMessage(message)
        textPacket.xuid = ""
        textPacket.platformChatId = ""
        textPacket.setFilteredMessage("")
        clientBound(textPacket)
    }

}