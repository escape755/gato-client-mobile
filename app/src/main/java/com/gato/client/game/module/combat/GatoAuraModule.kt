package com.gato.client.game.module.combat

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.ListItem
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import com.gato.client.game.entity.Entity
import com.gato.client.game.entity.EntityUnknown
import com.gato.client.game.entity.Item
import com.gato.client.game.entity.Player
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the GatoClient (PC) GatoAura module — adaptive melee aura.
 * Exact settings, defaults (the ctor values) and per-tick logic.
 *
 * PC reference: Combat/GatoAura.cpp
 *
 * Relay mapping (documented deviations):
 * - WallRange/LineOfSight: the relay has no voxel access, so the PC's
 *   getSeenPercent wall check cannot run; targets within Range are always
 *   treated as visible and WallRange is kept only for config compatibility.
 * - Raytrace Spoof: the hit result is engine memory; nothing to spoof over
 *   the relay (the attack packet already targets the entity).
 * - AutoWeapon "Switch": the relay cannot move the real client's hotbar, so
 *   Switch behaves like an attack from the best slot; "Spoof" additionally
 *   reports the best slot via MobEquipmentPacket.
 * - Java Cooldown: engine cooldowns are replaced by a static per-weapon
 *   table (ms).
 * - Packet Criticals: the PAIP position.y sawtooth is replicated; the
 *   fall-velocity claim is dropped (PAIP velocity is horizontal-only in
 *   current protocol).
 * - Rotations spoof the server-bound PAIP only; the local camera does not
 *   move (Normal = pitch + head yaw spoof, Strafe = full rotation spoof).
 * - caCompatibility: AutoCrystal/VoidAura/PistonCrystal do not exist on
 *   mobile, so the crystal-busy check is always false.
 */
class GatoAuraModule : Module("GatoAura", ModuleCategory.Combat) {

    // --- named selectors (horizontal chip list in the UI) ---
    private class Mode(override val name: String, val idx: Int) : ListItem

    private val rotModes = listOf(Mode("None", 0), Mode("Normal", 1), Mode("Strafe", 2))
    private val hitTypes = listOf(Mode("Single", 0), Mode("Multi", 1))
    private val targetPriorities = listOf(Mode("Distance", 0), Mode("Health", 1))
    private val weaponModes = listOf(Mode("None", 0), Mode("Switch", 1), Mode("Spoof", 2))

    // --- Settings: same names/defaults (ctor values)/ranges as the PC ctor ---
    private var range by floatValue("Range", 5f, 3f..40f)
    private var wallRange by floatValue("WallRange", 0f, 0f..40f) // sin LOS en relay (ver class comment)
    private var interval by intValue("Interval", 1, 0..20)
    private var java by boolValue("Java Cooldown", true)
    private var rotModeItem by listValue("Rotations", rotModes[1], rotModes.toSet())
    private var rotationSpeed by intValue("Rot Speed", 10, 10..180)

    private var adaptiveRot by boolValue("Adaptive Rot", false)
    private var shouldCriticals by boolValue("Packet Criticals", false)
    private var criticalVelocity by floatValue("Critical Velocity", 1f, -5f..5f)
    private var strikeMode by boolValue("Strike Mode", false)
    private var minDist by floatValue("Min Dist", 3f, 0f..20f)
    private var maxDist by floatValue("Max Dist", 8f, 0f..20f)
    private var randomize by boolValue("Aim Randomize", true)
    private var facingCheck by boolValue("Facing Check", false)
    private var maxAngle by floatValue("Max Angle", 45f, 0f..90f)

    private var hitTypeItem by listValue("HitType", hitTypes[0], hitTypes.toSet())
    private var targetPriorityItem by listValue("Target Priority", targetPriorities[0], targetPriorities.toSet())
    private var packetAttack by boolValue("Packet", false)
    private var adaptivePackets by boolValue("Adaptive Packets", true)
    private var raytraceSpoof by boolValue("Raytrace Spoof", false)        // inerte en relay
    private var hitChance by intValue("HitChance", 100, 0..100)
    private var intervalJitter by floatValue("Interval Jitter", 15f, 0f..50f)
    private var hitAttempts by intValue("Hit Attempts", 1, 1..50)
    private var weaponItem by listValue("Weapon", weaponModes[0], weaponModes.toSet())
    private var includeMobs by boolValue("Mobs", false)
    private var hurtTimeCheck by boolValue("Hurt Check", true)
    private var eatStop by boolValue("EatStop", false)

    // int accessors over the named selectors
    private val rotMode get() = (rotModeItem as Mode).idx
    private val hitType get() = (hitTypeItem as Mode).idx
    private val targetPriority get() = (targetPriorityItem as Mode).idx
    private val autoWeaponMode get() = (weaponItem as Mode).idx

    init {
        // Visibility rules copied from the PC registerSetting lambdas
        getValue("Critical Velocity")?.visibleIf = { shouldCriticals }
        getValue("Strike Mode")?.visibleIf = { shouldCriticals }
        getValue("Min Dist")?.visibleIf = { adaptiveRot }
        getValue("Max Dist")?.visibleIf = { adaptiveRot }
        getValue("Max Angle")?.visibleIf = { facingCheck }
    }

    // runtime state
    private val targetList = ArrayList<Entity>()
    private var shouldRot = false
    private var targetYaw = 0f
    private var currentYaw = 0f
    private var targetPitch = 0f
    private var currentPitch = 0f
    private var critPend = false
    private var critDip = 0f
    private var timePassed = 0L          // ms clock for Java cooldown / tick clock for interval
    private var lastTickTime = 0L
    private var lastJava = false
    private var usingItemTicks = 0

    override fun onEnabled() {
        super.onEnabled()
        targetYaw = 0f; currentYaw = 0f; targetPitch = 0f; currentPitch = 0f
        critPend = false; critDip = 0f
    }

    override fun onDisabled() {
        super.onDisabled()
        targetList.clear()
        shouldRot = false
        targetYaw = 0f; currentYaw = 0f; targetPitch = 0f; currentPitch = 0f
        critPend = false; critDip = 0f
    }

    // ---- static per-weapon tables (mobile substitute for engine values) ----
    private fun weaponDamage(identifier: String?): Float = when (identifier) {
        "minecraft:wooden_sword" -> 4f
        "minecraft:stone_sword" -> 5f
        "minecraft:iron_sword" -> 6f
        "minecraft:diamond_sword" -> 7f
        "minecraft:netherite_sword" -> 8f
        "minecraft:golden_sword" -> 4f
        "minecraft:wooden_axe" -> 7f
        "minecraft:stone_axe" -> 9f
        "minecraft:iron_axe" -> 9f
        "minecraft:diamond_axe" -> 9f
        "minecraft:netherite_axe" -> 10f
        "minecraft:trident" -> 9f
        else -> 1f
    }

    private fun weaponCooldownMs(identifier: String?): Long = when {
        identifier == null -> 0L
        identifier.endsWith("_sword") -> 625L
        identifier.endsWith("_axe") -> 1000L
        identifier == "minecraft:trident" -> 500L
        else -> 0L
    }

    private fun getBestWeaponSlot(target: Entity): Int {
        val inventory = session.localPlayer.inventory
        var damage = weaponDamage(inventory.content[inventory.heldItemSlot].definition?.identifier)
        var slot = inventory.heldItemSlot
        for (i in 0..8) {
            val currentDamage = weaponDamage(inventory.content[i].definition?.identifier)
            if (currentDamage > damage) {
                damage = currentDamage
                slot = i
            }
        }
        return slot
    }

    private fun attack(target: Entity): Boolean {
        val randomNumber = (Math.random() * 100).toInt()
        val inventory = session.localPlayer.inventory

        var attempts = hitAttempts
        if (adaptivePackets) {
            val dist = target.distance(session.localPlayer.vec3Position)
            if (dist < 3.5f) attempts *= 2
            else if (dist > 10f) attempts = if (attempts > 1) attempts / 2 else 1
        }

        val attacked = randomNumber < hitChance
        if (attacked) {
            repeat(attempts) {
                if (packetAttack) {
                    // exact PC packet: ItemUseOnActor attack with criticalVelocity offset
                    val transaction = InventoryTransactionPacket()
                    transaction.transactionType = InventoryTransactionType.ITEM_USE_ON_ENTITY
                    transaction.actionType = 1
                    transaction.runtimeEntityId = target.runtimeEntityId
                    transaction.hotbarSlot = inventory.heldItemSlot
                    transaction.itemInHand = inventory.hand
                    transaction.playerPosition = Vector3f.from(
                        session.localPlayer.posX,
                        session.localPlayer.posY + criticalVelocity,
                        session.localPlayer.posZ
                    )
                    transaction.clickPosition = Vector3f.from(0.1f, 0.5f, 0.1f)
                    session.serverBound(transaction)
                } else {
                    session.localPlayer.attack(target)
                }
            }
        }

        session.localPlayer.swing()
        return attacked
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isSessionCreated) return
        val packet = interceptablePacket.packet

        when (packet) {
            is PlayerAuthInputPacket -> {
                if (isEnabled) tick(packet)
                spoofRotation(packet)
            }

            // Strike Mode: yOffset on own outbound movement packets
            // (only flows on client-auth servers; PAIP carries position on server-auth)
            is MovePlayerPacket -> applyStrikeOffset(packet)
            is MoveEntityAbsolutePacket -> applyStrikeOffset(packet)
        }
    }

    private fun applyStrikeOffset(packet: BedrockPacket) {
        if (!isEnabled || !shouldCriticals || critDip == 0f) return
        val yOffset: Float = if (strikeMode) {
            if (criticalVelocity < 0f) criticalVelocity * -2f + criticalVelocity
            else if (criticalVelocity > 0f) criticalVelocity * 2f - criticalVelocity
            else 0f
        } else {
            criticalVelocity
        }
        if (yOffset == 0f) return
        when (packet) {
            is MoveEntityAbsolutePacket -> {
                if (packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                    packet.position = Vector3f.from(
                        packet.position.x, packet.position.y + yOffset, packet.position.z
                    )
                }
            }
            is MovePlayerPacket -> {
                if (packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                    packet.position = Vector3f.from(
                        packet.position.x, packet.position.y + yOffset, packet.position.z
                    )
                }
            }
        }
    }

    private fun tick(packet: PlayerAuthInputPacket) {
        val localPlayer = session.localPlayer
        critPend = false

        targetList.clear()
        if (eatStop) {
            // PC: getItemUseDuration() > 0; the input only exposes the START event,
            // so the using-item state is held for a window after seeing it
            if (packet.inputData.contains(PlayerAuthInputData.START_USING_ITEM)) usingItemTicks = 20
            if (usingItemTicks > 0) {
                usingItemTicks--
                shouldRot = false
                critDip = 0f
                return
            }
        }

        // ---- target collection ----
        for (entity in session.level.entityMap.values) {
            if (entity.distance(localPlayer.vec3Position) > range) continue
            val isPlayer = entity is Player
            val isMob = entity is EntityUnknown && entity !is Item &&
                entity.identifier != "minecraft:item" && !isPlayer
            if (!isPlayer && !isMob) continue
            if (!isPlayer && !includeMobs) continue
            if (hurtTimeCheck && ((entity.metadata[EntityDataTypes.HURT_TICKS] as? Int) ?: 0) > 0) continue
            // PC: wall check via getSeenPercent -> wallRange; no LOS in relay (see class comment)
            targetList.add(entity)
        }

        if (targetList.isEmpty()) {
            shouldRot = false
            critDip = 0f
            return
        }

        // ---- target priority ----
        if (targetPriority == 1) {
            targetList.sortBy { (it.attributes["minecraft:health"]?.value ?: 20f) }
        } else {
            targetList.sortBy { it.distance(localPlayer.vec3Position) }
        }

        // ---- aim angle ----
        val first = targetList[0]
        val (width, height) = targetDims(first)
        val boxCenterY = first.posY + height * 0.5f
        var aimX = first.posX
        var aimY = boxCenterY
        var aimZ = first.posZ
        if (randomize) {
            val yLow = first.posY + 0.1f
            val yHigh = first.posY + height - 0.1f
            val xLow = first.posX - width * 0.5f + 0.2f
            val xHigh = first.posX + width * 0.5f - 0.2f
            val zLow = first.posZ - width * 0.5f + 0.2f
            val zHigh = first.posZ + width * 0.5f - 0.2f
            aimX = randomFloat(xLow, xHigh)
            aimY = if (yHigh > yLow) randomFloat(yLow, yHigh) else boxCenterY
            aimZ = randomFloat(zLow, zHigh)
        }

        // CalcAngle(eyePos -> aimPos): {x: pitch, y: yaw}
        val eyeY = localPlayer.posY + 1.62f // eye height
        val dX = aimX - localPlayer.posX
        val dY = aimY - eyeY
        val dZ = aimZ - localPlayer.posZ
        val horiz = sqrt(dX * dX + dZ * dZ)
        targetPitch = (atan2(dY, horiz) * 57.295776f) * -1f
        targetYaw = -atan2(dX, dZ) * 57.295776f

        shouldRot = true
        // crystalBusy: no existen auras de cristal en mobile -> skip (caCompatibility)

        val weaponSlot = if (autoWeaponMode != 0 || java) getBestWeaponSlot(first)
        else localPlayer.inventory.heldItemSlot

        var attackInterval = interval.toFloat()
        if (intervalJitter > 0f && !java) {
            attackInterval *= 1f + randomFloat(-intervalJitter, intervalJitter) / 100f
            if (attackInterval < 0f) attackInterval = 0f
        }

        var facingOk = true
        if (facingCheck) {
            var facingDiff = targetYaw - currentYaw
            while (facingDiff < -180f) facingDiff += 360f
            while (facingDiff > 180f) facingDiff -= 360f
            facingOk = abs(facingDiff) <= maxAngle
        }

        if (facingOk) {
            val cooldownItem = localPlayer.inventory.content[
                if (autoWeaponMode != 0) weaponSlot else localPlayer.inventory.heldItemSlot
            ]
            val cooldownMs = weaponCooldownMs(cooldownItem.definition?.identifier)
            val now = System.nanoTime() / 1_000_000L
            val reached: Boolean
            if (java && cooldownMs > 0L) {
                reached = now - timePassed >= cooldownMs
            } else {
                // tick-based interval (20 tps)
                val tickIntervalMs = (attackInterval * 50f).toLong()
                reached = now - lastTickTime >= tickIntervalMs
                lastTickTime = now
            }
            if (java && cooldownMs > 0L && reached) timePassed = now

            if (reached) {
                if (autoWeaponMode != 0) {
                    // Switch/Spoof: report the best slot to the server (Spoof keeps
                    // reporting it every attack, like the PC selectedSlotServerSide flow)
                    if (localPlayer.inventory.heldItemSlot != weaponSlot) {
                        session.serverBound(MobEquipment(weaponSlot))
                    }
                }
                var attackedAny = false
                for (target in targetList) {
                    if (attack(target)) attackedAny = true
                    if (hitType != 1) break
                }
                critPend = shouldCriticals && attackedAny
            }
        }
    }

    private fun MobEquipment(slot: Int) = MobEquipmentPacket().apply {
        runtimeEntityId = session.localPlayer.runtimeEntityId
        item = session.localPlayer.inventory.content[slot]
        inventorySlot = slot
        hotbarSlot = slot
    }

    private fun spoofRotation(packet: PlayerAuthInputPacket) {
        // rotation smoothing (PC onUpdateRotation) + PAIP spoof (PC onSendPacket)
        if (!shouldRot || targetList.isEmpty()) return

        var angleDiff = targetYaw - currentYaw
        while (angleDiff < -180f) angleDiff += 360f
        while (angleDiff > 180f) angleDiff -= 360f

        var finalRotationSpeed = rotationSpeed.toFloat()
        if (adaptiveRot) {
            val first = targetList[0]
            val distance = first.distance(session.localPlayer.vec3Position)
            val denom = maxOf(maxDist - minDist, 1f)
            var ratio = (distance - minDist) / denom
            if (ratio < 0f) ratio = 0f
            if (ratio > 1f) ratio = 1f
            finalRotationSpeed = 10f + ratio * (rotationSpeed - 10f)
        }

        currentYaw += angleDiff * (0.01f * finalRotationSpeed)
        val pitchDiff = targetPitch - currentPitch
        currentPitch += pitchDiff * (0.01f * finalRotationSpeed)
        if (currentPitch > 90f) currentPitch = 90f
        if (currentPitch < -90f) currentPitch = -90f

        when (rotMode) {
            0 -> return
            1 -> {
                // Normal: pitch + head yaw spoof (camera yaw untouched, like presentRot.x only)
                packet.rotation = Vector3f.from(currentPitch, packet.rotation.y, currentYaw)
            }
            2 -> {
                // Strafe: full rotation spoof
                packet.rotation = Vector3f.from(currentPitch, currentYaw, currentYaw)
            }
        }

        val distance = targetList[0].distance(session.localPlayer.vec3Position)
        if (distance < 0.1f) {
            packet.rotation = Vector3f.from(90f, packet.rotation.y, currentYaw) // trust
        }

        // Packet Criticals: sawtooth position dip on the outgoing PAIP
        if (shouldCriticals && shouldRot) {
            packet.position = Vector3f.from(
                packet.position.x, packet.position.y - critDip, packet.position.z
            )
            critDip += 0.012f
            if (critDip >= 0.24f) critDip = 0f
            // critPend fall-velocity claim: PAIP velocity is horizontal-only in the
            // current protocol, so the -0.0625 y claim has no field to land on
        }
        critPend = false
    }

    private fun targetDims(entity: Entity): Pair<Float, Float> {
        val w = entity.metadata[EntityDataTypes.WIDTH] as? Float ?: 0.6f
        val h = entity.metadata[EntityDataTypes.HEIGHT] as? Float ?: 1.8f
        return w to h
    }

    private fun randomFloat(low: Float, high: Float): Float =
        if (high <= low) low else low + Math.random().toFloat() * (high - low)
}
