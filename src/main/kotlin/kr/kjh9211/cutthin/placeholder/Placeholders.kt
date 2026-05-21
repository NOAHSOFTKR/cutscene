package kr.kjh9211.cutthin.placeholder

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Thin façade around PlaceholderAPI so callers don't have to guard each invocation.
 * The plugin-existence check happens BEFORE any PlaceholderAPI class symbol is touched,
 * which avoids NoClassDefFoundError when PAPI isn't installed.
 */
object Placeholders {

    fun apply(player: Player?, text: String): String {
        if (text.isEmpty()) return text
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return text
        return resolveViaPapi(player, text)
    }

    private fun resolveViaPapi(player: Player?, text: String): String =
        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text)
}
