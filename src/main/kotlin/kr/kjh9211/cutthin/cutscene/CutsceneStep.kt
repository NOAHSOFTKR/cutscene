package kr.kjh9211.cutthin.cutscene

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.potion.PotionEffectType

enum class ChatTarget { PLAYER, ALL }

enum class ParticleAnchor { PLAYER, ABSOLUTE }

sealed class CutsceneStep {

    data class Wait(val ticks: Long) : CutsceneStep()

    data class Chat(
        val message: String,
        val target: ChatTarget = ChatTarget.PLAYER,
    ) : CutsceneStep()

    data class Title(
        val title: String,
        val subtitle: String,
        val fadeIn: Int,
        val stay: Int,
        val fadeOut: Int,
    ) : CutsceneStep()

    data class ActionBar(val text: String) : CutsceneStep()

    data class Effect(
        val effect: PotionEffectType,
        val duration: Int,
        val amplifier: Int,
        val ambient: Boolean = false,
        val particles: Boolean = false,
    ) : CutsceneStep()

    data class ClearEffects(val types: List<PotionEffectType>? = null) : CutsceneStep()

    data class PlaySound(
        val sound: Sound,
        val volume: Float = 1.0f,
        val pitch: Float = 1.0f,
    ) : CutsceneStep()

    data class SpawnParticle(
        val particle: Particle,
        val count: Int,
        val offsetX: Double,
        val offsetY: Double,
        val offsetZ: Double,
        val speed: Double = 0.0,
        val anchor: ParticleAnchor = ParticleAnchor.PLAYER,
        val worldName: String? = null,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val z: Double = 0.0,
    ) : CutsceneStep()

    object ClearInventory : CutsceneStep()

    object HideInventory : CutsceneStep()

    object ShowInventory : CutsceneStep()

    data class Teleport(
        val world: String?,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float = 0f,
        val pitch: Float = 0f,
        val randomRadius: Int? = null,
        val safeY: Boolean = true,
    ) : CutsceneStep()

    data class Command(val command: String) : CutsceneStep()

    data class FireEvent(
        val key: String,
        val placeholders: Map<String, String> = emptyMap(),
    ) : CutsceneStep()

    data class Move(
        val world: String?,
        val x: Double,
        val y: Double,
        val z: Double,
        val durationTicks: Int,
        val easing: Easing = Easing.LINEAR,
        val preserveLook: Boolean = false,
    ) : CutsceneStep()

    data class LookAt(
        val world: String?,
        val x: Double,
        val y: Double,
        val z: Double,
        val durationTicks: Int,
        val easing: Easing = Easing.LINEAR,
    ) : CutsceneStep()

    data class Velocity(
        val x: Double,
        val y: Double,
        val z: Double,
        val add: Boolean = false,
    ) : CutsceneStep()

    object ClearChat : CutsceneStep()
}
