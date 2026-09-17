package com.gato.relay.listener

import com.gato.relay.GatoRelaySession
import com.gato.relay.address.GatoAddress
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket

@Suppress("MemberVisibilityCanBePrivate")
class TransferPacketListener(
    val gatoRelaySession: GatoRelaySession
) : GatoRelayPacketListener {

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        if (packet is TransferPacket) {
            val remoteAddress = GatoAddress(packet.address, packet.port)
            val localAddress = gatoRelaySession.gatoRelay.localAddress
            gatoRelaySession.gatoRelay.remoteAddress = remoteAddress
            gatoRelaySession.clientBoundImmediately(TransferPacket().apply {
                address = localAddress.hostName
                port = localAddress.port
            })

            gatoRelaySession.gatoRelay.gatoRelaySession = null
            return true
        }
        return false
    }

}