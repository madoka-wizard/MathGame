package mathhelper.games.matify.common

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import android.view.MotionEvent
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Toast
import androidx.core.text.getSpans
import api.*
import config.CompiledConfiguration
import expressiontree.ExpressionNode
import expressiontree.ExpressionSubstitution
import mathhelper.games.matify.PlayScene
import mathhelper.games.matify.R
import mathhelper.games.matify.level.Type
import mathhelper.games.matify.mathResolver.MathResolver
import mathhelper.games.matify.mathResolver.MathResolverPair
import mathhelper.games.matify.mathResolver.TaskType

class GlobalMathView: TextView {
    private val TAG = "GlobalMathView"
    var expression: ExpressionNode? = null
        private set
    var currentAtom: ExpressionNode? = null
        private set
    var currentSubatoms: ArrayList<ExpressionNode> = arrayListOf()
        private set
    var currentRulesToResult : Map<ExpressionSubstitution, ExpressionNode>? = null
    var flagInMultiselect = false
    private var mathPair: MathResolverPair? = null
    private var type: Type = Type.OTHER

    /** INITIALIZATION **/
    constructor(context: Context): super(context) {
        Log.d(TAG, "constructor from context")
        setDefaults()
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        Log.d(TAG, "constructor from attrs")
        setDefaults()
    }

    private fun setDefaults() {
        Log.d(TAG, "setDefaults")
        val themeName = Storage.shared.theme(context)
        setTextColor(ThemeController.shared.getColorByTheme(themeName, ColorName.TEXT_COLOR))
        typeface = Typeface.MONOSPACE
        textSize = Constants.centralExpressionDefaultSize
        setLineSpacing(0f, Constants.mathLineSpacing)
        setPadding(
            Constants.defaultPadding, Constants.defaultPadding,
            Constants.defaultPadding, Constants.defaultPadding)
    }

    fun setExpression(expressionStr: String, type: Type) {
        Log.d(TAG, "setExpression from str")
        this.type = type
        if (expressionStr.isNotEmpty()) {
            expression = structureStringToExpression(expressionStr)
            textSize = Constants.centralExpressionDefaultSize
            currentAtom = null
            setTextFromExpression()
        }
    }

    fun setExpression(expressionNode: ExpressionNode, type: Type, resetSize: Boolean = true) {
        Log.d(TAG, "setExpression from node")
        this.type = type
        expression = expressionNode
        if (resetSize) {
            textSize = Constants.centralExpressionDefaultSize
        }
        currentAtom = null
        setTextFromExpression()
    }

    /** Scene interaction **/
    fun performSubstitution(subst: ExpressionSubstitution): ExpressionNode? {
        Log.d(TAG, "performSubstitution")
        var res: ExpressionNode? = null
        if (expression == null || currentAtom == null) {
            Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
        } else {
            val substitutionPlaces = findSubstitutionPlacesInExpression(expression!!, subst)
            if (substitutionPlaces.isNotEmpty()) {
                val substPlace = substitutionPlaces.find {
                    it.originalValue.nodeId == currentAtom!!.nodeId
                }
                if (substPlace != null) {
                    expression = substPlace.originalExpression
                    applySubstitution(expression!!, subst, listOf(substPlace))
                    setTextFromExpression()
                    res = expression!!.clone()
                    currentAtom = null
                }
            }
        }
        return res
    }

    fun performSubstitutionForMultiselect(subst: ExpressionSubstitution): ExpressionNode? {
        Log.d(TAG, "performSubstitution")
        var res: ExpressionNode? = null
        if (expression == null || currentRulesToResult == null) {
            Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
        } else {
            res = currentRulesToResult!![subst]
            expression = res!!.clone()
            setTextFromExpression()
            currentAtom = null
            currentRulesToResult = null
            currentSubatoms = arrayListOf()
        }
        return res
    }

    fun recolorCurrentAtom(color: Int) {
        val newText = SpannableString(text)
        val colorSpans = newText.getSpans<ForegroundColorSpan>(0, text.length)
        for (cs in colorSpans) {
            val start = newText.getSpanStart(cs)
            val end = newText.getSpanEnd(cs)
            newText.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
        text = newText
    }

    fun clearExpression() {
        currentAtom = null
        val newText = SpannableString(text)
        val colorSpans = newText.getSpans<ForegroundColorSpan>(0, text.length)
        for (cs in colorSpans) {
            newText.removeSpan(cs)
        }
        val boldSpans = newText.getSpans<StyleSpan>(0, text.length)
        for (bs in boldSpans) {
            newText.removeSpan(bs)
        }
        text = newText
    }

    /** TextView OVERRIDES **/
    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onTouchEvent")
        super.onTouchEvent(event)
        if (event.pointerCount == 2) {
            return false
        }
        if (expression != null && AndroidUtil.touchUpInsideView(this, event)) {
            if (currentAtom == null) {
                currentSubatoms = arrayListOf()
                selectCurrentAtom(event)
            }
            else
                selectCurrentSubatom(event)
        }
        return true
    }

    /** View OVERRIDES **/

    /** UTILS **/
    private fun selectCurrentAtom(event: MotionEvent) {
        Log.d(TAG, "selectCurrentAtom")
        val x = event.x - textSize / 4
        val y = event.y - textSize / 4
        if (layout != null) {
            val offset = getOffsetForPosition(x, y)

            val themeName = Storage.shared.theme(context)
            val atomColor = ThemeController.shared.getColorByTheme(themeName, ColorName.TEXT_HIGHLIGHT_COLOR)

            val atom = mathPair!!.getColoredAtom(offset, atomColor)
            if (atom != null) {
                if (currentAtom == null || currentAtom!!.nodeId != atom.nodeId) {
                    currentAtom = atom
                    text = mathPair!!.matrix
                    flagInMultiselect = false
                    PlayScene.shared.onExpressionClicked()
                }
            }
        }
    }

    private fun selectCurrentSubatom(event: MotionEvent) {
        Log.d(TAG, "selectCurrentSubatom")
        val x = event.x - textSize / 4
        val y = event.y - textSize / 4
        if (layout != null) {
            val offset = getOffsetForPosition(x, y)

            val themeName = Storage.shared.theme(context)
            val atomColor = ThemeController.shared.getColorByTheme(themeName, ColorName.TEXT_HIGHLIGHT_COLOR)

            val atom = mathPair!!.getColoredSubatom(offset, currentAtom!!, Color.RED)
            if (atom != null) {
                if (currentSubatoms.isEmpty() || !currentSubatoms.any{it.nodeId == atom.nodeId})
                    currentSubatoms.add(atom)
                    text = mathPair!!.matrix
                    flagInMultiselect = true
                    PlayScene.shared.onAtomClicked()
            }
        }
    }

    private fun setTextFromExpression() {
        Log.d(TAG, "setTextFromExpression")
        mathPair = when(type) {
            Type.SET -> MathResolver.resolveToPlain(expression!!, taskType = TaskType.SET)
            else -> MathResolver.resolveToPlain(expression!!)
        }
        text = mathPair!!.matrix
    }
}