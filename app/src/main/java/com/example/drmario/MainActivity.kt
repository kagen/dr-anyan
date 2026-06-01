package com.example.drmario

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.drmario.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val game = DrMarioGame()
    private val handler = Handler(Looper.getMainLooper())

    private val gameLoop = object : Runnable {
        override fun run() {
            game.tick()
            updateUi()
            if (!game.gameOver) {
                handler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gameView.setGame(game)

        binding.leftButton.setOnClickListener {
            game.moveLeft()
            updateUi()
        }
        binding.rightButton.setOnClickListener {
            game.moveRight()
            updateUi()
        }
        binding.rotateButton.setOnClickListener {
            game.rotateClockwise()
            updateUi()
        }
        binding.dropButton.setOnClickListener {
            game.hardDrop()
            updateUi()
        }

        updateUi()
        handler.postDelayed(gameLoop, 500L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(gameLoop)
    }

    private fun updateUi() {
        val status = if (game.virusesRemaining == 0) {
            "CLEAR! Score: ${game.score}"
        } else if (game.gameOver) {
            "GAME OVER  Score: ${game.score}"
        } else {
            "Score: ${game.score}  Viruses: ${game.virusesRemaining}"
        }
        binding.statusText.text = status
        binding.gameView.invalidate()
    }
}
