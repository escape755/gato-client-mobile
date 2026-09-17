package com.gato.relay.listener

import com.gato.relay.GatoRelaySession
import com.gato.relay.util.AuthUtils
import com.gato.relay.util.warmUp
import com.gato.relay.util.isExpired
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import org.cloudburstmc.protocol.bedrock.util.JsonUtils
import org.jose4j.json.JsonUtil
import org.jose4j.json.internal.json_simple.JSONObject
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwx.HeaderParameterNames
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@Suppress("MemberVisibilityCanBePrivate")
class OnlineLoginPacketListener(
    val gatoRelaySession: GatoRelaySession,
    val authManager: BedrockAuthManager
) : GatoRelayPacketListener {

    private var skinData: JSONObject? = null

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is LoginPacket) {
            if (authManager.isExpired) {
                gatoRelaySession.server.disconnect("Your session was expired, you need to delete account then login again in the Gato Client Mobile")
                return true
            }

            println("Handle online login data")
            runCatching {
                val p = packet.authPayload
                when (p) {
                    is org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload ->
                        println("[AUTH-DIAG] client auth: CertificateChainPayload type=${p.authType} chainSize=${p.chain.size} first=${p.chain.firstOrNull()?.take(40)}")
                    is org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload -> {
                        println("[AUTH-DIAG] client auth: TokenPayload type=${p.authType} tokenHead=${p.token.take(60)}")
                        val jws = org.jose4j.jws.JsonWebSignature()
                        jws.compactSerialization = p.token
                        println("[AUTH-DIAG] CLIENT token claims=${jws.unverifiedPayload.take(700)}")
                    }
                    else -> println("[AUTH-DIAG] client auth: ${p?.javaClass?.name}")
                }
            }

            val jws = JsonWebSignature()
            jws.compactSerialization = packet.clientJwt

            skinData = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))
            println("[AUTH-DIAG] CLIENT skin claims=${jws.unverifiedPayload.take(300)}")

            // warm the auth chain off the netty thread while the server dial runs —
            // the lazy holders would otherwise block the event loop for seconds and
            // time the MC client out
            kotlin.concurrent.thread(name = "AuthWarmUp") {
                runCatching { authManager.warmUp() }
            }

            connectServer()
            return true
        }
        return false
    }

    @OptIn(ExperimentalEncodingApi::class)
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
                // build the login OFF the netty event loop: the auth holders may hit
                // the network (MSA/XBL/Mojang chain) and blocking here times the MC
                // client out; netty channels accept writes from any thread
                kotlin.concurrent.thread(name = "OnlineLoginBuild") {
                    try {
                        val token = AuthUtils.fetchOnlineToken(authManager)
                        println("[AUTH-DIAG] sending online token len=${token.length} head=${token.take(40)}")
                        runCatching {
                            val jws = org.jose4j.jws.JsonWebSignature()
                            jws.compactSerialization = token
                            println("[AUTH-DIAG] OUR token claims=${jws.unverifiedPayload.take(700)}")
                        }
                        val skinData =
                            AuthUtils.fetchOnlineSkinData(
                                authManager,
                                skinData!!,
                                gatoRelaySession.gatoRelay.remoteAddress!!
                            )

                        runCatching {
                            val sjws = org.jose4j.jws.JsonWebSignature()
                            sjws.compactSerialization = skinData
                            println("[AUTH-DIAG] OUR skin claims=${sjws.unverifiedPayload.take(300)}")
                        }

                        val loginPacket = LoginPacket()
                        loginPacket.protocolVersion = gatoRelaySession.server.codec.protocolVersion
                        loginPacket.authPayload = TokenPayload(token, AuthType.FULL)
                        loginPacket.clientJwt = skinData
                        gatoRelaySession.serverBoundImmediately(loginPacket)

                        println("Login success")
                    } catch (e: Throwable) {
                        gatoRelaySession.clientBound(DisconnectPacket().apply {
                            setKickMessage(e.toString())
                        })
                        println("Login failed: $e")
                    }
                }
            } catch (e: Throwable) {
                gatoRelaySession.clientBound(DisconnectPacket().apply {
                    setKickMessage(e.toString())
                })
                println("Login dispatch failed: $e")
            }

            return true
        }
        if (packet is ServerToClientHandshakePacket) {
            val jws = JsonWebSignature().apply {
                compactSerialization = packet.jwt
            }

            val saltJwt = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))
            val x5u = jws.getHeader(HeaderParameterNames.X509_URL)
            val serverKey = EncryptionUtils.parseKey(x5u)
            val key = EncryptionUtils.getSecretKey(
                authManager.sessionKeyPair.private, serverKey,
                Base64.decode(JsonUtils.childAsType(saltJwt, "salt", String::class.java))
            )
            gatoRelaySession.client!!.enableEncryption(key)
            println("Encryption enabled")

            gatoRelaySession.serverBoundImmediately(ClientToServerHandshakePacket())
            return true
        }
        return false
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