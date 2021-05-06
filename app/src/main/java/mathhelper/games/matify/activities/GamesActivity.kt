package mathhelper.games.matify.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import mathhelper.games.matify.GlobalScene
import mathhelper.games.matify.R
import mathhelper.games.matify.TutorialScene
import mathhelper.games.matify.common.*
import java.util.*
import kotlin.collections.ArrayList

class GamesActivity : AppCompatActivity() {
    private val TAG = "GamesActivity"
    private lateinit var gamesViews: ArrayList<TextView>
    private lateinit var gamesList: GridLayout
    private var gameTouched: View? = null
    private lateinit var serverDivider: View
    private lateinit var serverLabel: TextView
    private lateinit var serverList: LinearLayout
    private lateinit var serverScroll: ScrollView
    private var askForTutorial = false

    private fun setLanguage() {
        val locale = Locale("en")
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        setLanguage()
        setTheme(Storage.shared.themeInt(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(Constants.serverId)
            .build()
        GlobalScene.shared.googleSignInClient = GoogleSignIn.getClient(this, gso)
        Storage.shared.checkDeviceId(this)
        if (!Storage.shared.isUserAuthorized(this)) {
            startActivity(Intent(this, AuthActivity::class.java))
            askForTutorial = true
        }
        GlobalScene.shared.gamesActivity = this
        gamesViews = ArrayList()
        gamesList = findViewById(R.id.games_list)
        serverDivider = findViewById(R.id.server_divider)
        serverLabel = findViewById(R.id.server_games_label)
        serverList = findViewById(R.id.server_games_list)
        serverScroll = findViewById(R.id.server_scroll)
        generateList()
        if (Build.VERSION.SDK_INT < 24) {
            val settings = findViewById<TextView>(R.id.settings)
            settings.text = "\uD83D\uDD27"
        }
    }

    override fun onResume() {
        super.onResume()
        serverDivider.visibility = View.GONE
        serverLabel.visibility = View.GONE
        serverList.visibility = View.GONE
        serverScroll.visibility = View.GONE
        if (askForTutorial) {
            askForTutorialDialog()
            askForTutorial = false
        }
    }

    fun settings(v: View?) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun generateList() {
        GlobalScene.shared.games.forEachIndexed { i, game ->
            val gameView = AndroidUtil.createSquareButtonView(this)

            var text = game.getNameByLanguage(resources.configuration.locales[0].language)
            if (text.length > 12) {
                text = text.take(12) + ".."
            }
            gameView.text = text
            val themeName = Storage.shared.theme(this)
            gameView.setTextColor(ThemeController.shared.getColorByTheme(themeName, ColorName.TEXT_COLOR))
            /*
            if (game.lastResult != null) {
                gameView.text = "${game.name}\n${game.lastResult!!}"
            }
            */
            gameView.background = getDrawable(R.drawable.rect_shape)
            gameView.setOnTouchListener { v, event ->
                super.onTouchEvent(event)
                when {
                    event.action == MotionEvent.ACTION_DOWN && gameTouched == null -> {
                        gameTouched = v
                        v.background = getDrawable(R.drawable.rect_shape_clicked)
                    }
                    event.action == MotionEvent.ACTION_UP && gameTouched == v -> {
                        v.background = getDrawable(R.drawable.rect_shape)
                        if (AndroidUtil.touchUpInsideView(v, event)) {
                            GlobalScene.shared.currentGame = game
                        }
                        gameTouched = null
                    }
                    event.action == MotionEvent.ACTION_CANCEL && gameTouched == v -> {
                        v.background = getDrawable(R.drawable.rect_shape)
                        gameTouched = null
                    }
                }
                true
            }
            gamesList.addView(gameView)
            gamesViews.add(gameView)
        }
    }

    private fun askForTutorialDialog() {
        val builder = AlertDialog.Builder(
            this, ThemeController.shared.getAlertDialogByTheme(Storage.shared.theme(this))
        )
        builder
            .setTitle(R.string.welcome)
            .setMessage(R.string.wanna_see_tutorial)
            .setPositiveButton(R.string.yep) { dialog: DialogInterface, id: Int ->
                TutorialScene.shared.start(this)
            }
            .setNegativeButton(R.string.no_i_am_pro) { dialog: DialogInterface, id: Int ->
            }
        val dialog = builder.create()
        AndroidUtil.showDialog(dialog)
    }
}