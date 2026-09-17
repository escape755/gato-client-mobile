package com.gato.relay.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gato.relay.GatoRelay
import com.gato.relay.GatoRelaySession
import com.gato.relay.address.GatoAddress
import net.lenni0451.commons.httpclient.HttpClient
import net.lenni0451.commons.httpclient.retry.RetryConfig
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService
import org.cloudburstmc.protocol.bedrock.BedrockPong
import java.io.File
import java.nio.file.Paths
import java.util.function.Consumer

/**
 * Game version reported to the Minecraft session service during login.
 * Kept in sync with the relay's default codec (the target Bedrock version).
 */
val MINECRAFT_GAME_VERSION: String = GatoRelay.DefaultCodec.minecraftVersion

/**
 * Minecraft version -> RakNet protocol, newest first (from the vendored codecs).
 * The relay advertisement should report the CONNECTING client's version: a
 * client sees a server advertising a newer protocol and aborts the initial
 * connection (InitialConnection-13) even though the codec negotiation could
 * handle it.
 */
val SUPPORTED_VERSIONS: Map<String, Int> = linkedMapOf(
    "1.26.50" to 2192,
    "1.26.45" to 2169,
    "1.26.44" to 2168,
    "1.26.40" to 2168,
    "1.26.30" to 1001,
    "1.26.20" to 975,
    "1.26.10" to 944,
    "1.26.0" to 924,
    "1.21.130" to 898,
    "1.21.124" to 860,
    "1.21.120" to 859,
    "1.21.111" to 844,
    "1.21.100" to 827,
    "1.21.93" to 819,
    "1.21.90" to 818,
    "1.21.80" to 800,
    "1.21.70" to 786,
    "1.21.60" to 776,
    "1.21.50" to 766,
    "1.21.40" to 748
)

/** Picks the advertised version for a client version: exact, else nearest lower, else default. */
fun advertisedVersionFor(clientVersion: String?): String? {
    if (clientVersion == null) return null
    SUPPORTED_VERSIONS[clientVersion]?.let { return clientVersion }

    fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
    val target = parts(clientVersion)
    return SUPPORTED_VERSIONS.keys
        .map { it to parts(it) }
        .filter { (_, p) ->
            val len = minOf(p.size, target.size)
            for (i in 0 until len) {
                if (p[i] != target[i]) return@filter p[i] < target[i]
            }
            p.size <= target.size
        }
        .maxByOrNull { (_, p) -> p.map { it.toLong() }.fold(0L) { acc, v -> acc * 1000 + v } }
        ?.first
}

/** Advertisement reporting the given client version (or the codec default). */
fun buildAdvertisement(minecraftVersion: String?): BedrockPong {
    val protocol = minecraftVersion?.let { SUPPORTED_VERSIONS[it] } ?: return GatoRelay.DefaultAdvertisement
    return BedrockPong()
        .edition("MCPE")
        .gameType("Survival")
        .version(minecraftVersion)
        .protocolVersion(protocol)
        .motd("GatoRelay")
        .playerCount(0)
        .maximumPlayerCount(20)
        .subMotd("")
        .nintendoLimited(false)
}

fun captureGamePacket(
    advertisement: BedrockPong = GatoRelay.DefaultAdvertisement,
    localAddress: GatoAddress = GatoAddress("0.0.0.0", 19132),
    remoteAddress: GatoAddress,
    onSessionCreated: GatoRelaySession.() -> Unit
): GatoRelay {
    return GatoRelay(
        localAddress = localAddress,
        advertisement = advertisement
    ).capture(
        remoteAddress = remoteAddress,
        onSessionCreated = onSessionCreated
    )
}

private fun createAuthHttpClient(): HttpClient {
    val httpClient = MinecraftAuth.createHttpClient()
    httpClient.connectTimeout = 30000
    httpClient.readTimeout = 30000
    httpClient.setRetryHandler(RetryConfig(3, 100))
    return httpClient
}

/**
 * Device-code login against the new Bedrock token authentication
 * (MinecraftAuth v5 BedrockAuthManager). The callback receives the
 * MsaDeviceCode so the UI can open the verification URL.
 */
fun authorize(
    gameVersion: String = MINECRAFT_GAME_VERSION,
    cache: Boolean = true,
    file: File? = Paths.get(".").resolve("bedrockSession.json").toFile(),
    msaDeviceCodeCallback: Consumer<MsaDeviceCode> = Consumer {
        println("Go to ${it.directVerificationUri}")
    }
): BedrockAuthManager {
    val httpClient = createAuthHttpClient()

    if (cache && file != null && file.exists()) {
        val json = JsonParser.parseString(file.readText()).asJsonObject
        return BedrockAuthManager.fromJson(httpClient, gameVersion, json)
    }

    val authManager = BedrockAuthManager.create(httpClient, gameVersion)
        .login(::DeviceCodeMsaAuthService, msaDeviceCodeCallback)

    if (cache && file != null && !file.isDirectory) {
        val json = BedrockAuthManager.toJson(authManager)
        file.writeText(AuthUtils.gson.toJson(json))
    }

    return authManager
}

/**
 * Lazily refreshes the underlying tokens (MSA first; the XBL/session chain
 * holders refresh themselves when their values are requested).
 */
fun BedrockAuthManager.refresh(): BedrockAuthManager {
    this.msaToken.refreshIfExpired()
    return this
}

/**
 * Pre-fetches every token the online login needs (MSA -> XBL device/user ->
 * bedrock XSTS -> PlayFab -> Minecraft certificate chain). MUST be called from
 * a background thread: each holder may hit the network. Warming these up front
 * keeps the relay's netty event loop from blocking (and the MC client from
 * timing out) while the join proceeds.
 */
fun BedrockAuthManager.warmUp() {
    runCatching { this.msaToken.upToDate }
    runCatching { this.bedrockXstsToken.upToDate }
    runCatching { this.playFabToken.upToDate }
    runCatching { this.minecraftCertificateChain.upToDate }
    runCatching { this.minecraftMultiplayerToken.upToDate }
}

val BedrockAuthManager.isExpired: Boolean
    get() = !this.msaToken.hasValue() || this.msaToken.isExpired
