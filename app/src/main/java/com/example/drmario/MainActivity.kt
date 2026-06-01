package com.example.drmario

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.drmario.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val game = DrMarioGame()
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var endSoundPlayed = false
    private var bgmStep = 0

    private var isPaused = false
    private var isLevelTransition = false
    private var pendingLevelAdvance = false

    private var touchActive = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var tapSlopPx = 0
    private var swipeThresholdPx = 0
    private var tapTimeoutMs = 0
    private var longPressDirection = 0
    private var longPressTriggered = false

    private val bgmPattern = intArrayOf(
        ToneGenerator.TONE_DTMF_5,
        -1,
        ToneGenerator.TONE_DTMF_7,
        -1,
        ToneGenerator.TONE_DTMF_9,
        -1,
        ToneGenerator.TONE_DTMF_7,
        -1
    )

    private val bgmLoop = object : Runnable {
        override fun run() {
            if (isPaused || isLevelTransition || game.gameOver) return
            val tone = bgmPattern[bgmStep % bgmPattern.size]
            if (tone >= 0) {
                playTone(tone, 65)
            }
            bgmStep++
            val delay = (420L - (game.level - 1) * 18L).coerceAtLeast(180L)
            handler.postDelayed(this, delay)
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!canTickGame()) return
            val result = game.tick()
            handleTickResult(result)
            if (canTickGame()) {
                handler.postDelayed(this, result.dropIntervalMs)
            }
        }
    }

    private val longPressStartRunnable = Runnable {
        if (!touchActive || !canAcceptInput()) return@Runnable
        val direction = directionForLongPress(touchDownX)
        if (direction == 0) return@Runnable
        longPressDirection = direction
        longPressTriggered = true
        performMove(direction)
        handler.postDelayed(longPressRepeatRunnable, 85L)
    }

    private val longPressRepeatRunnable = object : Runnable {
        override fun run() {
            if (!touchActive || !longPressTriggered || !canAcceptInput()) return
            performMove(longPressDirection)
            handler.postDelayed(this, 85L)
        }
    }

    private val levelAdvanceRunnable = Runnable {
        if (game.gameOver) return@Runnable
        if (isPaused) {
            pendingLevelAdvance = true
            return@Runnable
        }
        game.startNextLevel()
        isLevelTransition = false
        pendingLevelAdvance = false
        updateUi()
        rescheduleGameLoop(game.currentDropIntervalMs())
        handler.postDelayed(bgmLoop, 180L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        binding.gameView.setGame(game)

        enableFullscreen()
        setupControlButtons()
        setupTouchControls()

        updateUi()
        rescheduleGameLoop(game.currentDropIntervalMs())
        handler.postDelayed(bgmLoop, 350L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(gameLoop)
        handler.removeCallbacks(bgmLoop)
        handler.removeCallbacks(longPressStartRunnable)
        handler.removeCallbacks(longPressRepeatRunnable)
        handler.removeCallbacks(levelAdvanceRunnable)
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val statusBaseLeft = binding.statusText.paddingLeft
        val statusBaseTop = binding.statusText.paddingTop
        val statusBaseRight = binding.statusText.paddingRight
        val statusBaseBottom = binding.statusText.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.statusText.setPadding(
                statusBaseLeft,
                statusBaseTop + bars.top,
                statusBaseRight,
                statusBaseBottom
            )
            binding.controlRow.setPadding(
                binding.controlRow.paddingLeft,
                4 + bars.top / 3,
                binding.controlRow.paddingRight,
                binding.controlRow.paddingBottom
            )
            insets
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupControlButtons() {
        binding.pauseButton.setOnClickListener {
            if (isPaused) {
                resumeGame()
            } else {
                pauseGame()
            }
        }
        binding.newGameButton.setOnClickListener {
            startNewGame()
        }
    }

    private fun setupTouchControls() {
        val config = ViewConfiguration.get(this)
        tapSlopPx = config.scaledTouchSlop
        swipeThresholdPx = config.scaledTouchSlop * 4
        tapTimeoutMs = ViewConfiguration.getTapTimeout() * 2

        binding.gameView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    longPressTriggered = false
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownTime = event.eventTime
                    handler.removeCallbacks(longPressStartRunnable)
                    handler.removeCallbacks(longPressRepeatRunnable)
                    handler.postDelayed(longPressStartRunnable, 230L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (abs(dx) > tapSlopPx || abs(dy) > tapSlopPx) {
                        handler.removeCallbacks(longPressStartRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val duration = event.eventTime - touchDownTime
                    val wasLongPress = longPressTriggered
                    stopContinuousMove()
                    touchActive = false
                    if (!wasLongPress) {
                        handleTouchGesture(event.x, event.y, duration)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun stopContinuousMove() {
        handler.removeCallbacks(longPressStartRunnable)
        handler.removeCallbacks(longPressRepeatRunnable)
        longPressTriggered = false
        longPressDirection = 0
    }

    private fun directionForLongPress(x: Float): Int {
        val w = binding.gameView.width.toFloat()
        if (w <= 0f) return 0
        return when {
            x < w * 0.45f -> -1
            x > w * 0.55f -> 1
            else -> 0
        }
    }

    private fun performMove(direction: Int) {
        if (!canAcceptInput()) return
        if (direction < 0) {
            game.moveLeft()
        } else if (direction > 0) {
            game.moveRight()
        }
        playTone(ToneGenerator.TONE_PROP_BEEP, 28)
        updateUi()
    }

    private fun handleTouchGesture(endX: Float, endY: Float, durationMs: Long) {
        if (!canAcceptInput()) return

        val dx = endX - touchDownX
        val dy = endY - touchDownY
        val absX = abs(dx)
        val absY = abs(dy)

        if (absX <= tapSlopPx && absY <= tapSlopPx && durationMs <= tapTimeoutMs) {
            game.rotateClockwise()
            playTone(ToneGenerator.TONE_DTMF_9, 40)
            updateUi()
            return
        }

        if (dy > swipeThresholdPx && absY > absX) {
            val result = game.hardDrop()
            handleTickResult(result)
            if (canTickGame()) {
                rescheduleGameLoop(result.dropIntervalMs)
            }
            return
        }

        if (absX > swipeThresholdPx && absX > absY) {
            performMove(if (dx > 0) 1 else -1)
        }
    }

    private fun handleTickResult(result: TickResult) {
        result.lockEvent?.let { lockEvent ->
            val hardDrop = lockEvent.cause == LockCause.HARD_DROP
            binding.gameView.triggerLockEffect(lockEvent.cells, hardDrop)
            if (hardDrop) {
                playTone(ToneGenerator.TONE_PROP_BEEP2, 65)
            } else {
                playTone(ToneGenerator.TONE_PROP_ACK, 32)
            }
        }

        for (event in result.clearEvents) {
            binding.gameView.triggerClearEffect(event.cells, event.chainLevel)
            when {
                event.chainLevel >= 3 -> playTone(ToneGenerator.TONE_SUP_CONFIRM, 140)
                event.chainLevel == 2 -> playTone(ToneGenerator.TONE_PROP_ACK, 130)
                event.clearedViruses > 0 -> playTone(ToneGenerator.TONE_PROP_BEEP2, 120)
                else -> playTone(ToneGenerator.TONE_PROP_BEEP, 90)
            }
        }

        if (result.levelClearedNow) {
            isLevelTransition = true
            handler.removeCallbacks(gameLoop)
            handler.removeCallbacks(bgmLoop)
            binding.gameView.triggerLevelClearEffect(result.stage)
            playTone(ToneGenerator.TONE_SUP_RINGTONE, 240)
            handler.postDelayed({ playTone(ToneGenerator.TONE_SUP_CONFIRM, 220) }, 220L)
            handler.removeCallbacks(levelAdvanceRunnable)
            handler.postDelayed(levelAdvanceRunnable, 1700L)
        }

        if (result.gameOver && !endSoundPlayed) {
            endSoundPlayed = true
            isLevelTransition = false
            handler.removeCallbacks(levelAdvanceRunnable)
            handler.removeCallbacks(bgmLoop)
            playTone(ToneGenerator.TONE_PROP_NACK, 260)
        }

        updateUi()
    }

    private fun pauseGame() {
        if (isPaused) return
        isPaused = true
        stopContinuousMove()
        handler.removeCallbacks(gameLoop)
        handler.removeCallbacks(bgmLoop)
        binding.pauseButton.text = "Resume"
        updateUi()
    }

    private fun resumeGame() {
        if (!isPaused) return
        isPaused = false
        binding.pauseButton.text = "Pause"

        if (pendingLevelAdvance) {
            pendingLevelAdvance = false
            handler.post(levelAdvanceRunnable)
        } else if (canTickGame()) {
            rescheduleGameLoop(game.currentDropIntervalMs())
            handler.postDelayed(bgmLoop, 180L)
        }
        updateUi()
    }

    private fun startNewGame() {
        stopContinuousMove()
        handler.removeCallbacks(gameLoop)
        handler.removeCallbacks(bgmLoop)
        handler.removeCallbacks(levelAdvanceRunnable)

        game.newGame()
        isPaused = false
        isLevelTransition = false
        pendingLevelAdvance = false
        endSoundPlayed = false
        bgmStep = 0
        binding.pauseButton.text = "Pause"
        playTone(ToneGenerator.TONE_PROP_BEEP2, 120)

        updateUi()
        rescheduleGameLoop(game.currentDropIntervalMs())
        handler.postDelayed(bgmLoop, 250L)
    }

    private fun canAcceptInput(): Boolean {
        return !isPaused && !isLevelTransition && !game.gameOver && !game.isLevelCleared
    }

    private fun canTickGame(): Boolean {
        return !isPaused && !isLevelTransition && !game.gameOver && !game.isLevelCleared
    }

    private fun rescheduleGameLoop(delayMs: Long) {
        handler.removeCallbacks(gameLoop)
        if (canTickGame()) {
            handler.postDelayed(gameLoop, delayMs)
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        toneGenerator?.startTone(toneType, durationMs)
    }

    private fun updateUi() {
        val status = when {
            game.gameOver -> "GAME OVER  Score: ${game.score}  Lv: ${game.level}  Stage: ${game.stage}"
            isLevelTransition || game.isLevelCleared -> "LEVEL ${game.stage} CLEAR!  Score: ${game.score}"
            isPaused -> "PAUSED  Score: ${game.score}  Viruses: ${game.virusesRemaining}  Stage: ${game.stage}"
            else -> "Score: ${game.score}  Viruses: ${game.virusesRemaining}  Lv: ${game.level}  Stage: ${game.stage}"
        }
        binding.statusText.text = status
        binding.gameView.invalidate()
    }
}
