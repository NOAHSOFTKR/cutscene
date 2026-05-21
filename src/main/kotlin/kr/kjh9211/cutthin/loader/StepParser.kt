package kr.kjh9211.cutthin.loader

import kr.kjh9211.cutthin.cutscene.ChatTarget
import kr.kjh9211.cutthin.cutscene.CutsceneStep
import kr.kjh9211.cutthin.cutscene.Easing
import kr.kjh9211.cutthin.cutscene.ParticleAnchor
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.potion.PotionEffectType

class StepParser {

    fun parse(raw: Any?, defaultFadeIn: Int, defaultStay: Int, defaultFadeOut: Int): CutsceneStep {
        val map = coerceToMap(raw)
            ?: throw IllegalArgumentException("Step must be a map: $raw")

        val type = (map["type"] as? String)?.lowercase()
            ?: throw IllegalArgumentException("Step missing 'type': $map")

        return when (type) {
            "wait" -> CutsceneStep.Wait(
                ticks = (map["ticks"] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("wait step missing 'ticks'"),
            )

            "chat", "message" -> CutsceneStep.Chat(
                message = stringField(map, "message", "text"),
                target = parseChatTarget(map["target"] as? String),
            )

            "title" -> CutsceneStep.Title(
                title = (map["title"] as? String).orEmpty(),
                subtitle = (map["subtitle"] as? String).orEmpty(),
                fadeIn = intField(map, "fadeIn", "fade_in", default = defaultFadeIn),
                stay = intField(map, "stay", default = defaultStay),
                fadeOut = intField(map, "fadeOut", "fade_out", default = defaultFadeOut),
            )

            "actionbar", "action_bar" -> CutsceneStep.ActionBar(
                text = stringField(map, "text", "message"),
            )

            "effect" -> CutsceneStep.Effect(
                effect = parseEffect(stringField(map, "effect", "type_name")),
                duration = intField(map, "duration", default = 200),
                amplifier = intField(map, "amplifier", "level", default = 0),
                ambient = (map["ambient"] as? Boolean) ?: false,
                particles = (map["particles"] as? Boolean) ?: false,
            )

            "clear_effects", "cleareffects" -> CutsceneStep.ClearEffects(
                types = (map["effects"] as? List<*>)?.mapNotNull { name ->
                    (name as? String)?.let { parseEffect(it) }
                },
            )

            "sound" -> CutsceneStep.PlaySound(
                sound = parseSound(stringField(map, "sound")),
                volume = floatField(map, "volume", default = 1.0f),
                pitch = floatField(map, "pitch", default = 1.0f),
            )

            "particle" -> CutsceneStep.SpawnParticle(
                particle = parseParticle(stringField(map, "particle")),
                count = intField(map, "count", default = 20),
                offsetX = doubleField(map, "offsetX", "offset_x", default = 0.5),
                offsetY = doubleField(map, "offsetY", "offset_y", default = 1.0),
                offsetZ = doubleField(map, "offsetZ", "offset_z", default = 0.5),
                speed = doubleField(map, "speed", default = 0.0),
                anchor = parseAnchor(map["at"] as? String),
                worldName = map["world"] as? String,
                x = doubleField(map, "x", default = 0.0),
                y = doubleField(map, "y", default = 0.0),
                z = doubleField(map, "z", default = 0.0),
            )

            "clear_inventory", "clearinventory" -> CutsceneStep.ClearInventory

            "hide_inventory", "hideinventory" -> CutsceneStep.HideInventory

            "show_inventory", "showinventory", "restore_inventory" -> CutsceneStep.ShowInventory

            "teleport" -> CutsceneStep.Teleport(
                world = map["world"] as? String,
                x = doubleField(map, "x", default = 0.0),
                y = doubleField(map, "y", default = 80.0),
                z = doubleField(map, "z", default = 0.0),
                yaw = floatField(map, "yaw", default = 0f),
                pitch = floatField(map, "pitch", default = 0f),
                randomRadius = (map["randomRadius"] as? Number)?.toInt()
                    ?: (map["random_radius"] as? Number)?.toInt(),
                safeY = (map["safeY"] as? Boolean) ?: (map["safe_y"] as? Boolean) ?: true,
            )

            "command", "cmd" -> CutsceneStep.Command(
                command = stringField(map, "command", "cmd"),
            )

            "fire_event", "fireevent", "event" -> CutsceneStep.FireEvent(
                key = stringField(map, "key", "event"),
                placeholders = (map["placeholders"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) ->
                        val ks = k as? String ?: return@mapNotNull null
                        val vs = v?.toString() ?: return@mapNotNull null
                        ks to vs
                    }?.toMap()
                    ?: emptyMap(),
            )

            "move" -> {
                val target = parseTargetCoordinates(map, defaultY = 80.0)
                CutsceneStep.Move(
                    world = target.world,
                    x = target.x,
                    y = target.y,
                    z = target.z,
                    durationTicks = intField(map, "duration_ticks", "duration", "ticks"),
                    easing = Easing.parse(map["easing"] as? String),
                    preserveLook = (map["preserve_look"] as? Boolean) ?: (map["preserveLook"] as? Boolean) ?: false,
                )
            }

            "look_at", "lookat" -> {
                val target = parseTargetCoordinates(map, defaultY = 80.0)
                CutsceneStep.LookAt(
                    world = target.world,
                    x = target.x,
                    y = target.y,
                    z = target.z,
                    durationTicks = intField(map, "duration_ticks", "duration", "ticks"),
                    easing = Easing.parse(map["easing"] as? String),
                )
            }

            "velocity" -> CutsceneStep.Velocity(
                x = doubleField(map, "x", default = 0.0),
                y = doubleField(map, "y", default = 0.0),
                z = doubleField(map, "z", default = 0.0),
                add = (map["add"] as? Boolean) ?: false,
            )

            "clear_chat", "clearchat" -> CutsceneStep.ClearChat

            else -> throw IllegalArgumentException("Unknown step type: '$type'")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerceToMap(raw: Any?): Map<String, Any?>? = when (raw) {
        is Map<*, *> -> raw.entries.associate { (k, v) -> k.toString() to v }
        is ConfigurationSection -> raw.getValues(false).mapKeys { it.key }
        else -> null
    }

    private fun stringField(map: Map<String, Any?>, vararg keys: String): String {
        for (key in keys) {
            (map[key] as? String)?.let { return it }
        }
        throw IllegalArgumentException("Required string field not found among ${keys.toList()} in $map")
    }

    private fun intField(map: Map<String, Any?>, vararg keys: String, default: Int? = null): Int {
        for (key in keys) {
            (map[key] as? Number)?.let { return it.toInt() }
        }
        return default ?: throw IllegalArgumentException("Required int field not found among ${keys.toList()} in $map")
    }

    private fun doubleField(map: Map<String, Any?>, vararg keys: String, default: Double? = null): Double {
        for (key in keys) {
            (map[key] as? Number)?.let { return it.toDouble() }
        }
        return default ?: throw IllegalArgumentException("Required double field not found among ${keys.toList()} in $map")
    }

    private fun floatField(map: Map<String, Any?>, vararg keys: String, default: Float? = null): Float {
        for (key in keys) {
            (map[key] as? Number)?.let { return it.toFloat() }
        }
        return default ?: throw IllegalArgumentException("Required float field not found among ${keys.toList()} in $map")
    }

    private data class TargetCoordinates(val world: String?, val x: Double, val y: Double, val z: Double)

    @Suppress("UNCHECKED_CAST")
    private fun parseTargetCoordinates(map: Map<String, Any?>, defaultY: Double): TargetCoordinates {
        val nested: Map<String, Any?>? = when (val t = map["target"]) {
            is Map<*, *> -> t.entries.associate { (k, v) -> k.toString() to v }
            is org.bukkit.configuration.ConfigurationSection -> t.getValues(false).mapKeys { it.key }
            else -> null
        }
        val source = nested ?: map
        return TargetCoordinates(
            world = source["world"] as? String,
            x = doubleField(source, "x", default = 0.0),
            y = doubleField(source, "y", default = defaultY),
            z = doubleField(source, "z", default = 0.0),
        )
    }

    private fun parseChatTarget(value: String?): ChatTarget =
        when (value?.lowercase()) {
            null, "player", "self" -> ChatTarget.PLAYER
            "all", "everyone", "broadcast" -> ChatTarget.ALL
            else -> throw IllegalArgumentException("Unknown chat target: '$value'")
        }

    private fun parseAnchor(value: String?): ParticleAnchor =
        when (value?.lowercase()) {
            null, "player" -> ParticleAnchor.PLAYER
            "absolute", "world" -> ParticleAnchor.ABSOLUTE
            else -> throw IllegalArgumentException("Unknown particle anchor: '$value'")
        }

    private fun parseEffect(name: String): PotionEffectType {
        val normalized = name.uppercase().replace('-', '_').replace(' ', '_')
        @Suppress("DEPRECATION")
        return PotionEffectType.getByName(normalized)
            ?: throw IllegalArgumentException("Unknown potion effect: '$name'")
    }

    private fun parseSound(name: String): Sound {
        val normalized = name.uppercase().replace('-', '_').replace('.', '_')
        return runCatching { Sound.valueOf(normalized) }
            .getOrElse { throw IllegalArgumentException("Unknown sound: '$name'") }
    }

    private fun parseParticle(name: String): Particle {
        val normalized = name.uppercase().replace('-', '_').replace(' ', '_')
        return runCatching { Particle.valueOf(normalized) }
            .getOrElse { throw IllegalArgumentException("Unknown particle: '$name'") }
    }
}
