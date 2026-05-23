package kr.kjh9211.cutthin.runner

import kr.kjh9211.cutthin.cutscene.Cutscene
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import kr.kjh9211.cutthin.event.CutsceneEndEvent
import kr.kjh9211.cutthin.event.CutsceneStartEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CutsceneRunner(
    private val plugin: JavaPlugin,
    private val executor: StepExecutor,
    private val onSessionStart: (CutsceneSession) -> Unit,
    private val onSessionEnd: (CutsceneSession, CutsceneEndEvent.Reason) -> Unit,
) {

    private val sessions = ConcurrentHashMap<UUID, CutsceneSession>()
    private val allowConcurrent: Boolean
        get() = plugin.config.getBoolean("allow-concurrent", false)

    fun isPlaying(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun activeCutscene(playerId: UUID): String? = sessions[playerId]?.cutscene?.id

    fun activeSessions(): Collection<CutsceneSession> = sessions.values

    fun session(playerId: UUID): CutsceneSession? = sessions[playerId]

    fun start(player: Player, cutscene: Cutscene): StartResult {
        if (!allowConcurrent && isPlaying(player.uniqueId)) {
            return StartResult.AlreadyPlaying
        }

        val session = CutsceneSession.snapshot(player, cutscene)

        // Insert into map before firing the event: if an event handler calls start() again
        // for the same player, putIfAbsent ensures only one session wins.
        if (!allowConcurrent && sessions.putIfAbsent(player.uniqueId, session) != null) {
            return StartResult.AlreadyPlaying
        } else if (allowConcurrent) {
            sessions[player.uniqueId] = session
        }

        val startEvent = CutsceneStartEvent(player, cutscene)
        Bukkit.getPluginManager().callEvent(startEvent)
        if (startEvent.isCancelled) {
            sessions.remove(player.uniqueId, session)
            return StartResult.Cancelled
        }

        onSessionStart(session)
        scheduleNext(session, 0L)
        return StartResult.Started(session)
    }

    fun stop(playerId: UUID, reason: CutsceneEndEvent.Reason = CutsceneEndEvent.Reason.STOPPED): Boolean {
        val session = sessions.remove(playerId) ?: return false
        session.stopped = true
        session.cancelPending()
        val player = Bukkit.getPlayer(playerId)
        if (player != null) {
            Bukkit.getPluginManager().callEvent(CutsceneEndEvent(player, session.cutscene, reason))
        }
        onSessionEnd(session, reason)
        return true
    }

    fun stopAll(reason: CutsceneEndEvent.Reason = CutsceneEndEvent.Reason.STOPPED) {
        sessions.keys.toList().forEach { stop(it, reason) }
    }

    private fun scheduleNext(session: CutsceneSession, delayTicks: Long) {
        if (session.stopped) return

        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            runStep(session)
        }, delayTicks.coerceAtLeast(0))
        session.pendingTask = task
    }

    private fun runStep(session: CutsceneSession) {
        if (session.stopped) return

        val player = Bukkit.getPlayer(session.playerId)
        if (player == null || !player.isOnline) {
            stop(session.playerId, CutsceneEndEvent.Reason.PLAYER_QUIT)
            return
        }

        val steps = session.cutscene.steps
        if (session.stepIndex >= steps.size) {
            finish(session)
            return
        }

        val step = steps[session.stepIndex]
        val nextDelay = try {
            executor.execute(step, session)
        } catch (ex: Exception) {
            plugin.logger.warning(
                "Cutscene '${session.cutscene.id}' step #${session.stepIndex + 1} threw: ${ex.message}"
            )
            stop(session.playerId, CutsceneEndEvent.Reason.ERROR)
            return
        }

        if (nextDelay < 0L) {
            stop(session.playerId, CutsceneEndEvent.Reason.PLAYER_QUIT)
            return
        }

        session.stepIndex++
        if (session.stepIndex >= steps.size) {
            if (nextDelay > 0L) {
                scheduleNext(session, nextDelay)
            } else {
                finish(session)
            }
        } else {
            scheduleNext(session, nextDelay)
        }
    }

    private fun finish(session: CutsceneSession) {
        session.stopped = true
        if (sessions.remove(session.playerId) == null) return
        session.cancelPending()
        val player = Bukkit.getPlayer(session.playerId)
        if (player != null) {
            Bukkit.getPluginManager().callEvent(
                CutsceneEndEvent(player, session.cutscene, CutsceneEndEvent.Reason.COMPLETED)
            )
        }
        onSessionEnd(session, CutsceneEndEvent.Reason.COMPLETED)
    }

    sealed class StartResult {
        data class Started(val session: CutsceneSession) : StartResult()
        object AlreadyPlaying : StartResult()
        object Cancelled : StartResult()
    }
}
