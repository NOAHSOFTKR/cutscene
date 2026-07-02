package kr.kjh9211.cutthin.spike

/*
 * ⚠ Phase 0 스파이크 전용 임시 코드 — 정식 기능 아님.
 * 스펙테이터+엔티티 추적 카메라 방식이 실제로 부드럽게 렌더링되는지,
 * SPECTATOR 전환 없이도 CAMERA 패킷이 먹히는지를 실서버에서 육안으로 검증하기 위한 것.
 * 검증이 끝나면 이 파일 + plugin.yml의 cameraspike 명령 등록 + CutThin.kt의 등록 코드를
 * 전부 삭제하고 git 이력에도 남기지 말 것 (커밋 금지).
 *
 * 사용법 (게임 내, op 권한):
 *   /cameraspike start                    - A안: 게임모드 유지, ArmorStand 리그, CAMERA 패킷만 전송
 *   /cameraspike start spectator          - B안: SPECTATOR로 전환 후 동일 테스트 (A와 체감 비교용)
 *   /cameraspike start display            - D안: ArmorStand 대신 ItemDisplay(teleportDuration=2) 사용
 *   /cameraspike start spectator display  - 조합도 가능
 *   /cameraspike stop                     - 종료 + 원상복구 (게임모드/카메라 바인딩/리그 despawn)
 *
 * 확인할 것 (기획서 Phase 0 A~D 대응):
 *   A) 카메라가 실제로 리그에 바인딩되는지 / stop 시 원래 시점으로 복귀하는지
 *   B) spectator 유무에 따른 체감 부드러움·부작용(노클립, HUD, sneak 리셋) 차이
 *   C) 30초 이상(=600틱) 켜두고 5초(100틱) 근처마다 스터터가 보이는지 (60틱 강제 리싱크 버그 확인)
 *   D) ArmorStand vs ItemDisplay 카메라 피벗(눈높이) 위치·부드러움 차이
 *   E) 리그 켜진 채로 /kill, 로그아웃, 서버 종료 시 고아 엔티티/게임모드 잔류 여부
 */

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CameraSpikeCommand(private val plugin: JavaPlugin) : CommandExecutor {

    private data class Session(
        val rig: Entity,
        val originalGameMode: GameMode,
        val usedSpectator: Boolean,
        val task: BukkitTask,
    )

    private val sessions = mutableMapOf<UUID, Session>()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("player only")
            return true
        }

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            player.sendMessage("§cProtocolLib이 없어서 스파이크를 실행할 수 없습니다.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "start" -> start(
                player,
                useSpectator = args.any { it.equals("spectator", ignoreCase = true) },
                useDisplay = args.any { it.equals("display", ignoreCase = true) },
            )
            "stop" -> stop(player)
            else -> player.sendMessage(
                "§7/cameraspike start [spectator] [display]  |  /cameraspike stop"
            )
        }
        return true
    }

    private fun start(player: Player, useSpectator: Boolean, useDisplay: Boolean) {
        if (sessions.containsKey(player.uniqueId)) {
            player.sendMessage("§c이미 실행 중 — 먼저 /cameraspike stop")
            return
        }

        val center = player.location.clone().add(0.0, 2.0, 0.0)
        val world = center.world ?: return

        val rig: Entity = if (useDisplay) {
            world.spawn(center, ItemDisplay::class.java) { d ->
                d.setItemStack(ItemStack(Material.AIR))
                d.isPersistent = false
                d.setTeleportDuration(2)
                d.interpolationDuration = 2
            }
        } else {
            world.spawn(center, ArmorStand::class.java) { a ->
                a.isVisible = false
                a.isMarker = true
                a.setGravity(false)
                a.isInvulnerable = true
                a.isPersistent = false
                a.setAI(false)
            }
        }

        val originalGameMode = player.gameMode
        if (useSpectator) {
            player.gameMode = GameMode.SPECTATOR
        }

        sendCameraPacket(player, rig)

        var tick = 0
        val radius = 5.0
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tick++
            val angle = Math.toRadians((tick * 4).toDouble())
            val x = center.x + radius * cos(angle)
            val z = center.z + radius * sin(angle)
            val loc = Location(world, x, center.y, z)
            val dx = center.x - x
            val dz = center.z - z
            loc.yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            rig.teleport(loc)
        }, 1L, 1L)

        sessions[player.uniqueId] = Session(rig, originalGameMode, useSpectator, task)
        player.sendMessage(
            "§a스파이크 시작 — spectator=$useSpectator display=$useDisplay. /cameraspike stop 으로 종료"
        )
    }

    private fun stop(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: run {
            player.sendMessage("§c실행 중인 스파이크 없음")
            return
        }
        session.task.cancel()
        sendCameraPacket(player, player)
        if (session.usedSpectator) {
            player.gameMode = session.originalGameMode
        }
        session.rig.remove()
        player.sendMessage("§a스파이크 종료, 원상복구 완료")
    }

    private fun sendCameraPacket(player: Player, target: Entity) {
        val packet = PacketContainer(PacketType.Play.Server.CAMERA)
        packet.integers.write(0, target.entityId)
        runCatching {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false)
        }.onFailure {
            player.sendMessage("§cCAMERA 패킷 전송 실패: ${it.message}")
        }
    }
}
