package kr.kjh9211.cutthin.loader

import kr.kjh9211.cutthin.cutscene.Cutscene
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.cutscene.CutsceneStep
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class CutsceneLoader(
    private val plugin: JavaPlugin,
    private val stepParser: StepParser = StepParser(),
) {
    fun cutscenesDirectory(): File = File(plugin.dataFolder, "cutscenes")

    fun extractDefaultsIfMissing(defaults: List<String>) {
        val directory = cutscenesDirectory()
        if (directory.exists() && directory.listFiles { f -> f.extension.equals("yml", true) }?.isNotEmpty() == true) {
            return
        }
        directory.mkdirs()
        defaults.forEach { resourcePath ->
            try {
                plugin.saveResource(resourcePath, false)
            } catch (ex: IllegalArgumentException) {
                plugin.logger.warning("Default cutscene resource missing: $resourcePath (${ex.message})")
            }
        }
    }

    fun loadAll(registry: CutsceneRegistry) {
        registry.clear()
        val directory = cutscenesDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
            return
        }

        val defaults = plugin.config
        val fadeIn = defaults.getInt("default-fade.in", 10)
        val stay = defaults.getInt("default-fade.stay", 60)
        val fadeOut = defaults.getInt("default-fade.out", 20)

        val seen = mutableSetOf<String>()
        directory.walkTopDown()
            .filter { it.isFile && (it.extension.equals("yml", true) || it.extension.equals("yaml", true)) }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                val cutscene = runCatching { loadFromFile(file, fadeIn, stay, fadeOut) }
                    .onFailure { ex ->
                        plugin.logger.warning("Failed to load cutscene '${file.name}': ${ex.message}")
                    }
                    .getOrNull() ?: return@forEach

                if (!seen.add(cutscene.id.lowercase())) {
                    plugin.logger.warning("Duplicate cutscene id '${cutscene.id}' in ${file.absolutePath}; skipping.")
                    return@forEach
                }
                registry.register(cutscene)
            }
    }

    private fun loadFromFile(file: File, fadeIn: Int, stay: Int, fadeOut: Int): Cutscene {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val id = yaml.getString("id")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("missing 'id'")

        val name = yaml.getString("name")?.ifBlank { id } ?: id
        val freeze = yaml.getBoolean("freeze", true)

        val rawSteps = yaml.getList("steps")
            ?: throw IllegalArgumentException("missing 'steps' list")

        val steps: List<CutsceneStep> = rawSteps.mapIndexed { index, raw ->
            try {
                stepParser.parse(raw, fadeIn, stay, fadeOut)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException("step #${index + 1}: ${ex.message}", ex)
            }
        }

        return Cutscene(
            id = id,
            name = name,
            freeze = freeze,
            steps = steps,
            sourceFile = file.absolutePath,
        )
    }
}
