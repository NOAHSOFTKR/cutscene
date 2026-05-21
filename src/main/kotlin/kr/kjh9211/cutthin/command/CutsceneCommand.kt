package kr.kjh9211.cutthin.command

import kr.kjh9211.cutthin.api.CutThinAPI
import kr.kjh9211.cutthin.cutscene.CutsceneRegistry
import kr.kjh9211.cutthin.runner.CutsceneRunner
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class CutsceneCommand(
    private val registryProvider: () -> CutsceneRegistry,
    private val runner: CutsceneRunner,
    private val reload: () -> Int,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender, label)
            return true
        }

        return when (args[0].lowercase()) {
            "play" -> handlePlay(sender, args.drop(1))
            "stop" -> handleStop(sender, args.drop(1))
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args.drop(1))
            "reload" -> handleReload(sender)
            else -> {
                sendHelp(sender, label)
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        val registry = registryProvider()
        return when (args.size) {
            1 -> complete(args[0], "play", "stop", "list", "info", "reload")
            2 -> when (args[0].lowercase()) {
                "play", "info" -> complete(args[1], registry.ids().toList())
                "stop" -> complete(args[1], onlinePlayerNames())
                else -> mutableListOf()
            }
            3 -> when (args[0].lowercase()) {
                "play" -> complete(args[2], onlinePlayerNames())
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }

    private fun handlePlay(sender: CommandSender, rest: List<String>): Boolean {
        if (!sender.hasPermission("cutthin.play")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }
        val id = rest.getOrNull(0)
        if (id.isNullOrBlank()) {
            sender.sendMessage("${ChatColor.RED}사용법: /cutscene play <id> [player]")
            return true
        }

        val target: Player = rest.getOrNull(1)?.let { Bukkit.getPlayerExact(it) }
            ?: (sender as? Player)
            ?: run {
                sender.sendMessage("${ChatColor.RED}대상 플레이어를 지정해야 합니다.")
                return true
            }

        return when (val result = CutThinAPI.play(target, id)) {
            CutThinAPI.PlayResult.SUCCESS -> {
                sender.sendMessage("${ChatColor.GREEN}컷신 '$id' 재생 시작 → ${target.name}")
                true
            }
            CutThinAPI.PlayResult.NOT_FOUND -> {
                sender.sendMessage("${ChatColor.RED}존재하지 않는 컷신: $id")
                true
            }
            CutThinAPI.PlayResult.ALREADY_PLAYING -> {
                sender.sendMessage("${ChatColor.YELLOW}${target.name}은(는) 이미 컷신 재생 중입니다.")
                true
            }
            CutThinAPI.PlayResult.CANCELLED -> {
                sender.sendMessage("${ChatColor.YELLOW}다른 플러그인이 컷신 시작을 취소했습니다.")
                true
            }
            CutThinAPI.PlayResult.NOT_READY -> {
                sender.sendMessage("${ChatColor.RED}CutThin이 아직 준비되지 않았습니다.")
                true
            }
        }
    }

    private fun handleStop(sender: CommandSender, rest: List<String>): Boolean {
        if (!sender.hasPermission("cutthin.stop")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }
        val target: Player = rest.getOrNull(0)?.let { Bukkit.getPlayerExact(it) }
            ?: (sender as? Player)
            ?: run {
                sender.sendMessage("${ChatColor.RED}대상 플레이어를 지정해야 합니다.")
                return true
            }

        val stopped = runner.stop(target.uniqueId)
        if (stopped) {
            sender.sendMessage("${ChatColor.GREEN}${target.name}의 컷신을 중단했습니다.")
        } else {
            sender.sendMessage("${ChatColor.YELLOW}${target.name}은(는) 컷신 재생 중이 아닙니다.")
        }
        return true
    }

    private fun handleList(sender: CommandSender): Boolean {
        if (!sender.hasPermission("cutthin.list")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }
        val registry = registryProvider()
        if (registry.size() == 0) {
            sender.sendMessage("${ChatColor.GRAY}등록된 컷신이 없습니다.")
            return true
        }
        sender.sendMessage("${ChatColor.AQUA}=== 등록된 컷신 (${registry.size()}) ===")
        registry.all().sortedBy { it.id }.forEach { cs ->
            sender.sendMessage(
                "${ChatColor.GRAY}- ${ChatColor.WHITE}${cs.id}${ChatColor.GRAY} : ${ChatColor.RESET}${cs.name}" +
                    " ${ChatColor.DARK_GRAY}(${cs.steps.size} steps)"
            )
        }
        return true
    }

    private fun handleInfo(sender: CommandSender, rest: List<String>): Boolean {
        if (!sender.hasPermission("cutthin.info")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }
        val id = rest.getOrNull(0) ?: run {
            sender.sendMessage("${ChatColor.RED}사용법: /cutscene info <id>")
            return true
        }
        val cs = registryProvider().find(id) ?: run {
            sender.sendMessage("${ChatColor.RED}존재하지 않는 컷신: $id")
            return true
        }
        sender.sendMessage("${ChatColor.AQUA}=== ${cs.name} ===")
        sender.sendMessage("${ChatColor.GRAY}id: ${ChatColor.WHITE}${cs.id}")
        sender.sendMessage("${ChatColor.GRAY}freeze: ${ChatColor.WHITE}${cs.freeze}")
        sender.sendMessage("${ChatColor.GRAY}steps: ${ChatColor.WHITE}${cs.steps.size}")
        sender.sendMessage("${ChatColor.GRAY}wait total: ${ChatColor.WHITE}${cs.totalWaitTicks} ticks (${cs.totalWaitTicks / 20.0}s)")
        cs.sourceFile?.let {
            sender.sendMessage("${ChatColor.GRAY}source: ${ChatColor.DARK_GRAY}$it")
        }
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("cutthin.reload")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }
        val count = reload()
        sender.sendMessage("${ChatColor.GREEN}$count 개의 컷신을 다시 불러왔습니다.")
        return true
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage("${ChatColor.AQUA}=== CutThin ===")
        sender.sendMessage("${ChatColor.GRAY}/$label play <id> [player] ${ChatColor.WHITE}- 컷신 재생")
        sender.sendMessage("${ChatColor.GRAY}/$label stop [player] ${ChatColor.WHITE}- 컷신 중단")
        sender.sendMessage("${ChatColor.GRAY}/$label list ${ChatColor.WHITE}- 등록된 컷신 목록")
        sender.sendMessage("${ChatColor.GRAY}/$label info <id> ${ChatColor.WHITE}- 컷신 정보")
        sender.sendMessage("${ChatColor.GRAY}/$label reload ${ChatColor.WHITE}- YAML 재로드")
    }

    private fun complete(prefix: String, vararg options: String): MutableList<String> =
        complete(prefix, options.toList())

    private fun complete(prefix: String, options: List<String>): MutableList<String> =
        options.filter { it.startsWith(prefix, ignoreCase = true) }.toMutableList()

    private fun onlinePlayerNames(): List<String> = Bukkit.getOnlinePlayers().map { it.name }
}
