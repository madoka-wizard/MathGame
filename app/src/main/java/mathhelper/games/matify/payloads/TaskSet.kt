package mathhelper.games.matify.payloads

import android.os.Build
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class TaskSetField(val str: String) {
    NAMESPACE_CODE("namespaceCode"),
    CODE("code"),
    NAME_EN("nameEn"),
    NAME_RU("nameRu"),
    SUBJECT_TYPES("subjectTypes"),
    TASKS("tasks"),
    AUTHOR_USER_CODE("authorUserCode"),
    RECOMMENDED_BY_COMMUNITY("recommendedByCommunity"),
    START_TIME("startTime"),
    END_TIME("endTime"),
    DESCRIPTION_SHORT_EN("descriptionShortEn"),
    DESCRIPTION_SHORT_RU("descriptionShortRu"),
    DESCRIPTION_EN("descriptionEn"),
    DESCRIPTION_RU("descriptionRu"),
    OTHER_DATA("otherData")
}

data class TaskSet(
    val namespaceCode: String,
    val code: String,
    val nameEn: String?,
    val nameRu: String?,
    val subjectTypes: List<String>,
    val tasks: List<*>,
    val authorUserCode: String?,
    val recommendedByCommunity: Boolean?,
    val startTime: LocalDateTime?,
    val endTime: LocalDateTime?,
    val descriptionShortRu: String?,
    val descriptionShortEn: String?,
    val descriptionRu: String?,
    val descriptionEn: String?,
    val otherData: Map<String, String?>
) {
    fun getName(languageCode: String = "ru"): String =
        if (languageCode.equals("ru", true) || nameEn == null) {
            nameRu!!
        } else {
            nameEn
        }

    companion object {
        @RequiresApi(Build.VERSION_CODES.O)
        fun create(taskSetJson: JSONObject): TaskSet? {
            val nameEnIsPresent = taskSetJson.has(TaskSetField.NAME_EN.str)
            val nameRuIsPresent = taskSetJson.has(TaskSetField.NAME_RU.str)
            if (!taskSetJson.has(TaskSetField.NAMESPACE_CODE.str) ||
                !taskSetJson.has(TaskSetField.CODE.str) ||
                (!nameEnIsPresent && !nameRuIsPresent) ||
                !taskSetJson.has(TaskSetField.TASKS.str)
            ) {
                return null
            }

            val subjectTypes = ArrayList<String>()
            if (taskSetJson.has(TaskSetField.SUBJECT_TYPES.str)) {
                val subjectTypesJson = taskSetJson.getJSONArray(TaskSetField.SUBJECT_TYPES.str)
                for (i in 0 until subjectTypesJson.length()) {
                    subjectTypes.add(subjectTypesJson.getString(i))
                }
            }

            val tasks = ArrayList<Task>()
            val tasksJson = taskSetJson.getJSONArray(TaskSetField.TASKS.str)
            for (i in 0 until tasksJson.length()) {
                Task.create(tasksJson.getJSONObject(i))?.let { task ->
                    tasks.add(task)
                }
            }

            val otherData = HashMap<String, String?>()

            return TaskSet(
                namespaceCode = taskSetJson.getString(TaskSetField.NAMESPACE_CODE.str),
                code = taskSetJson.getString(TaskSetField.CODE.str),
                nameEn = if (nameEnIsPresent) taskSetJson.getString(TaskSetField.NAME_EN.str) else null,
                nameRu = if (nameRuIsPresent) taskSetJson.getString(TaskSetField.NAME_RU.str) else null,
                subjectTypes = subjectTypes,
                tasks = tasks,
                authorUserCode = if (taskSetJson.has(TaskSetField.AUTHOR_USER_CODE.str))
                    taskSetJson.getString(TaskSetField.AUTHOR_USER_CODE.str) else null,
                recommendedByCommunity = if (taskSetJson.has(TaskSetField.RECOMMENDED_BY_COMMUNITY.str))
                    taskSetJson.getBoolean(TaskSetField.RECOMMENDED_BY_COMMUNITY.str) else null,
                startTime = if (taskSetJson.has(TaskSetField.START_TIME.str))
                    LocalDateTime.ofInstant(
                        Instant.parse(taskSetJson.getString(TaskSetField.START_TIME.str)),
                        ZoneId.systemDefault()
                    ) else null,
                endTime = if (taskSetJson.has(TaskSetField.END_TIME.str))
                    LocalDateTime.ofInstant(
                        Instant.parse(taskSetJson.getString(TaskSetField.END_TIME.str)),
                        ZoneId.systemDefault()
                    ) else null,
                descriptionShortRu = if (taskSetJson.has(TaskSetField.DESCRIPTION_SHORT_RU.str))
                    taskSetJson.getString(TaskSetField.DESCRIPTION_SHORT_RU.str) else null,
                descriptionShortEn = if (taskSetJson.has(TaskSetField.DESCRIPTION_SHORT_EN.str))
                    taskSetJson.getString(TaskSetField.DESCRIPTION_SHORT_EN.str) else null,
                descriptionRu = if (taskSetJson.has(TaskSetField.DESCRIPTION_RU.str))
                    taskSetJson.getString(TaskSetField.DESCRIPTION_RU.str) else null,
                descriptionEn = if (taskSetJson.has(TaskSetField.DESCRIPTION_EN.str))
                    taskSetJson.getString(TaskSetField.DESCRIPTION_EN.str) else null,
                otherData = otherData
            )
        }
    }
}
