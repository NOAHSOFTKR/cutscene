package kr.kjh9211.cutthin.cutscene

import kotlin.math.PI
import kotlin.math.cos

enum class Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    /**
     * Returns the eased progress for `t` in [0, 1].
     * Out-of-range inputs are clamped to the unit interval.
     */
    fun apply(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return when (this) {
            LINEAR -> x
            EASE_IN -> 1.0 - cos((x * PI) / 2.0)
            EASE_OUT -> 1.0 - (1.0 - x) * (1.0 - x)
            EASE_IN_OUT -> -(cos(PI * x) - 1.0) / 2.0
        }
    }

    companion object {
        fun parse(name: String?): Easing =
            when (name?.uppercase()?.replace('-', '_')) {
                null, "LINEAR" -> LINEAR
                "EASE_IN", "EASEIN", "IN" -> EASE_IN
                "EASE_OUT", "EASEOUT", "OUT" -> EASE_OUT
                "EASE_IN_OUT", "EASEINOUT", "IN_OUT", "INOUT" -> EASE_IN_OUT
                else -> throw IllegalArgumentException("Unknown easing: '$name'")
            }
    }
}
