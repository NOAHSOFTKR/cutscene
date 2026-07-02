package kr.kjh9211.cutthin.runner

import kr.kjh9211.cutthin.cutscene.ChatTarget
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import kr.kjh9211.cutthin.cutscene.CutsceneStep
import kr.kjh9211.cutthin.cutscene.Easing
import kr.kjh9211.cutthin.cutscene.InventorySnapshot
import kr.kjh9211.cutthin.cutscene.ParticleAnchor
import kr.kjh9211.cutthin.event.CutsceneFireEvent
import kr.kjh9211.cutthin.lock.PacketChatBlocker
import kr.kjh9211.cutthin.placeholder.Placeholders
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

class StepExecutor(
    private val plugin: JavaPlugin,
    private val packetChatBlocker: PacketChatBlocker,
    private val cameraRig: CameraRigController,
) {

    private fun sendToPlayer(player: Player, message: String) {
        packetChatBlocker.bypassed(player) { player.sendMessage(message) }
    }

    /**
     * Returns the delay (in ticks) until the next step should execute.
     * For non-Wait steps the result is 0 (immediate continuation).
     */
    fun execute(step: CutsceneStep, session: CutsceneSession): Long {
        val player = Bukkit.getPlayer(session.playerId) ?: return -1L

        return when (step) {
            is CutsceneStep.Wait -> step.ticks

            is CutsceneStep.Chat -> {
                val text = replacePlaceholders(step.message, player, session)
                val colored = colorize(text)
                when (step.target) {
                    ChatTarget.PLAYER -> sendToPlayer(player, colored)
                    ChatTarget.ALL -> {
                        Bukkit.getOnlinePlayers().forEach { recipient ->
                            packetChatBlocker.bypassed(recipient) { recipient.sendMessage(colored) }
                        }
                    }
                }
                0L
            }

            is CutsceneStep.Title -> {
                @Suppress("DEPRECATION")
                player.sendTitle(
                    colorize(replacePlaceholders(step.title, player, session)),
                    colorize(replacePlaceholders(step.subtitle, player, session)),
                    step.fadeIn,
                    step.stay,
                    step.fadeOut,
                )
                0L
            }

            is CutsceneStep.ActionBar -> {
                val component = net.md_5.bungee.api.chat.TextComponent(
                    colorize(replacePlaceholders(step.text, player, session))
                )
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    component,
                )
                0L
            }

            is CutsceneStep.Effect -> {
                player.addPotionEffect(
                    PotionEffect(step.effect, step.duration, step.amplifier, step.ambient, step.particles, true)
                )
                0L
            }

            is CutsceneStep.ClearEffects -> {
                val types = step.types
                if (types.isNullOrEmpty()) {
                    player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
                } else {
                    types.forEach { player.removePotionEffect(it) }
                }
                0L
            }

            is CutsceneStep.PlaySound -> {
                player.playSound(player.location, step.sound, step.volume, step.pitch)
                0L
            }

            is CutsceneStep.SpawnParticle -> {
                val world = when (step.anchor) {
                    ParticleAnchor.PLAYER -> player.world
                    ParticleAnchor.ABSOLUTE -> step.worldName?.let { Bukkit.getWorld(it) } ?: player.world
                }
                val loc = when (step.anchor) {
                    ParticleAnchor.PLAYER -> player.location.add(0.0, 1.0, 0.0)
                    ParticleAnchor.ABSOLUTE -> Location(world, step.x, step.y, step.z)
                }
                world.spawnParticle(
                    step.particle,
                    loc,
                    step.count,
                    step.offsetX,
                    step.offsetY,
                    step.offsetZ,
                    step.speed,
                )
                0L
            }

            CutsceneStep.ClearInventory -> {
                player.inventory.clear()
                player.inventory.setArmorContents(arrayOfNulls(4))
                player.inventory.setItemInOffHand(null)
                0L
            }

            CutsceneStep.HideInventory -> {
                if (session.hiddenInventory == null) {
                    session.hiddenInventory = InventorySnapshot.capture(player)
                }
                player.inventory.clear()
                player.inventory.setArmorContents(arrayOfNulls(4))
                player.inventory.setItemInOffHand(null)
                0L
            }

            CutsceneStep.ShowInventory -> {
                val snapshot = session.hiddenInventory
                if (snapshot != null) {
                    snapshot.restoreTo(player)
                    session.hiddenInventory = null
                } else if (plugin.config.getBoolean("debug")) {
                    plugin.logger.info(
                        "show_inventory step for ${player.name} skipped: no hidden snapshot"
                    )
                }
                0L
            }

            is CutsceneStep.Teleport -> {
                val target = resolveTeleportTarget(step, player)
                if (target != null) {
                    player.teleport(target)
                } else {
                    plugin.logger.warning("Teleport step failed: world '${step.world}' not found for ${player.name}")
                }
                0L
            }

            is CutsceneStep.Command -> {
                val resolved = replacePlaceholders(step.command, player, session)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved)
                0L
            }

            is CutsceneStep.FireEvent -> {
                val resolved = step.placeholders.mapValues { (_, v) ->
                    replacePlaceholders(v, player, session)
                } + mapOf(
                    "player" to player.name,
                    "cutscene_id" to session.cutscene.id,
                )
                Bukkit.getPluginManager().callEvent(
                    CutsceneFireEvent(player, session.cutscene, step.key, resolved)
                )
                0L
            }

            is CutsceneStep.Move -> scheduleMove(step, session, player)

            is CutsceneStep.LookAt -> scheduleLookAt(step, session, player)

            is CutsceneStep.Velocity -> {
                val vec = Vector(step.x, step.y, step.z)
                player.velocity = if (step.add) player.velocity.clone().add(vec) else vec
                0L
            }

            CutsceneStep.ClearChat -> {
                repeat(100) { sendToPlayer(player, " ") }
                0L
            }
        }
    }

    private fun scheduleMove(step: CutsceneStep.Move, session: CutsceneSession, player: Player): Long {
        val world = step.world?.let { Bukkit.getWorld(it) } ?: player.world
        val start = player.location.clone()
        val totalTicks = step.durationTicks.coerceAtLeast(1)

        val endX = step.x
        val endY = step.y
        val endZ = step.z

        val endYaw: Float
        val endPitch: Float
        if (step.preserveLook) {
            endYaw = start.yaw
            endPitch = start.pitch
        } else {
            val direction = Vector(endX - start.x, endY - start.y, endZ - start.z)
            val (yaw, pitch) = directionToYawPitch(direction)
            endYaw = yaw
            endPitch = pitch
        }

        for (tick in 1..totalTicks) {
            val raw = tick.toDouble() / totalTicks
            val t = step.easing.apply(raw)
            val x = start.x + (endX - start.x) * t
            val y = start.y + (endY - start.y) * t
            val z = start.z + (endZ - start.z) * t
            val yaw = interpAngle(start.yaw, endYaw, t.toFloat())
            val pitch = lerp(start.pitch, endPitch, t.toFloat())

            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val live = Bukkit.getPlayer(session.playerId) ?: return@Runnable
                if (!live.isOnline || session.stopped) return@Runnable
                val loc = Location(world, x, y, z, yaw, pitch)
                live.teleport(loc)
                cameraRig.bind(session, live, loc.clone().add(0.0, live.eyeHeight, 0.0))
                if (tick == totalTicks) {
                    cameraRig.release(session, live)
                }
            }, tick.toLong())
            session.addSubTask(task)
        }
        return totalTicks.toLong()
    }

    private fun scheduleLookAt(step: CutsceneStep.LookAt, session: CutsceneSession, player: Player): Long {
        val totalTicks = step.durationTicks.coerceAtLeast(1)
        val start = player.location.clone()
        val targetWorld = step.world?.let { Bukkit.getWorld(it) } ?: player.world

        val targetVec = Vector(step.x, step.y, step.z)
        val eyeOffset = player.eyeHeight
        val direction = Vector(
            targetVec.x - start.x,
            targetVec.y - (start.y + eyeOffset),
            targetVec.z - start.z,
        )
        val (endYaw, endPitch) = directionToYawPitch(direction)

        for (tick in 1..totalTicks) {
            val raw = tick.toDouble() / totalTicks
            val t = step.easing.apply(raw).toFloat()
            val yaw = interpAngle(start.yaw, endYaw, t)
            val pitch = lerp(start.pitch, endPitch, t)

            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val live = Bukkit.getPlayer(session.playerId) ?: return@Runnable
                if (!live.isOnline || session.stopped) return@Runnable
                val loc = live.location.clone()
                val newLoc = Location(targetWorld, loc.x, loc.y, loc.z, yaw, pitch)
                live.teleport(newLoc)
                cameraRig.bind(session, live, newLoc.clone().add(0.0, live.eyeHeight, 0.0))
                if (tick == totalTicks) {
                    cameraRig.release(session, live)
                }
            }, tick.toLong())
            session.addSubTask(task)
        }
        return totalTicks.toLong()
    }

    private fun directionToYawPitch(direction: Vector): Pair<Float, Float> {
        val dx = direction.x
        val dy = direction.y
        val dz = direction.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(atan2(-dy, horizontal)).toFloat()
        return yaw to pitch
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Yaw 보간 — -180~180 경계를 넘어가는 경우 짧은 쪽으로 회전. */
    private fun interpAngle(a: Float, b: Float, t: Float): Float {
        var diff = ((b - a + 540f) % 360f) - 180f
        return a + diff * t
    }

    private fun resolveTeleportTarget(step: CutsceneStep.Teleport, player: Player): Location? {
        val world = step.world?.let { Bukkit.getWorld(it) } ?: player.world
        val baseX = step.x
        val baseZ = step.z

        val (x, z) = if (step.randomRadius != null && step.randomRadius > 0) {
            val r = step.randomRadius.toDouble()
            val rx = Random.nextDouble(-r, r)
            val rz = Random.nextDouble(-r, r)
            baseX + rx to baseZ + rz
        } else {
            baseX to baseZ
        }

        val y = if (step.safeY) {
            world.getHighestBlockYAt(x.toInt(), z.toInt()).toDouble() + 1.0
        } else {
            step.y
        }

        return Location(world, x + 0.5, y, z + 0.5, step.yaw, step.pitch)
    }

    private fun replacePlaceholders(value: String, player: Player, session: CutsceneSession): String {
        val withLocals = value
            .replace("{player}", player.name)
            .replace("{cutscene}", session.cutscene.id)
            .replace("{cutscene_name}", session.cutscene.name)
        return Placeholders.apply(player, withLocals)
    }

    private fun colorize(value: String): String =
        ChatColor.translateAlternateColorCodes('&', value)
}
