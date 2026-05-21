package kr.kjh9211.cutthin.event

import kr.kjh9211.cutthin.cutscene.Cutscene
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class CutsceneStartEvent(
    val player: Player,
    val cutscene: Cutscene,
) : Event(), Cancellable {

    private var cancelled: Boolean = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(value: Boolean) {
        cancelled = value
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
