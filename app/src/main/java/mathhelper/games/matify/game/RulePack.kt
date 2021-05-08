package mathhelper.games.matify.game

import android.content.Context
import android.util.Log
import api.expressionSubstitutionFromStructureStrings
import expressiontree.ExpressionSubstitution
import mathhelper.games.matify.common.Constants.Companion.defaultRulePriority
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

enum class RulePackLinkField(val str: String) {
    NAMESPACE_CODE("namespaceCode"),
    RULE_PACK_CODE("rulePackCode"),
    RULE_PACK_NAME_EN("rulePackNameEn"),
    RULE_PACK_NAME_RU("rulePackNameRu")
}

data class RulePackLink(
    val namespaceCode: String,
    val rulePackCode: String,
    val rulePackNameEn: String?,
    val rulePackNameRu: String?
) {
    companion object {
        private val TAG = "RulePackLink"

        fun create(rulePackLinkJson: JSONObject): RulePackLink? {
            Log.d(TAG, "create")
            val nameEnIsPresent = rulePackLinkJson.has(RulePackLinkField.RULE_PACK_NAME_EN.str)
            val nameRuIsPresent = rulePackLinkJson.has(RulePackLinkField.RULE_PACK_NAME_RU.str)

            if (!rulePackLinkJson.has(RulePackLinkField.NAMESPACE_CODE.str) ||
                !rulePackLinkJson.has(RulePackLinkField.RULE_PACK_CODE.str) ||
                (!nameEnIsPresent && !nameRuIsPresent)
            ) {
                return null
            }
            return RulePackLink(
                namespaceCode = rulePackLinkJson.getString(RulePackLinkField.NAMESPACE_CODE.str),
                rulePackCode = rulePackLinkJson.getString(RulePackLinkField.RULE_PACK_CODE.str),
                rulePackNameEn = if (nameEnIsPresent) rulePackLinkJson.getString(RulePackLinkField.RULE_PACK_NAME_EN.str) else null,
                rulePackNameRu = if (nameRuIsPresent) rulePackLinkJson.getString(RulePackLinkField.RULE_PACK_NAME_RU.str) else null,
            )
        }
    }
}

enum class RuleField(val str: String) {
    RULE_LEFT("left"),
    RULE_RIGHT("right"),
    BASED_ON_TASK_CONTEXT("basedOnTaskContext"),
    MATCH_JUMBLED_AND_NESTED("matchJumbledAndNested"),
    SIMPLE_ADDITIONAL("simpleAdditional"),
    IS_EXTENDING("isExtending"),
    PRIORITY("priority"),
    CODE("code")
}

enum class RulePackField(val str: String) {
    NAMESPACE_CODE("namespaceCode"),
    CODE("code"),
    NAME_EN("nameEn"),
    NAME_RU("nameRu"),
    RULE_PACKS("rulePacks"),
    RULES("rules"),
    OTHER_DATA("otherData"),
    DESCRIPTION_SHORT_EN("descriptionShortEn"),
    DESCRIPTION_SHORT_RU("descriptionShortRu"),
    DESCRIPTION_EN("descriptionEn"),
    DESCRIPTION_RU("descriptionRu")
}

data class RulePack(
    val namespaceCode: String,
    val code: String,
    val nameEn: String?,
    val nameRu: String?,
    val rulePacks: ArrayList<RulePack>,
    val rules: ArrayList<ExpressionSubstitution>,
    val otherData: JSONObject?,
    val descriptionShortEn: String?,
    val descriptionShortRu: String?,
    val descriptionEn: String?,
    val descriptionRu: String?
) {
    companion object {
        private val TAG = "RulePack"

        fun create(rulePackJson: JSONObject, parsedRulePacks: HashMap<String, RulePack>, context: Context): RulePack? {
            Log.d(TAG, "create")
            val nameEnIsPresent = rulePackJson.has(RulePackField.NAME_EN.str)
            val nameRuIsPresent = rulePackJson.has(RulePackField.NAME_RU.str)

            if (!rulePackJson.has(RulePackField.NAMESPACE_CODE.str) ||
                !rulePackJson.has(RulePackField.CODE.str) ||
                !rulePackJson.has(RulePackField.RULE_PACKS.str) ||
                !rulePackJson.has(RulePackField.RULES.str) ||
                (!nameRuIsPresent && !nameEnIsPresent)
            ) {
                return null
            }
            val res = RulePack(
                namespaceCode = rulePackJson.getString(RulePackField.NAMESPACE_CODE.str),
                code = rulePackJson.getString(RulePackField.CODE.str),
                nameEn = if (nameEnIsPresent) rulePackJson.getString(RulePackField.NAME_EN.str) else null,
                nameRu = if (nameRuIsPresent) rulePackJson.getString(RulePackField.NAME_RU.str) else null,
                rules = ArrayList(),
                rulePacks = ArrayList(),
                descriptionEn = null,
                descriptionRu = null,
                descriptionShortEn = null,
                descriptionShortRu = null,
                otherData = null
            )

            val rulesJson = rulePackJson.getJSONArray(RulePackField.RULES.str)
            for (i in 0 until rulesJson.length()) {
                val rule = rulesJson.getJSONObject(i)
                if ((rule.has(RuleField.RULE_LEFT.str) && rule.has(RuleField.RULE_RIGHT.str) || rule.has(RuleField.CODE.str))
                ) {
                    res.rules.add(parseRule(rule))
                }
            }

            val rulePacksJson = rulePackJson.getJSONArray(RulePackField.RULE_PACKS.str)
            for (i in 0 until rulePacksJson.length()) {
                RulePackLink.create(rulePacksJson.getJSONObject(i))?.let { rulePackLink ->
                    parsedRulePacks[rulePackLink.rulePackCode]?.let {
                        res.rulePacks.add(it)
                    }
                }
            }
            return res
        }

        fun parseRule(ruleInfo: JSONObject): ExpressionSubstitution {
            val from = ruleInfo.optString(RuleField.RULE_LEFT.str, "")
            val to = ruleInfo.optString(RuleField.RULE_RIGHT.str, "")
            val basedOnTaskContext = ruleInfo.optBoolean(RuleField.BASED_ON_TASK_CONTEXT.str, false)
            val matchJumbledAndNested = ruleInfo.optBoolean(RuleField.MATCH_JUMBLED_AND_NESTED.str, false)
            val simpleAdditional = ruleInfo.optBoolean(RuleField.SIMPLE_ADDITIONAL.str, false)
            val isExtending = ruleInfo.optBoolean(RuleField.IS_EXTENDING.str, false)
            val priority = ruleInfo.optInt(RuleField.PRIORITY.str, defaultRulePriority)
            val nameEn = ruleInfo.optString(RulePackField.NAME_EN.str, "")
            val nameRu = ruleInfo.optString(RulePackField.NAME_RU.str, "")
            val code = ruleInfo.optString(RulePackField.CODE.str, "")
            return expressionSubstitutionFromStructureStrings(
                from,
                to,
                basedOnTaskContext,
                matchJumbledAndNested,
                simpleAdditional,
                isExtending,
                priority,
                code,
                nameEn,
                nameRu
            )
        }
    }

    fun getAllRules(): List<ExpressionSubstitution> {
        var res: ArrayList<ExpressionSubstitution> = rules
        for (rulePack in rulePacks) {
            res = (res + rulePack.getAllRules()) as ArrayList<ExpressionSubstitution>
        }
        return res
    }
}
