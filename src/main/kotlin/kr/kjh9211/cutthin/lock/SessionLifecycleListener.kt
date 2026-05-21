package kr.kjh9211.cutthin.lock

import kr.kjh9211.cutthin.event.CutsceneEndEvent
import kr.kjh9211.cutthin.runner.CutsceneRunner
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent

class SessionLifecycleListener(private val runner: CutsceneRunner) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onDeathBeforeStop(event: PlayerDeathEvent) {
        val playerId = event.entity.uniqueId
        val session = runner.session(playerId) ?: return
        val snapshot = session.hiddenInventory ?: return

        event.drops.addAll(snapshot.asDrops())
        session.hiddenInventory = null
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        val playerId = event.entity.uniqueId
        if (runner.isPlaying(playerId)) {
            runner.stop(playerId, CutsceneEndEvent.Reason.PLAYER_DEATH)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onQuitBeforeStop(event: PlayerQuitEvent) {
        val player = event.player
        val session = runner.session(player.uniqueId) ?: return
        val snapshot = session.hiddenInventory ?: return

        snapshot.restoreTo(player)
        session.hiddenInventory = null
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        if (runner.isPlaying(event.player.uniqueId)) {
            runner.stop(event.player.uniqueId, CutsceneEndEvent.Reason.PLAYER_QUIT)
        }
    }
}
