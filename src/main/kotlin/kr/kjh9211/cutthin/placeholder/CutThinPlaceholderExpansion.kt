package kr.kjh9211.cutthin.placeholder

import kr.kjh9211.cutthin.CutThin
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.runner.CutsceneRunner
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

class CutThinPlaceholderExpansion(
    private val plugin: CutThin,
    private val registryProvider: () -> CutsceneRegistry,
    private val runnerProvider: () -> CutsceneRunner,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "cutthin"

    override fun getAuthor(): String =
        plugin.description.authors.joinToString(", ").ifBlank { "unknown" }

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val registry = registryProvider()
        val runner = runnerProvider()
        val pieces = params.split(":")
        val head = pieces.firstOrNull()?.lowercase() ?: return null
        val arg = pieces.getOrNull(1)
        val playerId = player?.uniqueId

        return when (head) {
            "count" -> registry.size().toString()

            "active" -> {
                if (arg == null) {
                    (playerId != null && runner.isPlaying(playerId)).toString()
                } else {
                    val current = playerId?.let { runner.activeCutscene(it) }
                    current.equals(arg, ignoreCase = true).toString()
                }
            }

            "current", "current_id" -> playerId?.let { runner.activeCutscene(it) }.orEmpty()

            "current_name" -> {
                val id = playerId?.let { runner.activeCutscene(it) } ?: return ""
                registry.find(id)?.name.orEmpty()
            }

            "step_index" -> {
                val session = playerId?.let { runner.session(it) } ?: return "-1"
                session.stepIndex.toString()
            }

            "total_steps" -> {
                val session = playerId?.let { runner.session(it) } ?: return "0"
                session.cutscene.steps.size.toString()
            }

            "progress" -> {
                val session = playerId?.let { runner.session(it) } ?: return "0/0"
                "${session.stepIndex}/${session.cutscene.steps.size}"
            }

            "exists" -> {
                if (arg.isNullOrBlank()) return "false"
                (registry.find(arg) != null).toString()
            }

            "name" -> {
                if (arg.isNullOrBlank()) return ""
                registry.find(arg)?.name.orEmpty()
            }

            "steps" -> {
                if (arg.isNullOrBlank()) return "0"
                (registry.find(arg)?.steps?.size ?: 0).toString()
            }

            else -> null
        }
    }
}
