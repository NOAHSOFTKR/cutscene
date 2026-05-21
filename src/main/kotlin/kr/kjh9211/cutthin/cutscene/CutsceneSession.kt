package kr.kjh9211.cutthin.cutscene

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.Collections
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

    val subTasks: MutableList<BukkitTask> = Collections.synchronizedList(mutableListOf())

    fun addSubTask(task: BukkitTask) {
        subTasks.add(task)
    }

    fun cancelPending() {
        pendingTask?.let { task ->
            if (!task.isCancelled) task.cancel()
        }
        pendingTask = null
        synchronized(subTasks) {
            subTasks.forEach { if (!it.isCancelled) it.cancel() }
            subTasks.clear()
        }
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
