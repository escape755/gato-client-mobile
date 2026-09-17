package com.gato.relay.listener

import com.gato.relay.GatoRelay
import com.gato.relay.GatoRelaySession
import com.gato.relay.definition.Definitions
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec
import org.cloudburstmc.protocol.bedrock.codec.v729.serializer.InventoryContentSerializer_v729
import org.cloudburstmc.protocol.bedrock.codec.v729.serializer.InventorySlotSerializer_v729
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.packet.*

@Suppress("MemberVisibilityCanBePrivate")
class AutoCodecPacketListener(
    val gatoRelaySession: GatoRelaySession,
    val patchCodec: Boolean = true
) : GatoRelayPacketListener {

    companion object {

        private val protocols = AutoCodecPacketListener::class.java
            .getResourceAsStream("/protocol_mapping.txt")
            ?.bufferedReader()
            ?.use {
                it.readLines()
                    .map { version -> version.toInt() }
            } ?: emptyList()

        private fun fetchCodecIfClosest(
            protocolVersion: Int
        ): BedrockCodec {
            val closestProtocolVersion = protocols.findLast { currentProtocolVersion ->
                protocolVersion >= currentProtocolVersion
            } ?: GatoRelay.DefaultCodec.protocolVersion

            val bedrockCodecClass = Class.forName("org.cloudburstmc.protocol.bedrock.codec.v$closestProtocolVersion.Bedrock_v$closestProtocolVersion")
            val bedrockCodecField = bedrockCodecClass.getDeclaredField("CODEC")
            bedrockCodecField.isAccessible = true

            return bedrockCodecField.get(null) as BedrockCodec
        }

    }

    private fun patchCodecIfNeeded(codec: BedrockCodec): BedrockCodec {
        return if (patchCodec && codec.protocolVersion > 729) {
            codec.toBuilder()
                .updateSerializer(InventoryContentPacket::class.java, InventoryContentSerializer_v729.INSTANCE)
                .updateSerializer(InventorySlotPacket::class.java, InventorySlotSerializer_v729.INSTANCE)
                .build()
        } else {
            codec
        }
    }

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is RequestNetworkSettingsPacket) {
            val protocolVersion = packet.protocolVersion
            val bedrockCodec = patchCodecIfNeeded(fetchCodecIfClosest(protocolVersion))
            println("Fetched bedrock codec: ${bedrockCodec.protocolVersion}")

            gatoRelaySession.server.codec = bedrockCodec
            gatoRelaySession.server.peer.codecHelper.apply {
                itemDefinitions = Definitions.itemDefinitions
                blockDefinitions = Definitions.blockDefinitions
                cameraPresetDefinitions = Definitions.cameraPresetDefinitions
                encodingSettings = EncodingSettings.builder()
                    .maxListSize(Int.MAX_VALUE)
                    .maxByteArraySize(Int.MAX_VALUE)
                    .maxNetworkNBTSize(Int.MAX_VALUE)
                    .maxItemNBTSize(Int.MAX_VALUE)
                    .maxStringLength(Int.MAX_VALUE)
                    .build()
            }

            val networkSettingsPacket = NetworkSettingsPacket()
            networkSettingsPacket.compressionThreshold = 0
            networkSettingsPacket.compressionAlgorithm = PacketCompressionAlgorithm.ZLIB

            gatoRelaySession.clientBoundImmediately(networkSettingsPacket)
            gatoRelaySession.server.setCompression(PacketCompressionAlgorithm.ZLIB)
            println("Client enabled compression: ZLIB")
            return true
        }
        return false
    }

}