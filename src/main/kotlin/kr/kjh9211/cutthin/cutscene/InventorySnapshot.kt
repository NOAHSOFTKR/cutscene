package kr.kjh9211.cutthin.cutscene

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class InventorySnapshot private constructor(
    private val contents: Array<ItemStack?>,
    private val armor: Array<ItemStack?>,
    private val offHand: ItemStack?,
) {

    fun restoreTo(player: Player) {
        val inv = player.inventory
        inv.contents = cloneArray(contents)
        inv.setArmorContents(cloneArray(armor))
        inv.setItemInOffHand(offHand?.clone())
    }

    /**
     * Returns a flat list of non-empty items for use with `PlayerDeathEvent.getDrops()`.
     */
    fun asDrops(): List<ItemStack> {
        val out = mutableListOf<ItemStack>()
        contents.forEach { it?.takeIf { stack -> stack.type != Material.AIR }?.let(out::add) }
        armor.forEach { it?.takeIf { stack -> stack.type != Material.AIR }?.let(out::add) }
        offHand?.takeIf { it.type != Material.AIR }?.let(out::add)
        return out
    }

    private fun cloneArray(source: Array<ItemStack?>): Array<ItemStack?> =
        Array(source.size) { idx -> source[idx]?.clone() }

    companion object {
        fun capture(player: Player): InventorySnapshot {
            val inv = player.inventory
            return InventorySnapshot(
                contents = Array(inv.contents.size) { idx -> inv.contents[idx]?.clone() },
                armor = Array(inv.armorContents.size) { idx -> inv.armorContents[idx]?.clone() },
                offHand = inv.itemInOffHand.clone().takeIf { it.type != Material.AIR },
            )
        }
    }
}
