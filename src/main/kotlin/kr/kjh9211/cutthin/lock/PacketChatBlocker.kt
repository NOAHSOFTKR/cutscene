package kr.kjh9211.cutthin.lock

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Packet-level chat blocker — used only when ProtocolLib is present.
 * Cancels outgoing SYSTEM_CHAT / PLAYER_CHAT / DISGUISED_CHAT packets to locked players,
 * which catches every text path including plugin-direct Player.sendMessage() and
 * Bukkit.broadcastMessage() that the event-based listener cannot reach.
 *
 * Cutscene-originated chat is allowed through via [bypassed]: we mark the receiving
 * player as "bypassing" while StepExecutor calls sendMessage. The packet pipeline
 * runs synchronously from that call, so a non-deferred Set works.
 */
class PacketChatBlocker(
    private val plugin: Plugin,
    private val isLocked: (UUID) -> Boolean,
) {
    private val bypassing: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var adapter: PacketAdapter? = null

    fun bypassed(player: Player, action: () -> Unit) {
        bypassing.add(player.uniqueId)
        try {
            action()
        } finally {
            bypassing.remove(player.uniqueId)
        }
    }

    fun register() {
        if (adapter != null) return
        val manager = ProtocolLibrary.getProtocolManager()

        val packetTypes = mutableListOf(PacketType.Play.Server.SYSTEM_CHAT)
        runCatching { packetTypes.add(PacketType.Play.Server.DISGUISED_CHAT) }
        runCatching { packetTypes.add(PacketType.Play.Server.CHAT) }

        val listener = object : PacketAdapter(
            plugin,
            ListenerPriority.NORMAL,
            *packetTypes.toTypedArray(),
        ) {
            override fun onPacketSending(event: PacketEvent) {
                val player = event.player ?: return
                val id = player.uniqueId
                if (id in bypassing) return
                if (isLocked(id)) {
                    event.isCancelled = true
                }
            }
        }
        manager.addPacketListener(listener)
        adapter = listener
        plugin.logger.info("PacketChatBlocker registered — full chat suppression active")
    }

    fun unregister() {
        val listener = adapter ?: return
        ProtocolLibrary.getProtocolManager().removePacketListener(listener)
        adapter = null
        plugin.logger.info("PacketChatBlocker unregistered")
    }
}
