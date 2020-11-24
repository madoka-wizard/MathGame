package mathhelper.games.matify.level

import android.content.Context
import android.graphics.Color
import android.os.Build
import mathhelper.games.matify.common.ColorName
import mathhelper.games.matify.common.Constants
import mathhelper.games.matify.common.Storage
import mathhelper.games.matify.common.ThemeController

enum class AwardType(val str: String) {
    PLATINUM("\uD83C\uDF96"),
    GOLD("\uD83E\uDD47"),
    SILVER("\uD83E\uDD48"),
    BRONZE("\uD83E\uDD49"),
    PAUSED("\u23F8"),
    NONE("\uD83D\uDE2D")
}

data class Award(val context: Context, val value: AwardType, val coeff: Double) {
    var color: Int = when (value) {
        AwardType.GOLD -> Color.rgb(255, 215, 0)
        AwardType.SILVER -> Color.rgb(145, 142, 140)
        AwardType.BRONZE -> Color.rgb(174, 104, 66)
        else -> ThemeController.shared.getColorByTheme(Storage.shared.theme(context), ColorName.TEXT_COLOR)
    }

    companion object {
        fun getPaused(context: Context): Award {
            return Award(context, AwardType.PAUSED, -1.0)
        }
    }

    override fun toString(): String {
        return if (Build.VERSION.SDK_INT < 24) {
            when (value) {
                AwardType.GOLD -> "😁"
                AwardType.SILVER -> "\uD83D\uDE0A"
                AwardType.BRONZE -> "\uD83D\uDE10"
                AwardType.PAUSED -> "||"
                else -> value.str
            }
        } else {
            value.str
        }
    }
}