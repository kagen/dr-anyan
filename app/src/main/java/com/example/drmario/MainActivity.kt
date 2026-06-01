package com.example.drmario

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
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

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var tapSlopPx = 0
    private var swipeThresholdPx = 0
    private var tapTimeoutMs = 0

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
            if (game.gameOver) return
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
            val result = game.tick()
            handleTickResult(result)
            if (!result.gameOver) {
                handler.postDelayed(this, result.dropIntervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        binding.gameView.setGame(game)

        enableFullscreen()
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
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val baseLeft = binding.statusText.paddingLeft
        val baseTop = binding.statusText.paddingTop
        val baseRight = binding.statusText.paddingRight
        val baseBottom = binding.statusText.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.statusText) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(baseLeft, baseTop + bars.top, baseRight, baseBottom)
            insets
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupTouchControls() {
        val config = ViewConfiguration.get(this)
        tapSlopPx = config.scaledTouchSlop
        swipeThresholdPx = config.scaledTouchSlop * 4
        tapTimeoutMs = ViewConfiguration.getTapTimeout() * 2

        binding.gameView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownTime = event.eventTime
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleTouchGesture(event.x, event.y, event.eventTime - touchDownTime)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handleTouchGesture(event.x, event.y, event.eventTime - touchDownTime)
                    true
                }
                else -> true
            }
        }
    }

    private fun handleTouchGesture(endX: Float, endY: Float, durationMs: Long) {
        if (game.gameOver) return

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
            rescheduleGameLoop(result.dropIntervalMs)
            return
        }

        if (absX > swipeThresholdPx && absX > absY) {
            if (dx > 0) {
                game.moveRight()
            } else {
                game.moveLeft()
            }
            playTone(ToneGenerator.TONE_PROP_BEEP, 35)
            updateUi()
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

        if (result.gameOver && !endSoundPlayed) {
            endSoundPlayed = true
            handler.removeCallbacks(bgmLoop)
            if (game.virusesRemaining == 0) {
                playTone(ToneGenerator.TONE_PROP_ACK, 130)
                handler.postDelayed({ playTone(ToneGenerator.TONE_PROP_BEEP2, 160) }, 150L)
            } else {
                playTone(ToneGenerator.TONE_PROP_NACK, 260)
            }
        }

        updateUi()
    }

    private fun rescheduleGameLoop(delayMs: Long) {
        handler.removeCallbacks(gameLoop)
        if (!game.gameOver) {
            handler.postDelayed(gameLoop, delayMs)
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        toneGenerator?.startTone(toneType, durationMs)
    }

    private fun updateUi() {
        val status = if (game.virusesRemaining == 0) {
            "CLEAR! Score: ${game.score}  Lv: ${game.level}"
        } else if (game.gameOver) {
            "GAME OVER  Score: ${game.score}  Lv: ${game.level}"
        } else {
            "Score: ${game.score}  Viruses: ${game.virusesRemaining}  Lv: ${game.level}"
        }
        binding.statusText.text = status
        binding.gameView.invalidate()
    }
}
