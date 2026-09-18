package com.gato.client.game.module.misc

import com.gato.client.game.InterceptablePacket
import com.gato.client.game.Module
import com.gato.client.game.ModuleCategory
import com.gato.client.game.inventory.PlayerInventory
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket

/**
 * Port of Zenryox Client's AutoTotemModule (game/module/combat/AutoTotemModule.kt),
 * adapted to Gato's Module/PlayerInventory API. Requested directly by the user as a
 * straight port instead of Gato's own PC-parity Offhand implementation.
 *
 * Note: this is simpler than the previous Offhand module on purpose, matching
 * Zenryox exactly - no Shield swapping, no health-hysteresis "Smart" mode, no
 * SurroundOnly. Only totem-to-offhand (or totem-to-hand, if Prefer Main Hand is on).
 * Zenryox's CombatCoordinator inventory-lock dependency was dropped since Gato has
 * no equivalent cross-module lock; nothing else in Gato reads it.
 */
class OffhandModule : Module("Offhand", ModuleCategory.Misc) {

    private var delay by intValue("Delay (ms)", 50, 0..1000)
    private var onlyLowHealth by boolValue("Only Low Health", false)
    private var healthThreshold by intValue("Health Threshold", 10, 1..20)
    private var replaceOffhand by boolValue("Replace Offhand", true)
    private var preferMainHand by boolValue("Prefer Main Hand", false)
    private var hotbarPriority by boolValue("Hotbar Priority", true)
    private var debug by boolValue("Debug", false)

    private var lastTotemTime = 0L
    private var health = 20f

    private val TOTEM = "minecraft:totem_of_undying"

    private var lastDebugMsg: String? = null
    private fun dbg(msg: String) {
        if (!debug) return
        if (msg == lastDebugMsg) return
        lastDebugMsg = msg
        session.displayClientMessage("[Offhand] $msg")
    }

    private fun isTotem(item: ItemData) =
        item != ItemData.AIR && item.definition?.identifier == TOTEM

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        when (val packet = interceptablePacket.packet) {
            is UpdateAttributesPacket -> {
                if (isSessionCreated && packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                    packet.attributes.find { it.name == "minecraft:health" }?.let { health = it.value }
                }
            }

            is PlayerAuthInputPacket -> tick()
        }
    }

    private fun tick() {
        if (!isSessionCreated || !isEnabled) return

        val now = System.currentTimeMillis()
        val inventory = session.localPlayer.inventory

        if (onlyLowHealth && health > healthThreshold) {
            dbg("hp=$health | esperando (Only Low Health, umbral=$healthThreshold)")
            return
        }
        if (now - lastTotemTime < delay) return

        val target = if (preferMainHand) inventory.hand else inventory.offhand
        if (isTotem(target)) {
            dbg("hp=$health | ya tiene totem en " + (if (preferMainHand) "mano principal" else "offhand"))
            return
        }

        if (!replaceOffhand && !preferMainHand) {
            val offhand = inventory.offhand
            if (offhand != ItemData.AIR && !isTotem(offhand)) {
                dbg("hp=$health | offhand ocupado con otra cosa, Replace Offhand esta apagado")
                return
            }
        }

        val totemSlot = findTotemSlot(inventory)
        if (totemSlot == null) {
            dbg("hp=$health | NO encontre totem en el inventario")
            return
        }

        val targetSlot = if (preferMainHand) inventory.heldItemSlot else PlayerInventory.SLOT_OFFHAND
        if (totemSlot == targetSlot) return

        dbg("hp=$health | moviendo totem desde slot $totemSlot a slot $targetSlot")
        inventory.moveItem(totemSlot, targetSlot, inventory, session)
        lastTotemTime = now
    }

    private fun findTotemSlot(inventory: PlayerInventory): Int? {
        if (hotbarPriority) {
            inventory.searchForItemInHotbar { isTotem(it) }?.let { return it }
        }
        return inventory.searchForItem(0 until 36) { isTotem(it) }
    }
}
