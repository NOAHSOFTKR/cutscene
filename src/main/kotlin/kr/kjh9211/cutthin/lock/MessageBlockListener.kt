package kr.kjh9211.cutthin.lock

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * Event-level fallback for blocking incoming messages to locked players.
 * Covers: public chat (AsyncPlayerChatEvent), whisper commands (/msg etc.),
 * system join/quit/death broadcasts.
 *
 * Does NOT cover plugin-direct Player.sendMessage() or Bukkit.broadcastMessage()
 * calls — those require ProtocolLib (see PacketChatBlocker).
 */
class MessageBlockListener(
    private val config: FileConfiguration,
    private val isLocked: (UUID) -> Boolean,
) : Listener {

    private fun preventReceiveMessages(): Boolean =
        config.getBoolean("prevent-during-cutscene.receive-messages", true)

    private fun preventReceiveWhispers(): Boolean =
        config.getBoolean("prevent-during-cutscene.receive-whispers", true)

    private fun preventReceiveSystem(): Boolean =
        config.getBoolean("prevent-during-cutscene.receive-system", true)

    private fun whisperCommands(): Set<String> {
        val list = config.getStringList("whisper-commands")
            .ifEmpty { DEFAULT_WHISPER_COMMANDS }
        return list.map { it.lowercase().let { s -> if (s.startsWith("/")) s else "/$s" } }.toSet()
    }

    private fun whisperBlockedMessage(targetName: String): String {
        val template = config.getString("whisper-blocked-message", DEFAULT_WHISPER_BLOCKED)
            ?: DEFAULT_WHISPER_BLOCKED
        return ChatColor.translateAlternateColorCodes('&', template.replace("{target}", targetName))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!preventReceiveMessages()) return
        event.recipients.removeIf { isLocked(it.uniqueId) }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onWhisper(event: PlayerCommandPreprocessEvent) {
        if (!preventReceiveWhispers()) return
        val tokens = event.message.split(" ")
        val cmd = tokens.firstOrNull()?.lowercase() ?: return
        if (cmd !in whisperCommands()) return
        val targetName = tokens.getOrNull(1) ?: return
        val target: Player = Bukkit.getPlayerExact(targetName) ?: return
        if (!isLocked(target.uniqueId)) return
        event.isCancelled = true
        event.player.sendMessage(whisperBlockedMessage(target.name))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        if (!preventReceiveSystem()) return
        val message = event.joinMessage ?: return
        if (!hasLockedReceivers(event.player)) return
        event.joinMessage = null
        rebroadcastToUnlocked(message, except = event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuit(event: PlayerQuitEvent) {
        if (!preventReceiveSystem()) return
        val message = event.quitMessage ?: return
        if (!hasLockedReceivers(event.player)) return
        event.quitMessage = null
        rebroadcastToUnlocked(message, except = event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDeath(event: PlayerDeathEvent) {
        if (!preventReceiveSystem()) return
        val message = event.deathMessage ?: return
        if (!hasLockedReceivers(event.entity)) return
        event.deathMessage = null
        rebroadcastToUnlocked(message, except = event.entity)
    }

    private fun hasLockedReceivers(except: Player): Boolean =
        Bukkit.getOnlinePlayers().any { it.uniqueId != except.uniqueId && isLocked(it.uniqueId) }

    private fun rebroadcastToUnlocked(message: String, except: Player) {
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId != except.uniqueId && !isLocked(it.uniqueId) }
            .forEach { it.sendMessage(message) }
        Bukkit.getConsoleSender().sendMessage(message)
    }

    companion object {
        private val DEFAULT_WHISPER_COMMANDS = listOf("/msg", "/tell", "/w", "/whisper", "/pm")
        private const val DEFAULT_WHISPER_BLOCKED = "&c{target}님은 지금 메시지를 받을 수 없는 상태입니다."
    }
}
