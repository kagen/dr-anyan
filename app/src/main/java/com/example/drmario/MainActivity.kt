package com.example.drmario

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.drmario.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val game = DrMarioGame()
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var endSoundPlayed = false
    private var bgmStep = 0

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

        binding.leftButton.setOnClickListener {
            game.moveLeft()
            playTone(ToneGenerator.TONE_PROP_BEEP, 35)
            updateUi()
        }
        binding.rightButton.setOnClickListener {
            game.moveRight()
            playTone(ToneGenerator.TONE_PROP_BEEP, 35)
            updateUi()
        }
        binding.rotateButton.setOnClickListener {
            game.rotateClockwise()
            playTone(ToneGenerator.TONE_DTMF_9, 40)
            updateUi()
        }
        binding.dropButton.setOnClickListener {
            playTone(ToneGenerator.TONE_PROP_BEEP2, 60)
            val result = game.hardDrop()
            handleTickResult(result)
            rescheduleGameLoop(result.dropIntervalMs)
        }

        updateUi()
        rescheduleGameLoop(game.currentDropIntervalMs())
        handler.postDelayed(bgmLoop, 350L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(gameLoop)
        handler.removeCallbacks(bgmLoop)
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun handleTickResult(result: TickResult) {
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
