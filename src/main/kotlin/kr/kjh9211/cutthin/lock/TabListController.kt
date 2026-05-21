package kr.kjh9211.cutthin.lock

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TabMode {
    HEADER_FOOTER,
    HIDE_PLAYERS,
    NONE;

    companion object {
        fun parse(value: String?): TabMode =
            when (value?.uppercase()?.replace('-', '_')) {
                null, "", "HEADER_FOOTER", "HEADERFOOTER" -> HEADER_FOOTER
                "HIDE_PLAYERS", "HIDEPLAYERS", "HIDE" -> HIDE_PLAYERS
                "NONE", "OFF" -> NONE
                else -> HEADER_FOOTER
            }
    }
}

/**
 * Manages tab key visual suppression during cutscenes.
 *
 *  - [TabMode.HEADER_FOOTER]: overwrites the tab list header/footer with cinematic placeholder text.
 *  - [TabMode.HIDE_PLAYERS]: removes other players from the locked player's perception (world + tab).
 *  - [TabMode.NONE]: disabled.
 *
 * Also listens for join events to keep newly-joined players hidden from any in-progress cutscenes.
 */
class TabListController(
    private val plugin: Plugin,
    private val config: FileConfiguration,
) : Listener {

    private val applied: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val mode: TabMode get() = TabMode.parse(config.getString("tab-mode"))
    private val header: String get() = colorize(config.getString("tab-header", DEFAULT_HEADER) ?: DEFAULT_HEADER)
    private val footer: String get() = colorize(config.getString("tab-footer", DEFAULT_FOOTER) ?: DEFAULT_FOOTER)

    fun apply(player: Player) {
        applied.add(player.uniqueId)
        when (mode) {
            TabMode.HEADER_FOOTER -> {
                @Suppress("DEPRECATION")
                player.setPlayerListHeaderFooter(header, footer)
            }
            TabMode.HIDE_PLAYERS -> {
                Bukkit.getOnlinePlayers()
                    .filter { it.uniqueId != player.uniqueId }
                    .forEach {
                        @Suppress("DEPRECATION")
                        player.hidePlayer(plugin, it)
                    }
            }
            TabMode.NONE -> Unit
        }
    }

    fun release(player: Player) {
        applied.remove(player.uniqueId)
        when (mode) {
            TabMode.HEADER_FOOTER -> {
                @Suppress("DEPRECATION")
                player.setPlayerListHeaderFooter(null, null)
            }
            TabMode.HIDE_PLAYERS -> {
                Bukkit.getOnlinePlayers()
                    .filter { it.uniqueId != player.uniqueId }
                    .forEach {
                        @Suppress("DEPRECATION")
                        player.showPlayer(plugin, it)
                    }
            }
            TabMode.NONE -> Unit
        }
    }

    fun clear() {
        applied.clear()
    }

    fun isApplied(playerId: UUID): Boolean = applied.contains(playerId)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (mode != TabMode.HIDE_PLAYERS) return
        val joined = event.player
        applied.forEach { lockedId ->
            val locked = Bukkit.getPlayer(lockedId) ?: return@forEach
            if (locked.uniqueId == joined.uniqueId) return@forEach
            @Suppress("DEPRECATION")
            locked.hidePlayer(plugin, joined)
        }
    }

    private fun colorize(value: String): String =
        org.bukkit.ChatColor.translateAlternateColorCodes('&', value)

    companion object {
        private const val DEFAULT_HEADER = "&c&l컷신 진행 중\n&7잠시만 기다려주세요\n\n"
        private const val DEFAULT_FOOTER = "\n\n&8(컷신이 끝날 때까지 잠시만)"
    }
}
