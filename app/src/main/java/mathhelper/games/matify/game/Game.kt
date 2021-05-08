package mathhelper.games.matify.game

import android.content.Context
import android.util.Log
import mathhelper.games.matify.level.Level
import org.json.JSONObject

enum class TaskSetField(val str: String) {
    NAMESPACE_CODE("namespaceCode"),
    CODE("code"),
    NAME_EN("nameEn"),
    NAME_RU("nameRu"),
    SUBJECT_TYPES("subjectTypes"),
    TASKS("tasks"),
    RECOMMENDED_BY_COMMUNITY("recommendedByCommunity"),
    OTHER_DATA("otherData"),
    DESCRIPTION_SHORT_EN("descriptionShortEn"),
    DESCRIPTION_SHORT_RU("descriptionShortRu"),
    DESCRIPTION_EN("descriptionEn"),
    DESCRIPTION_RU("descriptionRu")
}

class Game(var fileName: String, val allRulePacks: HashMap<String, RulePack>) {
    lateinit var tasks: ArrayList<Level>
    lateinit var tasksJsons: ArrayList<JSONObject>
    lateinit var subjectTypes: ArrayList<String>
    lateinit var namespaceCode: String
    lateinit var code: String
    private var nameRu: String? = null
    private var nameEn: String? = null
    var version: Long = 0
    var loaded = false

    fun getNameByLanguage(languageCode: String) = if (languageCode.equals("ru", true)) {
        nameRu
    } else {
        nameEn
    }

    companion object {
        private val TAG = "Game"

        fun create(fileName: String, allRulePacks: HashMap<String, RulePack>, context: Context): Game? {
            Log.d(TAG, "create")
            val res = Game(fileName, allRulePacks)
            if (!res.preload(context)) {
                return null
            }
            return res
        }
    }

    fun preload(context: Context): Boolean {
        Log.d(TAG, "preload")
        return when {
            context.assets != null -> {
                val json = context.assets.open("games/$fileName").bufferedReader().use { it.readText() }
                val gameJson = JSONObject(json)
                preparse(gameJson)
            }
            else -> false
        }
    }

    fun load(context: Context): Boolean {
        Log.d(TAG, "load")
        return loaded || parse(context)
    }

    private fun preparse(gameJson: JSONObject): Boolean {
        val nameEnIsPresent = gameJson.has(TaskSetField.NAME_EN.str)
        val nameRuIsPresent = gameJson.has(TaskSetField.NAME_RU.str)
        if (!gameJson.has(TaskSetField.CODE.str) ||
            !gameJson.has(TaskSetField.NAMESPACE_CODE.str) ||
            !gameJson.has(TaskSetField.SUBJECT_TYPES.str) ||
            !gameJson.has(TaskSetField.TASKS.str) ||
            (!nameEnIsPresent && !nameRuIsPresent)
        ) {
            return false
        }

        namespaceCode = gameJson.getString(TaskSetField.NAMESPACE_CODE.str)
        code = gameJson.getString(TaskSetField.CODE.str)
        nameRu = if (nameRuIsPresent) gameJson.getString(TaskSetField.NAME_RU.str) else null
        nameEn = if (nameEnIsPresent) gameJson.getString(TaskSetField.NAME_EN.str) else null
        tasks = ArrayList()

        tasksJsons = ArrayList()
        val tasksJson = gameJson.getJSONArray(TaskSetField.TASKS.str)
        for (i in 0 until tasksJson.length()) {
            tasksJsons.add(tasksJson.getJSONObject(i))
        }

        subjectTypes = ArrayList()
        val subjectTypesJson = gameJson.getJSONArray(TaskSetField.SUBJECT_TYPES.str)
        for (i in 0 until subjectTypesJson.length()) {
            subjectTypes.add(subjectTypesJson.getString(i))
        }

        return true
    }

    private fun parse(context: Context): Boolean {
        for (json in tasksJsons) {
            val task = Level.parse(this, json, context)
            if (task != null) {
                tasks.add(task)
            }
        }
        loaded = true
        return true
    }
}