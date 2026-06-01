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
        var alpha: Float,
        var scale: Float
    )

    private data class BurstCell(
        val x: Int,
        val y: Int,
        var progress: Float,
        var alpha: Float
    )

    private data class LockPulse(
        val x: Int,
        val y: Int,
        var progress: Float,
        var alpha: Float
    )

    private var game: DrMarioGame? = null
    private val flashCells = mutableListOf<FlashCell>()
    private val burstCells = mutableListOf<BurstCell>()
    private val lockPulses = mutableListOf<LockPulse>()
    private var chainBanner: String? = null
    private var chainBannerAlpha = 0f
    private var boardFlashAlpha = 0f

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
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val burstLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FFE082")
    }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#80DEEA")
    }
    private val boardFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    fun triggerLockEffect(cells: List<Pair<Int, Int>>, hardDrop: Boolean) {
        if (cells.isEmpty()) return
        val alpha = if (hardDrop) 0.95f else 0.6f
        for ((x, y) in cells) {
            lockPulses += LockPulse(x = x, y = y, progress = 0f, alpha = alpha)
        }
        boardFlashAlpha = max(boardFlashAlpha, if (hardDrop) 0.26f else 0.14f)
        invalidate()
    }

    fun triggerClearEffect(cells: Set<Pair<Int, Int>>, chainLevel: Int) {
        if (cells.isEmpty()) return
        val initialAlpha = (0.55f + chainLevel * 0.15f).coerceAtMost(1f)
        for ((x, y) in cells) {
            val existing = flashCells.firstOrNull { it.x == x && it.y == y }
            if (existing == null) {
                flashCells += FlashCell(x = x, y = y, alpha = initialAlpha, scale = 1f)
            } else {
                existing.alpha = max(existing.alpha, initialAlpha)
                existing.scale = 1f
            }
            burstCells += BurstCell(x = x, y = y, progress = 0f, alpha = 1f)
        }

        boardFlashAlpha = max(boardFlashAlpha, (0.2f + chainLevel * 0.12f).coerceAtMost(0.48f))
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
        drawBurstEffects(canvas, left, top, cellSize, g.width, g.height)
        drawLockEffects(canvas, left, top, cellSize, g.width, g.height)
        drawBoardFlash(canvas, boardRect)
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
            val grow = (flash.scale - 1f) * cellSize * 0.45f
            val inset = max(2f, cellSize * 0.06f - grow)
            flashPaint.alpha = (flash.alpha * 210).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                RectF(left + inset, top + inset, left + cellSize - inset, top + cellSize - inset),
                cellSize * 0.2f,
                cellSize * 0.2f,
                flashPaint
            )
        }
    }

    private fun drawBurstEffects(
        canvas: Canvas,
        boardLeft: Float,
        boardTop: Float,
        cellSize: Float,
        boardWidth: Int,
        boardHeight: Int
    ) {
        for (burst in burstCells) {
            if (burst.x !in 0 until boardWidth || burst.y !in 0 until boardHeight) continue
            val cx = boardLeft + burst.x * cellSize + cellSize / 2f
            val cy = boardTop + burst.y * cellSize + cellSize / 2f
            val radius = cellSize * (0.2f + burst.progress * 0.7f)

            val alpha = (burst.alpha * 255).toInt().coerceIn(0, 255)
            burstPaint.alpha = alpha
            burstLinePaint.alpha = alpha

            canvas.drawCircle(cx, cy, radius, burstPaint)
            val line = radius * 0.9f
            canvas.drawLine(cx - line, cy, cx + line, cy, burstLinePaint)
            canvas.drawLine(cx, cy - line, cx, cy + line, burstLinePaint)
        }
    }

    private fun drawLockEffects(
        canvas: Canvas,
        boardLeft: Float,
        boardTop: Float,
        cellSize: Float,
        boardWidth: Int,
        boardHeight: Int
    ) {
        for (pulse in lockPulses) {
            if (pulse.x !in 0 until boardWidth || pulse.y !in 0 until boardHeight) continue
            val cx = boardLeft + pulse.x * cellSize + cellSize / 2f
            val cy = boardTop + pulse.y * cellSize + cellSize / 2f
            val radius = cellSize * (0.28f + pulse.progress * 0.55f)
            lockPaint.alpha = (pulse.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius, lockPaint)
        }
    }

    private fun drawBoardFlash(canvas: Canvas, boardRect: RectF) {
        if (boardFlashAlpha <= 0f) return
        boardFlashPaint.alpha = (boardFlashAlpha * 200).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(boardRect, 16f, 16f, boardFlashPaint)
    }

    private fun drawChainBanner(canvas: Canvas, boardTop: Float) {
        val text = chainBanner ?: return
        if (chainBannerAlpha <= 0f) return
        chainPaint.alpha = (chainBannerAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawText(text, width / 2f, boardTop - 18f, chainPaint)
    }

    private fun advanceEffects(): Boolean {
        var animating = false

        val flashIterator = flashCells.iterator()
        while (flashIterator.hasNext()) {
            val flash = flashIterator.next()
            flash.alpha -= 0.08f
            flash.scale += 0.03f
            if (flash.alpha <= 0f) {
                flashIterator.remove()
            } else {
                animating = true
            }
        }

        val burstIterator = burstCells.iterator()
        while (burstIterator.hasNext()) {
            val burst = burstIterator.next()
            burst.progress += 0.16f
            burst.alpha -= 0.09f
            if (burst.alpha <= 0f || burst.progress >= 1.3f) {
                burstIterator.remove()
            } else {
                animating = true
            }
        }

        val lockIterator = lockPulses.iterator()
        while (lockIterator.hasNext()) {
            val pulse = lockIterator.next()
            pulse.progress += 0.15f
            pulse.alpha -= 0.11f
            if (pulse.alpha <= 0f || pulse.progress >= 1.1f) {
                lockIterator.remove()
            } else {
                animating = true
            }
        }

        if (boardFlashAlpha > 0f) {
            boardFlashAlpha -= 0.06f
            animating = true
            if (boardFlashAlpha < 0f) {
                boardFlashAlpha = 0f
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
