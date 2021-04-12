package mathhelper.games.matify.mathResolver.mathResolverNodes

import expressiontree.ExpressionNode
import mathhelper.games.matify.mathResolver.*

class MathResolverNodeFunction(
    origin: ExpressionNode,
    needBrackets: Boolean = false,
    op: Operation,
    length: Int = 0, height: Int = 0
) : MathResolverNodeBase(origin, needBrackets, op, length, height) {
    private val delim = ","

    override fun setNodesFromExpression() {
        super.setNodesFromExpression()
        length += op!!.name.length + 2 + delim.length * (origin.children.size - 1)
        var maxH = 0
        for (node in origin.children) {
            val elem = createNode(node, getNeedBrackets(node), style, taskType)
            elem.setNodesFromExpression()
            children.add(elem)
            if (elem.height > maxH) {
                maxH = elem.height
                if (elem.op != null) {
                    baseLineOffset = elem.baseLineOffset
                }
            }
            length += elem.length
        }
        height = maxH
        if (baseLineOffset < 0) {
            baseLineOffset = height - 1
        }
    }

    override fun setCoordinates(leftTop: Point) {
        super.setCoordinates(leftTop)
        var currLen = leftTop.x + op!!.name.length + 1
        for (child in children) {
            child.setCoordinates(Point(currLen, leftTop.y + baseLineOffset - child.baseLineOffset))
            currLen += child.length + delim.length
        }
    }

    override fun getPlainNode(stringMatrix: ArrayList<String>, spannableArray: ArrayList<SpanInfo>) {
        val curStr = leftTop.y + baseLineOffset
        var curInd = leftTop.x
        children.forEachIndexed { ind: Int, child: MathResolverNodeBase ->
            if (ind == 0) {
                stringMatrix[curStr] = stringMatrix[curStr].replaceByIndex(curInd, op!!.name + "(")
                curInd += op!!.name.length + 1
            }
            child.getPlainNode(stringMatrix, spannableArray)
            curInd += child.length
            if (ind != children.size - 1) {
                stringMatrix[curStr] = stringMatrix[curStr].replaceByIndex(curInd, delim)
                curInd += delim.length
            } else {
                stringMatrix[curStr] = stringMatrix[curStr].replaceByIndex(curInd, ")")
            }
        }
    }
}