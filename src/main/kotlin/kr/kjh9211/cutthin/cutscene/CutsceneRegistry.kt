package kr.kjh9211.cutthin.cutscene

import java.util.concurrent.ConcurrentHashMap

class CutsceneRegistry {

    private val cutscenes = ConcurrentHashMap<String, Cutscene>()

    fun register(cutscene: Cutscene) {
        cutscenes[cutscene.id] = cutscene
    }

    fun unregister(id: String): Cutscene? = cutscenes.remove(id)

    fun clear() {
        cutscenes.clear()
    }

    fun find(id: String): Cutscene? = cutscenes[id]

    fun ids(): Set<String> = cutscenes.keys.toSet()

    fun all(): List<Cutscene> = cutscenes.values.toList()

    fun size(): Int = cutscenes.size
}
