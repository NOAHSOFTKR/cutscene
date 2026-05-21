package kr.kjh9211.cutthin.cutscene

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class CutsceneSession(
    val playerId: UUID,
    val playerName: String,
    val cutscene: Cutscene,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val originLocation: Location,
) {
    @Volatile
    var stepIndex: Int = 0

    @Volatile
    var pendingTask: BukkitTask? = null

    @Volatile
    var stopped: Boolean = false

    @Volatile
    var hiddenInventory: InventorySnapshot? = null

    fun cancelPending() {
        pendingTask?.let { task ->
            if (!task.isCancelled) task.cancel()
        }
        pendingTask = null
    }

    companion object {
        fun snapshot(player: Player, cutscene: Cutscene): CutsceneSession =
            CutsceneSession(
                playerId = player.uniqueId,
                playerName = player.name,
                cutscene = cutscene,
                originLocation = player.location.clone(),
            )
    }
}
