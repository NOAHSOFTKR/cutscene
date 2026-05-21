package kr.kjh9211.cutthin.runner

import kr.kjh9211.cutthin.cutscene.ChatTarget
import kr.kjh9211.cutthin.cutscene.CutsceneSession
import kr.kjh9211.cutthin.cutscene.CutsceneStep
import kr.kjh9211.cutthin.cutscene.InventorySnapshot
import kr.kjh9211.cutthin.cutscene.ParticleAnchor
import kr.kjh9211.cutthin.event.CutsceneFireEvent
import kr.kjh9211.cutthin.placeholder.Placeholders
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import kotlin.random.Random

class StepExecutor(private val plugin: JavaPlugin) {

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
                    ChatTarget.PLAYER -> player.sendMessage(colored)
                    ChatTarget.ALL -> Bukkit.broadcastMessage(colored)
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
        }
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
