package com.example.drmario

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class FlashCell(
        val x: Int,
        val y: Int,
        var alpha: Float
    )

    private var game: DrMarioGame? = null
    private val flashCells = mutableListOf<FlashCell>()
    private var chainBanner: String? = null
    private var chainBannerAlpha = 0f

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
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val chainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 54f
        isFakeBoldText = true
    }

    fun setGame(game: DrMarioGame) {
        this.game = game
        invalidate()
    }

    fun triggerClearEffect(cells: Set<Pair<Int, Int>>, chainLevel: Int) {
        if (cells.isEmpty()) return
        val initialAlpha = (0.45f + chainLevel * 0.15f).coerceAtMost(1f)
        for ((x, y) in cells) {
            val existing = flashCells.firstOrNull { it.x == x && it.y == y }
            if (existing == null) {
                flashCells += FlashCell(x, y, initialAlpha)
            } else {
                existing.alpha = max(existing.alpha, initialAlpha)
            }
        }
        if (chainLevel >= 2) {
            chainBanner = "${chainLevel} CHAIN!"
            chainBannerAlpha = 1f
        }
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

        val board = g.snapshotBoard()
        for (y in 0 until g.height) {
            for (x in 0 until g.width) {
                val l = left + x * cellSize
                val t = top + y * cellSize
                val r = l + cellSize
                val b = t + cellSize
                canvas.drawRect(l, t, r, b, gridPaint)

                val cell = board[y][x] ?: continue
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

        drawFlashEffects(canvas, left, top, cellSize, g.width, g.height)
        drawChainBanner(canvas, top)

        if (advanceEffects()) {
            postInvalidateOnAnimation()
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

    private fun drawFlashEffects(
        canvas: Canvas,
        boardLeft: Float,
        boardTop: Float,
        cellSize: Float,
        boardWidth: Int,
        boardHeight: Int
    ) {
        for (flash in flashCells) {
            if (flash.x !in 0 until boardWidth || flash.y !in 0 until boardHeight) continue
            val left = boardLeft + flash.x * cellSize
            val top = boardTop + flash.y * cellSize
            val inset = cellSize * 0.06f
            flashPaint.alpha = (flash.alpha * 200).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                RectF(left + inset, top + inset, left + cellSize - inset, top + cellSize - inset),
                cellSize * 0.2f,
                cellSize * 0.2f,
                flashPaint
            )
        }
    }

    private fun drawChainBanner(canvas: Canvas, boardTop: Float) {
        val text = chainBanner ?: return
        if (chainBannerAlpha <= 0f) return
        chainPaint.alpha = (chainBannerAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawText(text, width / 2f, boardTop - 18f, chainPaint)
    }

    private fun advanceEffects(): Boolean {
        var animating = false
        val iterator = flashCells.iterator()
        while (iterator.hasNext()) {
            val flash = iterator.next()
            flash.alpha -= 0.07f
            if (flash.alpha <= 0f) {
                iterator.remove()
            } else {
                animating = true
            }
        }

        if (chainBannerAlpha > 0f) {
            chainBannerAlpha -= 0.045f
            animating = true
            if (chainBannerAlpha <= 0f) {
                chainBannerAlpha = 0f
                chainBanner = null
            }
        }
        return animating
    }
}
