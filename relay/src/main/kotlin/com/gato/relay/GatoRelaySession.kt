package com.gato.relay

import com.gato.relay.listener.GatoRelayPacketListener
import io.netty.util.internal.PlatformDependent
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockPeer
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket
import java.util.*


class GatoRelaySession internal constructor(
    peer: BedrockPeer,
    subClientId: Int,
    val gatoRelay: GatoRelay
) {

    val server = ServerSession(peer, subClientId)

    var client: ClientSession? = null
        internal set(value) {
            value?.let {
                it.codec = server.codec
                it.peer.codecHelper.blockDefinitions = server.peer.codecHelper.blockDefinitions
                it.peer.codecHelper.itemDefinitions = server.peer.codecHelper.itemDefinitions
                it.peer.codecHelper.cameraPresetDefinitions = server.peer.codecHelper.cameraPresetDefinitions
                it.peer.codecHelper.encodingSettings = server.peer.codecHelper.encodingSettings

                var pair: Pair<BedrockPacket, Boolean>
                while (packetQueue.poll().also { packetPair -> pair = packetPair } != null) {
                    if (pair.second) {
                        it.sendPacketImmediately(pair.first)
                    } else {
                        it.sendPacket(pair.first)
                    }
                }
            }
            field = value
        }

    val listeners: MutableList<GatoRelayPacketListener> = ArrayList()

    private val packetQueue: Queue<Pair<BedrockPacket, Boolean>> = PlatformDependent.newMpscQueue()

    fun clientBound(packet: BedrockPacket) {
        server.sendPacket(packet)
    }

    fun clientBoundImmediately(packet: BedrockPacket) {
        server.sendPacketImmediately(packet)
    }

    fun serverBound(packet: BedrockPacket) {
        if (client != null) {
            client!!.sendPacket(packet)
        } else {
            packetQueue.add(packet to false)
        }
    }

    fun serverBoundImmediately(packet: BedrockPacket) {
        if (client != null) {
            client!!.sendPacketImmediately(packet)
        } else {
            packetQueue.add(packet to true)
        }
    }

    inner class ServerSession(peer: BedrockPeer, subClientId: Int) :
        BedrockServerSession(peer, subClientId) {

        init {
            packetHandler = object : BedrockPacketHandler {
                override fun onDisconnect(reason: CharSequence) {
                    println("Client disconnect: $reason")
                    runCatching {
                        client?.disconnect()
                    }
                    listeners.forEach {
                        runCatching {
                            it.onDisconnect(reason.toString())
                        }
                    }
                }
            }
        }

        override fun onPacket(wrapper: BedrockPacketWrapper) {
            listeners.forEach { listener ->
                try {
                    if (listener.beforeClientBound(wrapper.packet)) {
                        return
                    }
                } catch (e: Throwable) {
                    println("Before client bound error: ${e.stackTraceToString()}")
                }
            }

            val buffer = wrapper.packetBuffer
                .retainedSlice()
                .skipBytes(wrapper.headerLength)

            val unknownPacket = UnknownPacket()
            unknownPacket.payload = buffer
            unknownPacket.packetId = wrapper.packetId
            serverBound(unknownPacket)

            listeners.forEach { listener ->
                try {
                    listener.afterClientBound(wrapper.packet)
                } catch (e: Throwable) {
                    println("After client bound error: ${e.stackTraceToString()}")
                }
            }
        }

    }

    inner class ClientSession(peer: BedrockPeer, subClientId: Int) :
        BedrockClientSession(peer, subClientId) {

        init {
            packetHandler = object : BedrockPacketHandler {

                override fun onDisconnect(reason: CharSequence) {
                    println("Server disconnect: $reason")
                    runCatching {
                        server.disconnect()
                    }
                    listeners.forEach {
                        runCatching {
                            it.onDisconnect(reason.toString())
                        }
                    }
                }

            }
        }

        override fun onPacket(wrapper: BedrockPacketWrapper) {
            listeners.forEach { listener ->
                try {
                    if (listener.beforeServerBound(wrapper.packet)) {
                        return
                    }
                } catch (e: Throwable) {
                    println("Before server bound error: ${e.stackTraceToString()}")
                }
            }

            val buffer = wrapper.packetBuffer
                .retainedSlice()
                .skipBytes(wrapper.headerLength)

            val unknownPacket = UnknownPacket()
            unknownPacket.payload = buffer
            unknownPacket.packetId = wrapper.packetId
            clientBound(unknownPacket)

            listeners.forEach { listener ->
                try {
                    listener.afterServerBound(wrapper.packet)
                } catch (e: Throwable) {
                    println("After server bound error: ${e.stackTraceToString()}")
                }
            }
        }

    }

}