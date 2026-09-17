package com.gato.client.game.module.misc

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.ListItem
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import com.gato.client.game.inventory.PlayerInventory
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket

/**
 * Port of the GatoClient (PC) Offhand module (the AutoTotem) — exact behavior
 * and settings.
 *
 * PC reference: Client/Managers/ModuleManager/Modules/Category/Player/Offhand.cpp
 *
 * The PC module swaps inventory[slot] <-> offhand via a ComplexInventoryTransaction
 * with two InventoryActions; on mobile that maps to PlayerInventory.moveItem into
 * SLOT_OFFHAND, which sends the right packet for both server-authoritative
 * (ItemStackRequest place/swap) and legacy (InventoryTransaction) sessions and
 * syncs the client's inventory UI afterwards.
 *
 * Deviations imposed by the relay architecture:
 * - Item identity is matched by definition name (minecraft:totem_of_undying /
 *   minecraft:shield) instead of the PC SDK's runtime item ids.
 * - Health is tracked from the client-bound UpdateAttributesPacket (the PC reads
 *   it from memory each tick).
 * - SurroundOnly approximates the PC's 4-side air check with the auth input's
 *   HORIZONTAL_COLLISION flag — the relay has no voxel access.
 */
class OffhandModule : Module("Offhand", ModuleCategory.Misc) {

    // --- named selector (horizontal chip list in the UI) ---
    private class Mode(override val name: String, val idx: Int) : ListItem

    private val itemModes = listOf(Mode("Totem", 0), Mode("Shield", 1))

    // --- Settings: same names and defaults as the PC module ---
    private var itemItem by listValue("Item", itemModes[0], itemModes.toSet())
    private var delay by intValue("Delay", 0, 0..20)
    private var smart by boolValue("Smart", false)
    private var swapToHealth by floatValue("Swap Totem", 0f, 0f..20f)
    private var swapBack by floatValue("Swap Shield", 0f, 0f..20f)
    private var surroundOnly by boolValue("SurroundOnly", false)
    private var debug by boolValue("Debug", false)

    // int accessor over the named selector
    private val itemMode get() = (itemItem as Mode).idx

    init {
        // Visibility rules copied from the PC registerSetting lambdas
        getValue("Swap Totem")?.visibleIf = { smart }
        getValue("Swap Shield")?.visibleIf = { smart }
        getValue("SurroundOnly")?.visibleIf = { smart }
        getValue("Debug")?.visibleIf = { smart }
    }

    private val TOTEM = "minecraft:totem_of_undying"
    private val SHIELD = "minecraft:shield"

    private var shouldWeSwap = false
    private var swapDelay = 0
    private var health = 20f
    private var horizontalCollision = false

    private fun itemName(item: ItemData): String? = item.definition?.identifier

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        when (val packet = interceptablePacket.packet) {
            is UpdateAttributesPacket -> {
                if (isSessionCreated &&
                    packet.runtimeEntityId == session.localPlayer.runtimeEntityId
                ) {
                    packet.attributes
                        .find { it.name == "minecraft:health" }
                        ?.let { health = it.value }
                }
            }

            is PlayerAuthInputPacket -> tick(packet)
        }
    }

    private fun tick(packet: BedrockPacket) {
        if (!isSessionCreated) return
        packet as PlayerAuthInputPacket

        // SurroundOnly approximation signal (see class comment)
        horizontalCollision = packet.inputData.contains(PlayerAuthInputData.HORIZONTAL_COLLISION)

        if (!isEnabled) return

        if (debug) {
            session.displayClientMessage(health.toString())
        }

        val inventory = session.localPlayer.inventory
        val offhand = inventory.offhand

        var itemNameTarget: String? = null
        if (!smart) {
            itemNameTarget = if (itemMode == 0) TOTEM else SHIELD
            if (itemName(offhand) == itemNameTarget) return
        } else {
            // Update shouldweswap flag FIRST (health hysteresis, same as PC)
            if (health >= swapBack) shouldWeSwap = true
            if (health <= swapToHealth) shouldWeSwap = false

            // PC: shield anywhere in the 36 inventory slots or in the offhand;
            // the full-range search covers content[0..40] which includes both
            val hasShield = inventory.searchForItem { itemName(it) == SHIELD } != null
            val hasTotem = inventory.searchForItem { itemName(it) == TOTEM } != null

            // Shield only if: flag says swap, surrounded check passes, AND shield exists
            val isSurrounded = if (surroundOnly && shouldWeSwap) horizontalCollision else true

            itemNameTarget = if (shouldWeSwap && isSurrounded && hasShield) SHIELD else TOTEM

            if (itemName(offhand) == itemNameTarget) return
        }

        if (itemNameTarget == null) return

        if (swapDelay < delay) {
            swapDelay++
            return
        }
        swapDelay = 0

        // PC: search the 36 inventory slots for the item
        val bestSlot = inventory.searchForItem(0 until 36) { itemName(it) == itemNameTarget }
            ?: return

        // Swap inventory[bestSlot] <-> offhand and sync the client UI
        inventory.moveItem(bestSlot, PlayerInventory.SLOT_OFFHAND, inventory, session)
    }
}
