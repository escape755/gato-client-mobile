package com.gato.relay.listener

import com.gato.relay.GatoRelaySession
import com.gato.relay.util.AuthUtils
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import org.cloudburstmc.protocol.bedrock.util.JsonUtils
import org.jose4j.json.JsonUtil
import org.jose4j.json.internal.json_simple.JSONObject
import org.jose4j.jws.JsonWebSignature
import java.security.KeyPair


@Suppress("MemberVisibilityCanBePrivate")
class OfflineLoginPacketListener(
    val gatoRelaySession: GatoRelaySession,
    val keyPair: KeyPair = DefaultKeyPair
) : GatoRelayPacketListener {

    companion object {

        val DefaultKeyPair: KeyPair = EncryptionUtils.createKeyPair()

    }

    private var chain: List<String>? = null

    private var extraData: JSONObject? = null

    private var skinData: JSONObject? = null

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is LoginPacket) {
            chain = when (val authPayload = packet.authPayload) {
                is CertificateChainPayload -> authPayload.chain
                else -> throw IllegalStateException("Unsupported auth payload: ${authPayload?.getAuthType()}")
            }
            extraData =
                JSONObject(
                    JsonUtils.childAsType(
                        EncryptionUtils.validateChain(chain).rawIdentityClaims(),
                        "extraData",
                        Map::class.java
                    )
                )

            println("Handle offline login data")

            println("[AUTH-DIAG] login path: OFFLINE (no account selected)")
            runCatching {
                val p = packet.authPayload
                when (p) {
                    is org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload ->
                        println("[AUTH-DIAG] client auth: CertificateChainPayload type=${p.authType} chainSize=${p.chain.size}")
                    is org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload ->
                        println("[AUTH-DIAG] client auth: TokenPayload type=${p.authType} tokenHead=${p.token.take(60)}")
                    else -> println("[AUTH-DIAG] client auth: ${p?.javaClass?.name}")
                }
            }

            val jws = JsonWebSignature()
            jws.compactSerialization = packet.clientJwt

            skinData = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))
            connectServer()
            return true
        }
        return false
    }

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        if (packet is NetworkSettingsPacket) {
            val threshold = packet.compressionThreshold
            if (threshold > 0) {
                gatoRelaySession.client!!.setCompression(packet.compressionAlgorithm)
                println("Compression threshold set to $threshold")
            } else {
                gatoRelaySession.client!!.setCompression(PacketCompressionAlgorithm.NONE)
                println("Compression threshold set to 0")
            }

            try {
                val chain = AuthUtils.fetchOfflineChain(keyPair, extraData!!, chain!!)
                val skinData = AuthUtils.fetchOfflineSkinData(keyPair, skinData!!)

                println("[AUTH-DIAG] sending offline chain size=${chain.size}")
                val loginPacket = LoginPacket()
                loginPacket.protocolVersion = gatoRelaySession.server.codec.protocolVersion
                loginPacket.authPayload = CertificateChainPayload(chain, AuthType.SELF_SIGNED)
                loginPacket.clientJwt = skinData
                gatoRelaySession.serverBoundImmediately(loginPacket)

                println("Login success")
            } catch (e: Throwable) {
                gatoRelaySession.clientBound(DisconnectPacket().apply {
                    setKickMessage(e.toString())
                })
                println("Login failed: $e")
            }

            return true
        }
        return super.beforeServerBound(packet)
    }

    private fun connectServer() {
        gatoRelaySession.gatoRelay.connectToServer {
            println("Connected to server")

            val packet = RequestNetworkSettingsPacket()
            packet.protocolVersion = gatoRelaySession.server.codec.protocolVersion
            gatoRelaySession.serverBoundImmediately(packet)
        }
    }

}