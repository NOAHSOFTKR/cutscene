package kr.kjh9211.cutthin.event

import kr.kjh9211.cutthin.cutscene.Cutscene
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class CutsceneEndEvent(
    val player: Player,
    val cutscene: Cutscene,
    val reason: Reason,
) : Event() {

    enum class Reason { COMPLETED, STOPPED, PLAYER_QUIT, PLAYER_DEATH, ERROR }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    companion object {
        @JvmStatic
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }
}
