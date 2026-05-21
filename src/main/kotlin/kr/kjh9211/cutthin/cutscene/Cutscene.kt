package kr.kjh9211.cutthin.cutscene

data class Cutscene(
    val id: String,
    val name: String,
    val freeze: Boolean,
    val steps: List<CutsceneStep>,
    val sourceFile: String? = null,
) {
    val totalWaitTicks: Long
        get() = steps.filterIsInstance<CutsceneStep.Wait>().sumOf { it.ticks }
}
