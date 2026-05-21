package kr.kjh9211.cutthin

import kr.kjh9211.cutthin.api.CutThinAPI
import kr.kjh9211.cutthin.command.CutsceneCommand
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import kr.kjh9211.cutthin.event.CutsceneEndEvent
import org.bukkit.Bukkit
import kr.kjh9211.cutthin.loader.CutsceneLoader
import kr.kjh9211.cutthin.lock.PlayerLockListener
import kr.kjh9211.cutthin.lock.SessionLifecycleListener
import kr.kjh9211.cutthin.placeholder.CutThinPlaceholderBridge
import kr.kjh9211.cutthin.runner.CutsceneRunner
import kr.kjh9211.cutthin.runner.StepExecutor
import org.bukkit.plugin.java.JavaPlugin

class CutThin : JavaPlugin() {

    private lateinit var registry: CutsceneRegistry
    private lateinit var loader: CutsceneLoader
    private lateinit var runner: CutsceneRunner
    private lateinit var lockListener: PlayerLockListener
    private lateinit var placeholderBridge: CutThinPlaceholderBridge

    override fun onEnable() {
        saveDefaultConfig()

        registry = CutsceneRegistry()
        loader = CutsceneLoader(this)
        lockListener = PlayerLockListener(config)

        extractDefaultCutscenes()

        val executor = StepExecutor(this)
        runner = CutsceneRunner(
            plugin = this,
            executor = executor,
            onSessionStart = ::onSessionStart,
            onSessionEnd = ::onSessionEnd,
        )

        reloadCutscenes()

        val sessionLifecycleListener = SessionLifecycleListener(runner)
        server.pluginManager.registerEvents(lockListener, this)
        server.pluginManager.registerEvents(sessionLifecycleListener, this)

        val command = CutsceneCommand(
            registryProvider = { registry },
            runner = runner,
            reload = ::reloadCutscenes,
        )
        getCommand("cutscene")?.apply {
            setExecutor(command)
            tabCompleter = command
        } ?: logger.warning("Command 'cutscene' is missing from plugin.yml")

        CutThinAPI.bind(registry, runner, { reloadCutscenes() })

        placeholderBridge = CutThinPlaceholderBridge(this, { registry }, { runner })
        placeholderBridge.registerIfAvailable()

        logger.info("CutThin enabled with ${registry.size()} cutscenes")
    }

    override fun onDisable() {
        if (::runner.isInitialized) {
            runner.stopAll(CutsceneEndEvent.Reason.STOPPED)
        }
        if (::lockListener.isInitialized) {
            lockListener.clear()
        }
        if (::placeholderBridge.isInitialized) {
            placeholderBridge.close()
        }
        CutThinAPI.unbind()
    }

    private fun reloadCutscenes(): Int {
        reloadConfig()
        loader.loadAll(registry)
        logger.info("Loaded ${registry.size()} cutscenes from ${loader.cutscenesDirectory().absolutePath}")
        return registry.size()
    }

    private fun extractDefaultCutscenes() {
        loader.extractDefaultsIfMissing(
            listOf(
                "cutscenes/arcana-prologue-exile.yml",
                "cutscenes/arcana-memory-restore.yml",
                "cutscenes/arcana-final-boss.yml",
            )
        )
    }

    private fun onSessionStart(session: CutsceneSession) {
        if (session.cutscene.freeze) {
            lockListener.lock(session.playerId)
        }
    }

    private fun onSessionEnd(session: CutsceneSession, reason: CutsceneEndEvent.Reason) {
        lockListener.unlock(session.playerId)

        val snapshot = session.hiddenInventory
        if (snapshot != null) {
            val player = Bukkit.getPlayer(session.playerId)
            if (player != null && player.isOnline) {
                snapshot.restoreTo(player)
                session.hiddenInventory = null
            } else {
                logger.warning(
                    "Hidden inventory for ${session.playerName} could not be restored — player offline at session end (reason=$reason)"
                )
            }
        }

        if (config.getBoolean("debug")) {
            logger.info("Cutscene '${session.cutscene.id}' ended for ${session.playerName} (reason=$reason)")
        }
    }
}
