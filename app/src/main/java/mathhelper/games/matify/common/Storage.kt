package mathhelper.games.matify.common

import android.annotation.SuppressLint
import android.content.Context
import mathhelper.games.matify.AuthStatus
import mathhelper.games.matify.GlobalScene
import java.util.*
import kotlin.math.absoluteValue

enum class AuthInfo(val str: String) {
    UUID("uuid"),
    SERVER_TOKEN("serverToken"),
    LOGIN("login"),
    PASSWORD("password"),
    NAME("name"),
    FULL_NAME("fullName"),
    ADDITIONAL("additional"),
    AUTHORIZED("authorized"),
    AUTH_STATUS("authStatus"),
    TIME_COEFF("timeCoeff"),
    AWARD_COEFF("awardCoeff"),
    UNDO_COEFF("undoCoeff"),
}

enum class BaseInfo(val str: String) {
    DEVICE_ID("deviceId")
}

enum class SettingInfo(val str: String) {
    THEME("theme")
}

data class AuthInfoObjectFull(
    val base: AuthInfoObjectBase = AuthInfoObjectBase(),
    val uuid: String? = null,
    val coeffs: AuthInfoCoeffs = AuthInfoCoeffs()
)

data class AuthInfoObjectBase(
    var login: String? = null,
    var password: String? = null,
    val name: String? = null,
    val fullName: String? = null,
    val additional: String? = null,
    val authorized: Boolean? = null,
    val authStatus: AuthStatus? = null,
    val serverToken: String? = null
)

data class AuthInfoCoeffs(
    val timeCoeff: Float? = null,
    val awardCoeff: Float? = null,
    val undoCoeff: Int? = null
)

class Storage {
    companion object {
        private const val userInfoFile = "USER_INFO"
        private const val settingFile = "SETTINGS"
        private const val logFile = "LOGS"
        private const val base = "BASE"
        val shared = Storage()
    }

    @SuppressLint("ApplySharedPref")
    fun checkDeviceId(context: Context) {
        val prefs = context.getSharedPreferences(base, Context.MODE_PRIVATE)
        if (!prefs.contains(BaseInfo.DEVICE_ID.str)) {
            val prefEdit = prefs.edit()
            prefEdit.putString(BaseInfo.DEVICE_ID.str, UUID.randomUUID().toString())
            prefEdit.commit()
        }
    }

    fun isUserAuthorized(context: Context): Boolean {
        return context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
            .getBoolean(AuthInfo.AUTHORIZED.str, false)
    }

    @SuppressLint("ApplySharedPref")
    fun invalidateUser(context: Context) {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        prefEdit.putBoolean(AuthInfo.AUTHORIZED.str, false)
        prefEdit.commit()
    }

    fun authStatus(context: Context): AuthStatus {
        return AuthStatus.value(
            context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
                .getString(AuthInfo.AUTH_STATUS.str, AuthStatus.GUEST.str)!!
        )!!
    }

    fun deviceId(context: Context): String {
        return context.getSharedPreferences(base, Context.MODE_PRIVATE)
            .getString(BaseInfo.DEVICE_ID.str, "")!!
    }

    fun login(context: Context): String {
        return context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
            .getString(AuthInfo.LOGIN.str, "")!!
    }

    fun password(context: Context): String {
        return context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
            .getString(AuthInfo.PASSWORD.str, "")!!
    }

    fun serverToken(context: Context): String {
        return context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
            .getString(AuthInfo.SERVER_TOKEN.str, "")!!
    }

    fun theme(context: Context): ThemeName {
        val theme = context.getSharedPreferences(settingFile, Context.MODE_PRIVATE)
            .getString(SettingInfo.THEME.str, "")
        return when (theme) {
            "DARK" -> ThemeName.DARK
            "LIGHT" -> ThemeName.LIGHT
            else -> ThemeName.DARK
        }
    }

    fun themeInt(context: Context): Int {
        return theme(context).resId
    }

    @SuppressLint("ApplySharedPref")
    fun initWithUuid(context: Context): UUID {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        val uuid = UUID.randomUUID()
        prefEdit.putString(AuthInfo.UUID.str, uuid.toString())
        prefEdit.putBoolean(AuthInfo.AUTHORIZED.str, true)
        prefEdit.commit()
        return uuid
    }

    @SuppressLint("ApplySharedPref")
    fun initUserInfo(context: Context, info: AuthInfoObjectBase) {
        val uuid = initWithUuid(context)
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        if (info.login.isNullOrBlank()) {
            info.login = when (info.authStatus) {
                AuthStatus.GUEST -> "guest-" + uuid.hashCode().absoluteValue
                AuthStatus.MATH_HELPER -> "user-" + uuid.hashCode().absoluteValue
                else -> info.login
            }
        }
        if (info.password.isNullOrBlank()) {
            info.password = info.login
        }
        prefEdit.commit()
        setUserInfo(context, info)
        GlobalScene.shared.generateGamesMultCoeffs()
    }

    @SuppressLint("ApplySharedPref")
    fun setServerToken(context: Context, serverToken: String) {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        prefEdit.putString(AuthInfo.SERVER_TOKEN.str, serverToken)
        prefEdit.commit()
    }

    @SuppressLint("ApplySharedPref")
    fun setUserInfo(context: Context, info: AuthInfoObjectBase) {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        if (info.authStatus != null) {
            prefEdit.putString(AuthInfo.AUTH_STATUS.str, info.authStatus.str)
            GlobalScene.shared.authStatus = info.authStatus
        }
        if (info.login != null) {
            prefEdit.putString(AuthInfo.LOGIN.str, info.login)
        }
        if (info.password != null) {
            prefEdit.putString(AuthInfo.PASSWORD.str, info.password)
        }
        if (info.name != null) {
            prefEdit.putString(AuthInfo.NAME.str, info.name)
        }
        if (info.fullName != null) {
            prefEdit.putString(AuthInfo.FULL_NAME.str, info.fullName)
        }
        if (info.additional != null) {
            prefEdit.putString(AuthInfo.ADDITIONAL.str, info.additional)
        }
        if (info.serverToken != null) {
            prefEdit.putString(AuthInfo.SERVER_TOKEN.str, info.serverToken)
        }
        prefEdit.commit()
    }

    fun setTheme(context: Context, theme: ThemeName?) {
        val prefs = context.getSharedPreferences(settingFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        prefEdit.putString(SettingInfo.THEME.str, theme.toString())
        prefEdit.apply()
    }

    fun getUserInfoBase(context: Context): AuthInfoObjectBase {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        return AuthInfoObjectBase(
            login = prefs.getString(AuthInfo.LOGIN.str, null),
            password = prefs.getString(AuthInfo.PASSWORD.str, null),
            name = prefs.getString(AuthInfo.NAME.str, null),
            fullName = prefs.getString(AuthInfo.FULL_NAME.str, null),
            additional = prefs.getString(AuthInfo.ADDITIONAL.str, null),
            authorized = prefs.getBoolean(AuthInfo.AUTHORIZED.str, false),
            authStatus = AuthStatus.value(prefs.getString(AuthInfo.AUTH_STATUS.str, "")!!),
            serverToken = prefs.getString(AuthInfo.SERVER_TOKEN.str, null)
        )
    }

    fun getFullUserInfo(context: Context): AuthInfoObjectFull {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        return AuthInfoObjectFull(
            base = getUserInfoBase(context),
            coeffs = getUserCoeffs(context),
            uuid = prefs.getString(AuthInfo.UUID.str, "")
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setUserCoeffs(context: Context, coeffs: AuthInfoCoeffs) {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        if (coeffs.awardCoeff != null) {
            prefEdit.putFloat(AuthInfo.AWARD_COEFF.str, coeffs.awardCoeff)
        }
        if (coeffs.undoCoeff != null) {
            prefEdit.putInt(AuthInfo.UNDO_COEFF.str, coeffs.undoCoeff)
        }
        if (coeffs.timeCoeff != null) {
            prefEdit.putFloat(AuthInfo.TIME_COEFF.str, coeffs.timeCoeff)
        }
        prefEdit.commit()
    }

    fun getUserCoeffs(context: Context): AuthInfoCoeffs {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        return AuthInfoCoeffs(
            awardCoeff = prefs.getFloat(AuthInfo.AWARD_COEFF.str, 1f),
            undoCoeff = prefs.getInt(AuthInfo.UNDO_COEFF.str, 1),
            timeCoeff = prefs.getFloat(AuthInfo.TIME_COEFF.str, 1f)
        )
    }

    @SuppressLint("ApplySharedPref")
    fun resetGame(context: Context, gameCode: String) {
        val prefs = context.getSharedPreferences(gameCode, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        prefEdit.clear()
        prefEdit.commit()
    }

    @SuppressLint("ApplySharedPref")
    fun clearUserInfo(context: Context) {
        val prefs = context.getSharedPreferences(userInfoFile, Context.MODE_PRIVATE)
        val prefEdit = prefs.edit()
        prefEdit.clear()
        prefEdit.putBoolean(AuthInfo.AUTHORIZED.str, false)
        prefEdit.commit()
    }
}