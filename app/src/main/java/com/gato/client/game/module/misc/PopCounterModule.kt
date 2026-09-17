package com.gato.client.game.module.misc

import com.gato.client.game.FriendManager
import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import com.gato.client.game.entity.Player
import org.cloudburstmc.protocol.bedrock.data.LevelEvent
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import kotlin.math.sqrt

/**
 * Port of the GatoClient (PC) PopCounter module — exact behavior and settings.
 *
 * PC reference: Client/Managers/ModuleManager/Modules/Category/Player/PopCounter.cpp
 *
 * Pops are counted per player even while the module is disabled (same as the PC
 * module, which increments before checking isEnabled) — only the messages are gated.
 *
 * Deviations imposed by the relay architecture:
 * - The PC "didHaveTotem" offhand heuristic (which needs memory access to other
 *   players' equipment) is replaced by nearest-player matching against the totem
 *   sound position (LevelEvent mode).
 * - The PC resets its own counter when !isAlive each tick; the relay equivalent
 *   is resetting it on the respawn packet.
 */
class PopCounterModule : Module("PopCounter", ModuleCategory.Misc) {

    // --- Settings: same names and defaults as the PC module ---
    private var sendPops by boolValue("Send Pop Message", false)
    private var sendPopsOnDeath by boolValue("Send Death Message", false)
    private var countFriends by boolValue("Track Friends", false)
    private var countSelf by boolValue("Track Self", false)
    private var actorEventOnly by boolValue("ActorEvent", false)

    // --- Game-chat templates: same defaults and placeholders as the PC module ($ = §) ---
    private var popMessage by stringValue("Game Pop Message", "@!player! just popped !pops! !totem!", null)
    private var deathMessage by stringValue("Game Death Message", "@!player! just died after popping !pops! !totem!", null)

    // runtimeEntityId -> pops (same as the PC totemMap)
    private val totemMap = HashMap<Long, Int>()

    // uniqueEntityId -> (runtimeEntityId, username): RemoveEntityPacket only carries the
    // unique id and Level clears the entity before modules see the packet, so the names
    // of players that have popped are cached while they are alive.
    private val entityCache = HashMap<Long, Pair<Long, String>>()

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        when (val packet = interceptablePacket.packet) {
            is LevelEventPacket -> {
                if (!actorEventOnly &&
                    packet.type == LevelEvent.SOUND_TOTEM_USED &&
                    (packet.position.x != 0f || packet.position.y != 0f || packet.position.z != 0f)
                ) {
                    handleLevelEventPop(packet)
                }
            }

            is EntityEventPacket -> {
                if (actorEventOnly && packet.type == EntityEventType.CONSUME_TOTEM) {
                    handleActorEventPop(packet)
                }
            }

            is RemoveEntityPacket -> handleRemove(packet)

            is RespawnPacket -> {
                // PC equivalent: reset own counter when !isAlive per tick
                totemMap[session.localPlayer.runtimeEntityId] = 0
            }
        }
    }

    private fun handleLevelEventPop(packet: LevelEventPacket) {
        val eventPos = packet.position
        val candidates = mutableListOf<Triple<Long, Long, String>>() // runtimeId, uniqueId, name

        // local player + every tracked player entity, same actor-type filter as the PC module
        val local = session.localPlayer
        if (local.runtimeEntityId != 0L) {
            candidates.add(Triple(local.runtimeEntityId, local.uniqueEntityId, local.displayName))
        }
        session.level.entityMap.values.forEach { entity ->
            if (entity is Player) {
                candidates.add(Triple(entity.runtimeEntityId, entity.uniqueEntityId, entity.displayName))
            }
        }

        // PC: floor(pos).sub(0, 1, 0).dist(eventPos) > 10 -> skip; nearest one pops
        var best: Triple<Long, Long, String>? = null
        var bestDist = Float.MAX_VALUE
        for ((runtimeId, uniqueId, username) in candidates) {
            val pos = if (runtimeId == local.runtimeEntityId) {
                local.vec3PositionFeet
            } else {
                session.level.entityMap[runtimeId]?.vec3Position ?: continue
            }
            val dx = kotlin.math.floor(pos.x) - eventPos.x
            val dy = kotlin.math.floor(pos.y) - 1f - eventPos.y
            val dz = kotlin.math.floor(pos.z) - eventPos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > 10f) continue
            if (dist < bestDist) {
                bestDist = dist
                best = Triple(runtimeId, uniqueId, username)
            }
        }

        best?.let { (runtimeId, uniqueId, username) ->
            entityCache[uniqueId] = runtimeId to username
            onActorPop(runtimeId, username, uniqueId == session.localPlayer.uniqueEntityId)
        }
    }

    private fun handleActorEventPop(packet: EntityEventPacket) {
        val runtimeId = packet.runtimeEntityId
        if (runtimeId == session.localPlayer.runtimeEntityId) {
            entityCache[session.localPlayer.uniqueEntityId] =
                runtimeId to session.localPlayer.displayName
            onActorPop(runtimeId, session.localPlayer.displayName, true)
            return
        }
        val entity = session.level.entityMap[runtimeId] as? Player ?: return
        entityCache[entity.uniqueEntityId] = runtimeId to entity.displayName
        onActorPop(runtimeId, entity.displayName, false)
    }

    private fun handleRemove(packet: RemoveEntityPacket) {
        val (runtimeId, username) = entityCache[packet.uniqueEntityId] ?: return
        val pops = totemMap[runtimeId] ?: return
        if (pops == 0) return

        val isSelf = packet.uniqueEntityId == session.localPlayer.uniqueEntityId
        if (isSelf) {
            // PC skips the local player in the remove handler
            totemMap[runtimeId] = 0
            return
        }

        val isFriend = FriendManager.isInList(username)
        val totemWord = if (pops > 1) "totems" else "totem"
        val prefix = if (isFriend) "[-]" else "[+]"
        val prefixColor = if (isFriend) "§c" else "§a"
        val nameColor = if (isFriend) "§9" else "§c"

        if (isEnabled) {
            session.displayClientMessage(
                "$prefixColor$prefix $nameColor$username §fdied after popping §b$pops §f$totemWord!"
            )
        }

        if (sendPopsOnDeath && !isFriend) {
            sendGameChat(sanitize(deathMessage, username, pops))
        }

        totemMap[runtimeId] = 0
    }

    /** PC onActorPop: increments before any isEnabled check; messages are gated. */
    private fun onActorPop(runtimeId: Long, username: String, isSelf: Boolean) {
        val pops = (totemMap[runtimeId] ?: 0) + 1
        totemMap[runtimeId] = pops
        val totemWord = if (pops > 1) "totems" else "totem"

        if (!isEnabled) return

        val isFriend = FriendManager.isInList(username)
        when {
            isSelf -> {
                if (countSelf) {
                    session.displayClientMessage("§6[!] §9You §fpopped §b$pops §f$totemWord!")
                }
            }

            isFriend -> {
                if (countFriends) {
                    session.displayClientMessage("§6[!] §9$username §fpopped §b$pops §f$totemWord!")
                }
            }

            else -> {
                session.displayClientMessage("§6[!] §c$username §fpopped §b$pops §f$totemWord!")
                if (sendPops) {
                    sendGameChat(sanitize(popMessage, username, pops))
                }
            }
        }
    }

    /** PC sanitize: $ becomes §, placeholders resolved. */
    private fun sanitize(message: String, name: String, pops: Int): String =
        message
            .replace("$", "§")
            .replace("!clientname!", "Gato Client Mobile")
            .replace("!player!", name)
            .replace("!pops!", pops.toString())
            .replace("!totem!", if (pops > 1) "totems" else "totem")

    private fun sendGameChat(message: String) {
        session.serverBound(TextPacket().apply {
            type = TextPacket.Type.CHAT
            setMessage(message)
        })
    }

    /** Public accessor, same as the PC getTotemPops. */
    fun getTotemPops(runtimeId: Long): Int = totemMap[runtimeId] ?: 0
}
