package com.example.drmario

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var game: DrMarioGame? = null
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1C2533") }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33425A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A3445")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val virusEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    fun setGame(game: DrMarioGame) {
        this.game = game
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return

        val cellSize = min(width / (g.width + 2f), height / (g.height + 2f))
        val boardWidth = g.width * cellSize
        val boardHeight = g.height * cellSize
        val left = (width - boardWidth) / 2f
        val top = (height - boardHeight) / 2f

        val boardRect = RectF(left, top, left + boardWidth, top + boardHeight)
        canvas.drawRoundRect(boardRect, 16f, 16f, boardPaint)
        canvas.drawRoundRect(boardRect, 16f, 16f, framePaint)

        for (y in 0 until g.height) {
            for (x in 0 until g.width) {
                val l = left + x * cellSize
                val t = top + y * cellSize
                val r = l + cellSize
                val b = t + cellSize
                canvas.drawRect(l, t, r, b, gridPaint)

                val cell = g.snapshotBoard()[y][x] ?: continue
                drawCell(canvas, l, t, cellSize, cell)
            }
        }

        val activeCells = g.activeCells()
        val activeColors = g.activeColors()
        for (i in activeCells.indices) {
            val (x, y) = activeCells[i]
            if (x !in 0 until g.width || y !in 0 until g.height) continue
            val l = left + x * cellSize
            val t = top + y * cellSize
            drawRawPiece(canvas, l, t, cellSize, activeColors[i], false)
        }
    }

    private fun drawCell(canvas: Canvas, left: Float, top: Float, size: Float, cell: Cell) {
        drawRawPiece(canvas, left, top, size, cell.color, cell.kind == CellKind.VIRUS)
    }

    private fun drawRawPiece(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        color: PillColor,
        isVirus: Boolean
    ) {
        val inset = size * 0.08f
        val rect = RectF(left + inset, top + inset, left + size - inset, top + size - inset)
        piecePaint.color = color.drawColor
        canvas.drawRoundRect(rect, size * 0.18f, size * 0.18f, piecePaint)

        if (isVirus) {
            val eyeRadius = size * 0.06f
            val eyeY = top + size * 0.45f
            canvas.drawCircle(left + size * 0.38f, eyeY, eyeRadius, virusEyePaint)
            canvas.drawCircle(left + size * 0.62f, eyeY, eyeRadius, virusEyePaint)
        }
    }
}

