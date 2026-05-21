package kr.kjh9211.cutthin.lock

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerLockListener(private val config: FileConfiguration) : Listener {

    private val locked: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val allowedCommands = setOf("/cutscene", "/cs", "/cut")

    fun lock(playerId: UUID) {
        locked.add(playerId)
    }

    fun unlock(playerId: UUID) {
        locked.remove(playerId)
    }

    fun isLocked(playerId: UUID): Boolean = locked.contains(playerId)

    fun clear() {
        locked.clear()
    }

    private fun preventMovement(): Boolean = config.getBoolean("prevent-during-cutscene.movement", true)
    private fun preventInteraction(): Boolean = config.getBoolean("prevent-during-cutscene.interaction", true)
    private fun preventDamage(): Boolean = config.getBoolean("prevent-during-cutscene.damage", true)
    private fun preventInventory(): Boolean = config.getBoolean("prevent-during-cutscene.inventory", true)
    private fun preventDrop(): Boolean = config.getBoolean("prevent-during-cutscene.drop", true)
    private fun preventCommand(): Boolean = config.getBoolean("prevent-during-cutscene.command", true)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!isLocked(event.player.uniqueId) || !preventMovement()) return
        val from = event.from
        val to = event.to ?: return
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            event.setTo(from.clone().apply { yaw = to.yaw; pitch = to.pitch })
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (!isLocked(event.player.uniqueId) || !preventInteraction()) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val entity = event.entity as? Player ?: return
        if (!isLocked(entity.uniqueId) || !preventDamage()) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        if (!isLocked(attacker.uniqueId) || !preventDamage()) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!isLocked(player.uniqueId) || !preventInventory()) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (!isLocked(event.player.uniqueId) || !preventDrop()) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!isLocked(event.player.uniqueId) || !preventCommand()) return
        if (event.player.isOp) return
        val firstToken = event.message.split(" ").firstOrNull()?.lowercase() ?: return
        if (firstToken in allowedCommands) return
        event.isCancelled = true
        event.player.sendMessage("§c컷신 진행 중에는 명령어를 사용할 수 없습니다.")
    }
}
