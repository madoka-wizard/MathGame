package mathhelper.games.matify.level

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import api.*
import config.CompiledConfiguration
import expressiontree.ExpressionNode
import expressiontree.ExpressionStructureConditionNode
import expressiontree.ExpressionSubstitution
import expressiontree.SubstitutionApplication
import mathhelper.games.matify.common.Storage
import mathhelper.games.matify.game.Game
import mathhelper.games.matify.game.RuleField
import mathhelper.games.matify.game.RulePack
import mathhelper.games.matify.game.RulePackLink
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

enum class Type(val str: String) {
    SET("setTheory"),
    ALGEBRA("algebra"),
    TRIGONOMETRY("trigonometry"),
    OTHER("other")
}

enum class LevelField(val str: String) {
    IGNORE("ignore"),
    LEVEL_CODE("levelCode"),
    NAME("name"),
    NAME_RU("ru"),
    NAME_EN("en"),
    DIFFICULTY("difficulty"),
    TYPE("type"),
    STEPS_NUM("stepsNum"),
    TIME("time"),
    ENDLESS("endless"),
    AWARD_COEFFS("awardCoeffs"),
    SHOW_WRONG_RULES("showWrongRules"),
    SHOW_SUBST_RESULT("showSubstResult"),
    UNDO_CONSIDERING_POLICY("undoConsideringPolicy"),
    LONG_EXPRESSION_CROPPING_POLICY("longExpressionCroppingPolicy"),
    ORIGINAL_EXPRESSION("originalExpression"),
    FINAL_EXPRESSION("finalExpression"),
    FINAL_PATTERN("finalPattern"),
    RULES("rules"),
    RULE_PACKS("rulePacks"),
    MAX_CALC_COMPLEXITY("simpleComputationRuleParamsMaxCalcComplexity"),
    MAX_TEN_POW_ITERATIONS("simpleComputationRuleParamsMaxTenPowIterations"),
    MAX_PLUS_ARG_ROUNDED("simpleComputationRuleParamsMaxPlusArgRounded"),
    MAX_MUL_ARG_ROUNDED("simpleComputationRuleParamsMaxMulArgRounded"),
    MAX_DIV_BASE_ROUNDED("simpleComputationRuleParamsMaxDivBaseRounded"),
    MAX_POW_BASE_ROUNDED("simpleComputationRuleParamsMaxPowBaseRounded"),
    MAX_POW_DEG_ROUNDED("simpleComputationRuleParamsMaxPowDegRounded"),
    MAX_LOG_BASE_ROUNDED("simpleComputationRuleParamsMaxLogBaseRounded"),
    MAX_RES_ROUNDED("simpleComputationRuleParamsMaxResRounded")
}

class Level {
    private var rules = ArrayList<ExpressionSubstitution>()
    lateinit var startExpressionStr: String
    lateinit var startExpression: ExpressionNode
    lateinit var endExpression: ExpressionNode
    lateinit var endExpressionStr: String
    lateinit var endPattern: ExpressionStructureConditionNode
    lateinit var endPatternStr: String
    lateinit var type: Type
    lateinit var code: String
    lateinit var game: Game
    lateinit var name: String
    lateinit var nameRu: String
    lateinit var nameEn: String
    lateinit var maxCalcComplexityStr: String
    lateinit var maxTenPowIterations: String
    lateinit var maxPlusArgRounded: String
    lateinit var maxMulArgRounded: String
    lateinit var maxDivBaseRounded: String
    lateinit var maxPowBaseRounded: String
    lateinit var maxPowDegRounded: String
    lateinit var maxLogBaseRounded: String
    lateinit var maxResRounded: String
    var additionalParamsMap = mutableMapOf<String, String>()
    var awardCoeffs = "0.95 0.9 0.8"
    var awardMultCoeff = 1f
    var showWrongRules = false
    var showSubstResult = false
    var undoPolicy = UndoPolicy.NONE
    var longExpressionCroppingPolicy = ""
    var lastResult: Result? = null
    var difficulty: Double = 0.0
    var stepsNum = 1
    var time: Long = 180
    var timeMultCoeff = 1f
    var endless = true
    private var compiledConfiguration: CompiledConfiguration? = null

    fun getNameByLanguage(languageCode: String) = if (languageCode.equals("ru", true)) {
        nameRu
    } else {
        nameEn
    }

    companion object {
        private val TAG = "Level"

        fun parse(
            game: Game,
            levelJson: JSONObject,
            context: Context
        ): Level? {
            Log.d(TAG, "parse")
            var res: Level? = Level()
            res!!.game = game
            if (res.load(levelJson)) {
                res.setGlobalCoeffs(context)
                res.loadResult(context)
            } else {
                res = null
            }
            return res
        }
    }

    fun load(levelJson: JSONObject): Boolean {
        if (!levelJson.has(LevelField.LEVEL_CODE.str) || !levelJson.has(LevelField.NAME.str) ||
            !levelJson.has(LevelField.DIFFICULTY.str) || !levelJson.has(LevelField.TYPE.str) ||
            !levelJson.has(LevelField.ORIGINAL_EXPRESSION.str) || !levelJson.has(LevelField.FINAL_EXPRESSION.str)
        ) {
            return false
        }
        if (levelJson.optBoolean(LevelField.IGNORE.str, false)) {
            return false
        }
        code = levelJson.getString(LevelField.LEVEL_CODE.str)
        name = levelJson.getString(LevelField.NAME.str)
        nameRu = levelJson.optString(LevelField.NAME_RU.str, name)
        nameEn = levelJson.optString(LevelField.NAME_EN.str, name)
        difficulty = levelJson.getDouble(LevelField.DIFFICULTY.str)
        val typeStr = levelJson.getString(LevelField.TYPE.str)
        try {
            type = Type.valueOf(typeStr.uppercase(Locale.getDefault()))
        } catch (e: Exception) {
            return false
        }
        stepsNum = levelJson.optInt(LevelField.STEPS_NUM.str, stepsNum)
        time = levelJson.optLong(LevelField.TIME.str, time)
        endless = levelJson.optBoolean(LevelField.ENDLESS.str, endless)
        awardCoeffs = levelJson.optString(LevelField.AWARD_COEFFS.str, awardCoeffs)
        showWrongRules = levelJson.optBoolean(LevelField.SHOW_WRONG_RULES.str, showWrongRules)
        showSubstResult = levelJson.optBoolean(LevelField.SHOW_SUBST_RESULT.str, showSubstResult)
        val undoPolicyStr = levelJson.optString(LevelField.UNDO_CONSIDERING_POLICY.str, UndoPolicy.NONE.str)
        try {
            undoPolicy = UndoPolicy.valueOf(undoPolicyStr.uppercase(Locale.getDefault()))
        } catch (e: Exception) {
            return false
        }
        longExpressionCroppingPolicy = levelJson.optString(
            LevelField.LONG_EXPRESSION_CROPPING_POLICY.str,
            longExpressionCroppingPolicy
        )
        /** EXPRESSIONS */
        startExpressionStr = levelJson.getString(LevelField.ORIGINAL_EXPRESSION.str)
        startExpression = structureStringToExpression(startExpressionStr)
        endExpressionStr = levelJson.getString(LevelField.FINAL_EXPRESSION.str)
        endExpression = structureStringToExpression(endExpressionStr)
        Log.d(
            "task",
            "'${startExpression} -> ${endExpression}' parsed from '${startExpressionStr} -> ${endExpressionStr}'"
        )
        endPatternStr = levelJson.optString(LevelField.FINAL_PATTERN.str, "")
        endPattern = when (type) {
            Type.SET -> stringToExpressionStructurePattern(endPatternStr, type.str)
            else -> stringToExpressionStructurePattern(endPatternStr)
        }
        val rulesJson = levelJson.getJSONArray(LevelField.RULES.str)
        for (i in 0 until rulesJson.length()) {
            val rule = rulesJson.getJSONObject(i)
            if ((rule.has(RuleField.RULE_LEFT.str) && rule.has(RuleField.RULE_RIGHT.str)) || rule.has(RuleField.CODE.str)) {
                rules.add(RulePack.parseRule(rule))
            }
        }
        var allRules: ArrayList<ExpressionSubstitution> = rules

        val rulePacksJson = levelJson.getJSONArray(LevelField.RULE_PACKS.str)
        for (i in 0 until rulePacksJson.length()) {
            RulePackLink.create(rulePacksJson.getJSONObject(i))?.let { rulePackLink ->
                game.allRulePacks[rulePackLink.rulePackCode]?.getAllRules().let { rulesFromRulePack ->
                    if (rulesFromRulePack != null) {
                        allRules = (allRules + rulesFromRulePack) as ArrayList<ExpressionSubstitution>
                    }
                }
            }
        }

        maxCalcComplexityStr = levelJson.optString(LevelField.MAX_CALC_COMPLEXITY.str, "")
        if (maxCalcComplexityStr.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxCalcComplexity"] = maxCalcComplexityStr
        }
        maxTenPowIterations = levelJson.optString(LevelField.MAX_TEN_POW_ITERATIONS.str, "")
        if (maxTenPowIterations.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxTenPowIterations"] = maxTenPowIterations
        }
        maxPlusArgRounded = levelJson.optString(LevelField.MAX_PLUS_ARG_ROUNDED.str, "")
        if (maxPlusArgRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxPlusArgRounded"] = maxPlusArgRounded
        }
        maxMulArgRounded = levelJson.optString(LevelField.MAX_MUL_ARG_ROUNDED.str, "")
        if (maxMulArgRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxMulArgRounded"] = maxMulArgRounded
        }
        maxDivBaseRounded = levelJson.optString(LevelField.MAX_DIV_BASE_ROUNDED.str, "")
        if (maxDivBaseRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxDivBaseRounded"] = maxDivBaseRounded
        }
        maxPowBaseRounded = levelJson.optString(LevelField.MAX_POW_BASE_ROUNDED.str, "")
        if (maxPowBaseRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxPowBaseRounded"] = maxPowBaseRounded
        }
        maxPowDegRounded = levelJson.optString(LevelField.MAX_POW_DEG_ROUNDED.str, "")
        if (maxPowDegRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxPowDegRounded"] = maxPowDegRounded
        }
        maxLogBaseRounded = levelJson.optString(LevelField.MAX_LOG_BASE_ROUNDED.str, "")
        if (maxLogBaseRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxPowResRounded"] = maxLogBaseRounded
        }
        maxResRounded = levelJson.optString(LevelField.MAX_RES_ROUNDED.str, "")
        if (maxResRounded.isNotEmpty()) {
            additionalParamsMap["simpleComputationRuleParamsMaxResRounded"] = maxResRounded
        }

        compiledConfiguration = createCompiledConfigurationFromExpressionSubstitutionsAndParams(
            allRules.toTypedArray(),
            additionalParamsMap
        )
        return true
    }

    private fun setGlobalCoeffs(context: Context) {
        if (Storage.shared.isUserAuthorized(context)) {
            val coeffs = Storage.shared.getUserCoeffs(context)
            undoPolicy = UndoPolicy.values()[coeffs.undoCoeff ?: 0]
            timeMultCoeff = coeffs.timeCoeff ?: timeMultCoeff
            time = (time * timeMultCoeff).toLong()
            awardMultCoeff = coeffs.awardCoeff ?: awardMultCoeff
        }
    }

    fun checkEnd(expression: ExpressionNode): Boolean {
        Log.d(TAG, "checkEnd")
        return if (endPatternStr.isBlank()) {
            val currStr = expressionToStructureString(expression)
            Log.d(TAG, "current: $currStr | end: $endExpressionStr")
            currStr == endExpressionStr
        } else {
            val currStr = expressionToStructureString(expression)
            Log.d(TAG, "current: $currStr | pattern: $endPatternStr")
            compareByPattern(expression, endPattern)
        }
    }

    fun getAward(context: Context, resultTime: Long, resultStepsNum: Double): Award {
        Log.d(TAG, "getAward")
        val mark = when {
            resultStepsNum < stepsNum -> 1.0
            else -> (
                1 * (1 - resultTime.toDouble() / (10 * time)) + 3 * stepsNum.toDouble() / resultStepsNum
                ) / (1 + 3)
        }
        return Award(context, getAwardByCoeff(mark), mark)
    }

    private fun getAwardByCoeff(mark: Double): AwardType {
        val awards = awardCoeffs.split(" ").map { it.toDouble() * awardMultCoeff }
        return when {
            mark.equals(1.0) -> AwardType.PLATINUM
            mark >= awards[0] -> AwardType.GOLD
            awards[1] <= mark && mark < awards[0] -> AwardType.SILVER
            awards[2] <= mark && mark < awards[1] -> AwardType.BRONZE
            mark.equals(-1.0) -> AwardType.PAUSED
            else -> AwardType.NONE
        }
    }

    fun getSubstitutionApplication(
        nodes: List<ExpressionNode>,
        expression: ExpressionNode
    ):
        List<SubstitutionApplication>? {
        val nodeIds = nodes.map { it.nodeId }
        Log.d(
            TAG,
            "getSubstitutionApplication of '${expression.toString()}' in '(${nodeIds.joinToString()})'; detail: '${expression.toStringsWithNodeIds()}'"
        )

        val list = findApplicableSubstitutionsInSelectedPlace(
            expression,
            nodeIds.toTypedArray(),
            compiledConfiguration!!,
            withReadyApplicationResult = true
        )
        if (list.isEmpty()) {
            return null
        }

        return list
    }

    fun getRulesFromSubstitutionApplication(substitutionApplication: List<SubstitutionApplication>): List<ExpressionSubstitution> {
        Log.d(TAG, "getRulesFromSubstitutionApplication")

        var rules = substitutionApplication.map {
            Log.d(
                TAG,
                "expressionSubstitution code: '${it.expressionSubstitution.code}'; priority: '${it.expressionSubstitution.priority}'"
            )
            it.expressionSubstitution
        }
        rules = rules.distinctBy { Pair(it.left.identifier, it.right.identifier) }.toMutableList()
        rules.sortByDescending { it.left.toString().length }
        rules.sortBy { it.priority }
        return rules
    }

    fun getResultFromSubstitutionApplication(substitutionApplication: List<SubstitutionApplication>):
        Map<ExpressionSubstitution, ExpressionNode> {
        Log.d(TAG, "getResultFromSubstitutionApplication")
        return substitutionApplication.map { it.expressionSubstitution to it.resultExpression }.toMap()
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(game.code, MODE_PRIVATE)
        val prefEdit = prefs.edit()
        if (lastResult == null) {
            prefEdit.remove(code)
        } else {
            prefEdit.putString(code, lastResult!!.saveString())
        }
        prefEdit.apply()
    }

    private fun loadResult(context: Context) {
        val prefs = context.getSharedPreferences(game.code, MODE_PRIVATE)
        val resultStr = prefs.getString(code, "")
        if (!resultStr.isNullOrEmpty()) {
            val resultVals = resultStr.split(" ", limit = 4)
            lastResult = Result(
                resultVals[0].toDouble(), resultVals[1].toLong(),
                Award(context, getAwardByCoeff(resultVals[2].toDouble()), resultVals[2].toDouble())
            )
            if (resultVals.size == 4) {
                lastResult!!.expression = resultVals[3]
            }
        }
    }
}
