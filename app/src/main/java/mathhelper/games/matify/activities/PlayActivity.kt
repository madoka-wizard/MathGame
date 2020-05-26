package mathhelper.games.matify.activities

import android.app.AlertDialog
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BulletSpan
import android.util.Log
import android.view.*
import android.widget.*
import mathhelper.games.matify.level.Award
import mathhelper.games.matify.common.GlobalMathView
import mathhelper.games.matify.LevelScene
import mathhelper.games.matify.PlayScene
import mathhelper.games.matify.R
import mathhelper.games.matify.common.AndroidUtil
import mathhelper.games.matify.common.Constants
import kotlin.math.max
import kotlin.math.min

class PlayActivity: AppCompatActivity() {
    private val TAG = "PlayActivity"
    private var scale = 1.0f
    private var needClear = false
    private var loading = false
    private var scaleListener = MathScaleListener()
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var looseDialog: AlertDialog
    private lateinit var winDialog: AlertDialog
    private lateinit var backDialog: AlertDialog
    private lateinit var continueDialog: AlertDialog
    private lateinit var progress: ProgressBar

    lateinit var globalMathView: GlobalMathView
    lateinit var endExpressionView: TextView
    lateinit var endExpressionViewLabel: TextView
    lateinit var messageView: TextView
    lateinit var rulesLinearLayout: LinearLayout
    lateinit var rulesScrollView: ScrollView
    lateinit var noRules: TextView
    lateinit var timerView: TextView

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onTouchEvent")
        scaleDetector.onTouchEvent(event)
        when {
            event.action == MotionEvent.ACTION_DOWN -> {
                needClear = true
            }
            event.action == MotionEvent.ACTION_UP -> {
                if (needClear) {
                    globalMathView.clearExpression()
                    PlayScene.shared.clearRules()
                }
            }
        }
        return true
    }

    private fun setViews() {
        globalMathView = findViewById(R.id.global_expression)
        endExpressionView = findViewById(R.id.end_expression_view)
        endExpressionViewLabel = findViewById(R.id.end_expression_label)
        messageView = findViewById(R.id.message_view)
        rulesLinearLayout = findViewById(R.id.rules_linear_layout)
        rulesScrollView = findViewById(R.id.rules_scroll_view)
        noRules = findViewById(R.id.no_rules)
        timerView = findViewById(R.id.timer_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        scaleDetector = ScaleGestureDetector(this, scaleListener)
        setViews()
        progress = findViewById(R.id.progress)
        looseDialog = createLooseDialog()
        winDialog = createWinDialog()
        backDialog = createBackDialog()
        continueDialog = createContinueDialog()
        PlayScene.shared.playActivity = this
        startCreatingLevelUI()
    }

    override fun onBackPressed() {
        if (!loading) {
            back(null)
        }
    }

    override fun finish() {
        PlayScene.shared.cancelTimers()
        PlayScene.shared.playActivity = null
        super.finish()
    }

    fun startCreatingLevelUI() {
        if (LevelScene.shared.wasLevelPaused()) {
            AndroidUtil.showDialog(continueDialog)
        } else {
            createLevelUI(false)
        }
    }

    private fun createLevelUI(continueGame: Boolean) {
        loading = true
        timerView.text = ""
        globalMathView.text = ""
        endExpressionView.text = ""
        progress.visibility = View.VISIBLE
        PlayScene.shared.loadLevel(continueGame)
        progress.visibility = View.GONE
        loading = false
    }

    fun previous(v: View?) {
        if (!loading) {
            PlayScene.shared.previousStep()
        }
    }

    fun restart(v: View?) {
        if (!loading) {
            scale = 1f
            PlayScene.shared.restart()
        }
    }

    fun info(v: View?) {
        PlayScene.shared.info()
    }

    fun back(v: View?) {
        if (!loading) {
            if (LevelScene.shared.currentLevel!!.endless && PlayScene.shared.stepsCount > 0) {
                AndroidUtil.showDialog(backDialog)
            } else {
                returnToMenu(false)
            }
        }
    }

    private fun returnToMenu(save: Boolean) {
        PlayScene.shared.menu(save)
        finish()
    }

    fun showEndExpression(v: View?) {
        if (endExpressionView.visibility == View.GONE) {
            endExpressionViewLabel.text = getString(R.string.end_expression_opened)
            endExpressionView.visibility = View.VISIBLE
        } else {
            endExpressionViewLabel.text = getString(R.string.end_expression_closed)
            endExpressionView.visibility = View.GONE
        }
    }

    fun endExpressionHide(): Boolean {
        return endExpressionView.visibility != View.VISIBLE
    }

    fun onWin(stepsCount: Float, currentTime: Long, award: Award) {
        Log.d(TAG, "onWin")
        val msgTitle = "You finished level with:"
        val steps = "\n\tSteps: " + if (stepsCount.equals(stepsCount.toInt().toFloat())) {
            "${stepsCount.toInt()}"
        } else {
            "%.1f".format(stepsCount)
        }
        val sec = "${currentTime % 60}".padStart(2, '0')
        val time = "\n\tTime: ${currentTime / 60}:$sec"
        val spannable = SpannableString("$msgTitle$steps$time\n\nAWARD: $award")
        spannable.setSpan(BulletSpan(5, Constants.primaryColor), msgTitle.length + 1,
            msgTitle.length + steps.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(BulletSpan(5, Constants.primaryColor),
            msgTitle.length + steps.length + 1, msgTitle.length + steps.length + time.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        winDialog.setMessage(spannable)
        AndroidUtil.showDialog(winDialog)
    }

    fun onLoose() {
        AndroidUtil.showDialog(looseDialog)
    }

    private fun createWinDialog(): AlertDialog {
        Log.d(TAG, "createWinDialog")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("Congratulations!")
            .setMessage("")
            .setPositiveButton("Next") { dialog: DialogInterface, id: Int -> }
            .setNeutralButton("Menu") { dialog: DialogInterface, id: Int ->
                PlayScene.shared.menu(false)
                finish()
            }
            .setNegativeButton("Restart") { dialog: DialogInterface, id: Int ->
                scale = 1f
                PlayScene.shared.restart()
            }
            .setCancelable(false)
        val dialog = builder.create()
        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                scale = 1f
                if (!LevelScene.shared.nextLevel()) {
                    Toast.makeText(this, "Sorry, that's last level!", Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    private fun createLooseDialog(): AlertDialog {
        Log.d(TAG, "createLooseDialog")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("Time out!")
            .setMessage("May be next time?")
            .setPositiveButton("Restart") { dialog: DialogInterface, id: Int ->
                restart(null)
            }
            .setNegativeButton("Menu") { dialog: DialogInterface, id: Int ->
                back(null)
            }
            .setCancelable(false)
        return builder.create()
    }

    private fun createBackDialog(): AlertDialog {
        Log.d(TAG, "createBackDialog")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("Attention!")
            .setMessage("Save your current state?")
            .setPositiveButton("Yes") { dialog: DialogInterface, id: Int ->
                returnToMenu(true)
            }
            .setNegativeButton("No") { dialog: DialogInterface, id: Int ->
                returnToMenu(false)
            }
            .setNeutralButton("Cancel") { dialog: DialogInterface, id: Int -> }
            .setCancelable(false)
        return builder.create()
    }

    private fun createContinueDialog(): AlertDialog {
        Log.d(TAG, "createContinueDialog")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("Welcome back!")
            .setMessage("Continue from where you stopped?")
            .setPositiveButton("Yes") { dialog: DialogInterface, id: Int ->
                createLevelUI(true)
            }
            .setNegativeButton("No") { dialog: DialogInterface, id: Int ->
                createLevelUI(false)
            }
            .setCancelable(false)
        return builder.create()
    }

    inner class MathScaleListener: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            needClear = false
            scale *= detector.scaleFactor
            scale = max(
                Constants.ruleDefaultSize / Constants.centralExpressionDefaultSize,
                min(scale, Constants.centralExpressionMaxSize / Constants.centralExpressionDefaultSize))
            globalMathView.textSize = Constants.centralExpressionDefaultSize * scale
            return true
        }
    }
}