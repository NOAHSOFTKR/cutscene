package kr.kjh9211.cutthin.api

import kr.kjh9211.cutthin.cutscene.Cutscene
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.event.CutsceneEndEvent
import kr.kjh9211.cutthin.runner.CutsceneRunner
import org.bukkit.entity.Player
import java.util.UUID

object CutThinAPI {

    private var registry: CutsceneRegistry? = null
    private var runner: CutsceneRunner? = null
    private var reloadHandler: () -> Unit = {}

    @JvmStatic
    fun isInitialized(): Boolean = registry != null && runner != null

    @JvmStatic
    fun play(player: Player, cutsceneId: String): PlayResult {
        val registry = registry ?: return PlayResult.NOT_READY
        val runner = runner ?: return PlayResult.NOT_READY
        val cutscene: Cutscene = registry.find(cutsceneId) ?: return PlayResult.NOT_FOUND

        return when (runner.start(player, cutscene)) {
            is CutsceneRunner.StartResult.Started -> PlayResult.SUCCESS
            CutsceneRunner.StartResult.AlreadyPlaying -> PlayResult.ALREADY_PLAYING
            CutsceneRunner.StartResult.Cancelled -> PlayResult.CANCELLED
        }
    }

    @JvmStatic
    fun stop(player: Player): Boolean = stop(player.uniqueId)

    @JvmStatic
    fun stop(playerId: UUID): Boolean =
        runner?.stop(playerId, CutsceneEndEvent.Reason.STOPPED) ?: false

    @JvmStatic
    fun isPlaying(player: Player): Boolean = isPlaying(player.uniqueId)

    @JvmStatic
    fun isPlaying(playerId: UUID): Boolean = runner?.isPlaying(playerId) ?: false

    @JvmStatic
    fun currentCutscene(player: Player): String? = runner?.activeCutscene(player.uniqueId)

    @JvmStatic
    fun registerCutscene(cutscene: Cutscene) {
        registry?.register(cutscene)
    }

    @JvmStatic
    fun cutsceneIds(): Set<String> = registry?.ids() ?: emptySet()

    @JvmStatic
    fun cutscene(id: String): Cutscene? = registry?.find(id)

    @JvmStatic
    fun reload() {
        reloadHandler()
    }

    internal fun bind(
        registry: CutsceneRegistry,
        runner: CutsceneRunner,
        reloadHandler: () -> Unit,
    ) {
        this.registry = registry
        this.runner = runner
        this.reloadHandler = reloadHandler
    }

    internal fun unbind() {
        registry = null
        runner = null
        reloadHandler = {}
    }

    enum class PlayResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_PLAYING,
        CANCELLED,
        NOT_READY,
    }
}
