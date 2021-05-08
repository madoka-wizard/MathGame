package mathhelper.games.matify

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mathhelper.games.matify.activities.GamesActivity
import mathhelper.games.matify.activities.LevelsActivity
import mathhelper.games.matify.common.AuthInfoCoeffs
import mathhelper.games.matify.common.AuthInfoObjectBase
import mathhelper.games.matify.common.Constants
import mathhelper.games.matify.common.Storage
import mathhelper.games.matify.game.Game
import mathhelper.games.matify.game.RulePack
import mathhelper.games.matify.game.RulePackField
import mathhelper.games.matify.level.UndoPolicy
import mathhelper.games.matify.statistics.Pages
import mathhelper.games.matify.statistics.Request
import mathhelper.games.matify.statistics.RequestData
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

enum class AuthStatus(val str: String) {
    GUEST("guest"),
    GOOGLE("google"),
    MATH_HELPER("math_helper"),
    GITHUB("github");

    companion object {
        fun value(value: String) = values().find { it.str == value }
    }
}

data class RulePackDependencyNode(
    val code: String,
    var visited: Boolean,
    val dependencies: ArrayList<RulePackDependencyNode>,
    val json: JSONObject
) {
    companion object {
        fun create(rulePackJson: JSONObject): RulePackDependencyNode? {
            if (!rulePackJson.has(RulePackField.CODE.str)) {
                return null
            }
            return RulePackDependencyNode(
                code = rulePackJson.getString(RulePackField.CODE.str),
                visited = false,
                dependencies = ArrayList(),
                json = rulePackJson
            )
        }
    }
}

fun topologicalSort(rulePacks: ArrayList<JSONObject>): ArrayList<JSONObject> {
    val graph: ArrayList<RulePackDependencyNode> = ArrayList()
    rulePacks.forEach { rulePackJson -> RulePackDependencyNode.create(rulePackJson)?.let { graph.add(it) } }

    val res: ArrayList<JSONObject> = ArrayList()
    fun recursiveDfs(node: RulePackDependencyNode) {
        if (node.visited) {
            return
        }
        node.visited = true
        for (dependency in node.dependencies) {
            recursiveDfs(dependency)
        }
        res.add(node.json)
    }
    for (node in graph) {
        recursiveDfs(node)
    }
    return res
}

class GlobalScene {
    companion object {
        private const val TAG = "GlobalScene"
        private const val sigmaWidth = 3
        val shared: GlobalScene = GlobalScene()
    }

    var authStatus = AuthStatus.GUEST
    var googleSignInClient: GoogleSignInClient? = null
    var tutorialProcessing = false
    var rulePacks: HashMap<String, RulePack> = HashMap()
    var games: ArrayList<Game> = ArrayList()
    var gamesActivity: GamesActivity? = null
        set(value) {
            field = value
            if (value != null) {
                Request.startWorkCycle()
                tutorialProcessing = false

                rulePacks = HashMap()
                value.assets.list("rule_packs")?.let { rulePacksNames ->
                    val rulePacksJsons: ArrayList<JSONObject> = ArrayList()
                    for (fileName in rulePacksNames) {
                        value.assets?.let { assets ->
                            val json = JSONObject(
                                value.assets.open("rule_packs/$fileName").bufferedReader().use { it.readText() })
                            rulePacksJsons.add(json)
                        }
                    }

                    for (rulePackJson in topologicalSort(rulePacksJsons)) {
                        RulePack.create(rulePackJson, rulePacks, value)?.let {
                            rulePacks[it.code] = it
                        }
                    }
                }

                games = ArrayList()
                value.assets.list("games")?.let {
                    for (fileName in it) {
                        val loadedGame = Game.create(fileName, rulePacks, value)
                        if (loadedGame != null) {
                            games.add(loadedGame)
                        }
                    }
                }
                authStatus = Storage.shared.authStatus(value)
            }
        }
    var currentGame: Game? = null
        set(value) {
            field = value
            if (value != null) {
                Handler().postDelayed({
                    gamesActivity?.startActivity(Intent(gamesActivity, LevelsActivity::class.java))
                }, 100)
                // ActivityOptions.makeSceneTransitionAnimation(gamesActivity).toBundle())
                // TODO: send log about game started
            }
        }
    var loadingElement: ProgressBar? = null

    fun nextGame(): Game? {
        if (currentGame == null) {
            return null
        }
        val nextIndex = games.indexOf(currentGame!!) + 1
        return if (nextIndex != games.size) games[nextIndex] else null
    }

    fun resetAll() {
        if (LevelScene.shared.levelsActivity != null) {
            LevelScene.shared.back()
        }
        games.map { Storage.shared.resetGame(gamesActivity!!, it.code) }
    }

    fun logout() {
        resetAll()
        Storage.shared.clearUserInfo(gamesActivity!!)
        if (authStatus == AuthStatus.GOOGLE) {
            googleSignInClient!!.signOut()
        }
        Request.stopWorkCycle()
        gamesActivity!!.recreate()
    }

    fun generateGamesMultCoeffs() {
        Storage.shared.setUserCoeffs(
            gamesActivity!!, AuthInfoCoeffs(
                undoCoeff = Random().nextInt(UndoPolicy.values().size),
                timeCoeff = getByNormDist(1f, Constants.timeDeviation),
                awardCoeff = getByNormDist(1f, Constants.awardDeviation)
            )
        )
    }

    private fun getByNormDist(mean: Float, sigma: Float): Float {
        var res: Float
        val left = mean - sigmaWidth * sigma
        val right = mean + sigmaWidth * sigma
        do {
            res = Random().nextGaussian().toFloat() * sigma + mean
        } while (res !in left..right)
        return res
    }

    fun signUp(context: Activity, userData: AuthInfoObjectBase) {
        val requestRoot = JSONObject()
        requestRoot.put("login", userData.login)
        requestRoot.put("password", userData.password)
        requestRoot.put("name", userData.name)
        requestRoot.put("fullName", userData.fullName)
        requestRoot.put("additional", userData.additional)
        val req = RequestData(Pages.SIGNUP.value, body = requestRoot.toString())
        shared.request(context, background = {
            val response = Request.signRequest(req)
            Storage.shared.setServerToken(context, response.getString("token"))
        }, foreground = {
            context.finish()
        }, errorground = {
            Storage.shared.invalidateUser(context)
        })
    }

    fun request(
        context: Activity,
        background: () -> (Unit),
        foreground: () -> (Unit),
        errorground: () -> (Unit)
    ) {
        loadingElement?.visibility = View.VISIBLE
        GlobalScope.launch {
            try {
                background()
                context.runOnUiThread {
                    foreground()
                }
            } catch (e: Exception) {
                when (e) {
                    is Request.TimeoutException -> {
                        context.runOnUiThread {
                            Toast.makeText(context, R.string.problems_with_internet_connection, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                    is Request.TokenNotFoundException -> {
                        context.runOnUiThread {
                            Toast.makeText(context, R.string.bad_credentials_error, Toast.LENGTH_LONG).show()
                        }
                    }
                    is Request.UndefinedException -> {
                        context.runOnUiThread {
                            Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                errorground()
            } finally {
                context.runOnUiThread {
                    loadingElement?.visibility = View.INVISIBLE
                }
            }
        }
    }
}