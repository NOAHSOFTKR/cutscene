package kr.kjh9211.cutthin.placeholder

import kr.kjh9211.cutthin.CutThin
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.runner.CutsceneRunner
import org.bukkit.Bukkit

class CutThinPlaceholderBridge(
    private val plugin: CutThin,
    private val registryProvider: () -> CutsceneRegistry,
    private val runnerProvider: () -> CutsceneRunner,
) {
    private var expansion: CutThinPlaceholderExpansion? = null

    fun registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.logger.info("PlaceholderAPI not found; cutthin placeholders are disabled.")
            return
        }

        if (expansion == null) {
            expansion = CutThinPlaceholderExpansion(plugin, registryProvider, runnerProvider)
        }
        expansion?.register()
    }

    fun close() {
        expansion = null
    }
}
