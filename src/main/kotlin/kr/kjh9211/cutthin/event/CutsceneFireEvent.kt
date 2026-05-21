package kr.kjh9211.cutthin.event

import kr.kjh9211.cutthin.cutscene.Cutscene
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired mid-cutscene by a `fire_event` step. Other plugins (e.g. the story plugin)
 * can listen and convert this into their own event keys for trigger dispatch.
 */
class CutsceneFireEvent(
    val player: Player,
    val cutscene: Cutscene,
    val key: String,
    val placeholders: Map<String, String>,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
