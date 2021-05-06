package mathhelper.games.matify.mathResolver

import expressiontree.ExpressionNode
import java.util.*

class CustomSymbolsHandler {
    companion object {
        private val setCustomSymbols = hashMapOf(
            "0" to "∅",
            "1" to "U"
        )

        private val greekSymbols = hashMapOf(
            "A" to "α",
            "B" to "β",
            "C" to "c",
            "D" to "δ",
            "E" to "ε",
            "F" to "φ",
            "G" to "γ",
            "H" to "η",
            "I" to "ι",
            "J" to "j",
            "K" to "κ",
            "L" to "λ",
            "M" to "μ",
            "N" to "ν",
            "O" to "ω",
//            "P" to "π", //pi used in trigonometry, TODO: think, how to handle it
            "Q" to "q",
            "R" to "ρ",
            "S" to "ς",
            "T" to "τ",
            "U" to "υ",
            "V" to "v",
            "W" to "w",
//            "X" to "ξ", //x used in context rule tasks, TODO: think, how to handle it
            "Y" to "y",
            "Z" to "ζ"
        )

        private val otherCustomSymbols = hashMapOf(
            "cherry" to "\uD83C\uDF52"
        )

        fun getPrettyValue(origin: ExpressionNode, style: VariableStyle, taskType: TaskType): Pair<String, Boolean> {
            if (origin.parent == null) {
                return Pair(origin.value, false)
            }
            return when {
                style == VariableStyle.GREEK && greekSymbols.containsKey(origin.value.uppercase(Locale.getDefault())) ->
                    Pair(greekSymbols[origin.value.uppercase(Locale.getDefault())]!!, true)
                taskType == TaskType.SET && setCustomSymbols.containsKey(origin.value) ->
                    Pair(setCustomSymbols[origin.value]!!, true)
                otherCustomSymbols.containsKey(origin.value) -> Pair(otherCustomSymbols[origin.value]!!, true)
                else -> Pair(origin.value, false)
            }
        }
    }
}