package kr.kjh9211.cutthin

import kr.kjh9211.cutthin.api.CutThinAPI
import kr.kjh9211.cutthin.command.CutsceneCommand
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import kr.kjh9211.cutthin.event.CutsceneEndEvent
import kr.kjh9211.cutthin.loader.CutsceneLoader
import kr.kjh9211.cutthin.lock.MessageBlockListener
import kr.kjh9211.cutthin.lock.PacketChatBlocker
import kr.kjh9211.cutthin.lock.PlayerLockListener
import kr.kjh9211.cutthin.lock.SessionLifecycleListener
import kr.kjh9211.cutthin.lock.TabListController
import kr.kjh9211.cutthin.placeholder.CutThinPlaceholderBridge
import kr.kjh9211.cutthin.runner.CutsceneRunner
import kr.kjh9211.cutthin.runner.StepExecutor
import kr.kjh9211.cutthin.spike.CameraSpikeCommand // TEMP Phase 0 spike — remove before commit
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CutThin : JavaPlugin() {

    private lateinit var registry: CutsceneRegistry
    private lateinit var loader: CutsceneLoader
    private lateinit var runner: CutsceneRunner
    private lateinit var lockListener: PlayerLockListener
    private lateinit var tabListController: TabListController
    private lateinit var messageBlockListener: MessageBlockListener
    private lateinit var placeholderBridge: CutThinPlaceholderBridge
    private var packetChatBlocker: PacketChatBlocker? = null

    override fun onEnable() {
        saveDefaultConfig()

        registry = CutsceneRegistry()
        loader = CutsceneLoader(this)
        lockListener = PlayerLockListener(config)
        tabListController = TabListController(this, config)
        messageBlockListener = MessageBlockListener(config, lockListener::isLocked)

        if (server.pluginManager.getPlugin("ProtocolLib") != null) {
            try {
                val blocker = PacketChatBlocker(this, lockListener::isLocked)
                blocker.register()
                packetChatBlocker = blocker
            } catch (ex: Throwable) {
                logger.warning("ProtocolLib found but PacketChatBlocker init failed: ${ex.message}")
            }
        } else {
            logger.info("ProtocolLib not found — chat suppression uses event-based fallback only")
        }

        extractDefaultCutscenes()

        val executor = StepExecutor(this, packetChatBlocker)
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
        server.pluginManager.registerEvents(tabListController, this)
        server.pluginManager.registerEvents(messageBlockListener, this)

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

        // TEMP Phase 0 spike — remove this block before commit
        getCommand("cameraspike")?.setExecutor(CameraSpikeCommand(this))

        placeholderBridge = CutThinPlaceholderBridge(this, { registry }, { runner })
        placeholderBridge.registerIfAvailable()

        logger.info("CutThin enabled with ${registry.size()} cutscenes")
    }

    override fun onDisable() {
        if (::runner.isInitialized) {
            runner.stopAll(CutsceneEndEvent.Reason.STOPPED)
        }
        packetChatBlocker?.unregister()
        if (::lockListener.isInitialized) {
            lockListener.clear()
        }
        if (::tabListController.isInitialized) {
            tabListController.clear()
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
        if (!session.cutscene.freeze) return
        lockListener.lock(session.playerId)
        val player: Player = Bukkit.getPlayer(session.playerId) ?: return
        tabListController.apply(player)
        if (config.getBoolean("auto-clear-chat-on-start", true)) {
            clearChat(player)
        }
    }

    private fun onSessionEnd(session: CutsceneSession, reason: CutsceneEndEvent.Reason) {
        lockListener.unlock(session.playerId)
        val player: Player? = Bukkit.getPlayer(session.playerId)
        if (player != null) {
            tabListController.release(player)
        }

        // On death, onDeathBeforeStop already added the snapshot to event.drops — skip restore.
        if (reason != CutsceneEndEvent.Reason.PLAYER_DEATH) {
            val snapshot = session.hiddenInventory
            if (snapshot != null) {
                session.hiddenInventory = null
                if (player != null && player.isOnline) {
                    snapshot.restoreTo(player)
                } else {
                    logger.warning(
                        "Hidden inventory for ${session.playerName} could not be restored — player offline at session end (reason=$reason)"
                    )
                }
            }
        }

        if (config.getBoolean("debug")) {
            logger.info("Cutscene '${session.cutscene.id}' ended for ${session.playerName} (reason=$reason)")
        }
    }

    private fun clearChat(player: Player) {
        val blocker = packetChatBlocker
        repeat(100) {
            if (blocker != null) blocker.bypassed(player) { player.sendMessage(" ") }
            else player.sendMessage(" ")
        }
    }
}
