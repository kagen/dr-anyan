package com.example.drmario

import kotlin.random.Random

enum class PillColor(val drawColor: Int) {
    RED(0xFFE53935.toInt()),
    BLUE(0xFF1E88E5.toInt()),
    YELLOW(0xFFFDD835.toInt())
}

enum class CellKind {
    VIRUS,
    CAPSULE
}

data class Cell(
    val color: PillColor,
    val kind: CellKind,
    var groupId: Int?
)

data class ActiveCapsule(
    var anchorX: Int,
    var anchorY: Int,
    var orientation: Int,
    val firstColor: PillColor,
    val secondColor: PillColor,
    val groupId: Int
)

class DrMarioGame(
    val width: Int = 8,
    val height: Int = 16,
    initialVirusCount: Int = 14,
    private val random: Random = Random.Default
) {
    private val board: Array<Array<Cell?>> = Array(height) { arrayOfNulls(width) }
    private var nextGroupId = 1
    private var active: ActiveCapsule? = null

    var score: Int = 0
        private set

    var gameOver: Boolean = false
        private set

    var virusesRemaining: Int = 0
        private set

    init {
        spawnViruses(initialVirusCount)
        spawnCapsule()
    }

    fun snapshotBoard(): Array<Array<Cell?>> {
        return Array(height) { y -> Array(width) { x -> board[y][x] } }
    }

    fun activeCells(): List<Pair<Int, Int>> {
        val capsule = active ?: return emptyList()
        val second = secondOffset(capsule.orientation)
        return listOf(
            capsule.anchorX to capsule.anchorY,
            capsule.anchorX + second.first to capsule.anchorY + second.second
        )
    }

    fun activeColors(): List<PillColor> {
        val capsule = active ?: return emptyList()
        return listOf(capsule.firstColor, capsule.secondColor)
    }

    fun tick() {
        if (gameOver) return
        val capsule = active ?: run {
            spawnCapsule()
            return
        }
        if (!moveCapsule(capsule, 0, 1)) {
            lockCapsule(capsule)
            resolveBoard()
            spawnCapsule()
        }
    }

    fun moveLeft() {
        val capsule = active ?: return
        moveCapsule(capsule, -1, 0)
    }

    fun moveRight() {
        val capsule = active ?: return
        moveCapsule(capsule, 1, 0)
    }

    fun rotateClockwise() {
        val capsule = active ?: return
        val originalX = capsule.anchorX
        val originalY = capsule.anchorY
        val originalOrientation = capsule.orientation

        capsule.orientation = (capsule.orientation + 1) % 4
        val kicks = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1)
        for ((dx, dy) in kicks) {
            capsule.anchorX = originalX + dx
            capsule.anchorY = originalY + dy
            if (isCapsulePositionValid(capsule)) {
                return
            }
        }

        capsule.anchorX = originalX
        capsule.anchorY = originalY
        capsule.orientation = originalOrientation
    }

    fun hardDrop() {
        if (gameOver) return
        val capsule = active ?: return
        while (moveCapsule(capsule, 0, 1)) {
            // Continue dropping.
        }
        tick()
    }

    private fun spawnViruses(targetCount: Int) {
        virusesRemaining = 0
        var placed = 0
        var attempts = 0
        while (placed < targetCount && attempts < targetCount * 20) {
            attempts++
            val x = random.nextInt(width)
            val y = random.nextInt(height / 2, height)
            if (board[y][x] != null) continue
            val color = PillColor.entries[random.nextInt(PillColor.entries.size)]
            board[y][x] = Cell(color, CellKind.VIRUS, null)
            placed++
        }
        virusesRemaining = placed
    }

    private fun spawnCapsule() {
        if (gameOver) return
        val colors = listOf(
            PillColor.entries[random.nextInt(PillColor.entries.size)],
            PillColor.entries[random.nextInt(PillColor.entries.size)]
        )
        val capsule = ActiveCapsule(
            anchorX = width / 2 - 1,
            anchorY = 1,
            orientation = 3,
            firstColor = colors[0],
            secondColor = colors[1],
            groupId = nextGroupId++
        )
        if (!isCapsulePositionValid(capsule)) {
            gameOver = true
            active = null
            return
        }
        active = capsule
    }

    private fun secondOffset(orientation: Int): Pair<Int, Int> {
        return when (orientation and 3) {
            0 -> 0 to -1
            1 -> 1 to 0
            2 -> 0 to 1
            else -> -1 to 0
        }
    }

    private fun moveCapsule(capsule: ActiveCapsule, dx: Int, dy: Int): Boolean {
        capsule.anchorX += dx
        capsule.anchorY += dy
        if (isCapsulePositionValid(capsule)) {
            return true
        }
        capsule.anchorX -= dx
        capsule.anchorY -= dy
        return false
    }

    private fun isCapsulePositionValid(capsule: ActiveCapsule): Boolean {
        val second = secondOffset(capsule.orientation)
        val positions = listOf(
            capsule.anchorX to capsule.anchorY,
            capsule.anchorX + second.first to capsule.anchorY + second.second
        )
        for ((x, y) in positions) {
            if (x !in 0 until width || y !in 0 until height) return false
            if (board[y][x] != null) return false
        }
        return true
    }

    private fun lockCapsule(capsule: ActiveCapsule) {
        val second = secondOffset(capsule.orientation)
        val firstX = capsule.anchorX
        val firstY = capsule.anchorY
        val secondX = capsule.anchorX + second.first
        val secondY = capsule.anchorY + second.second

        board[firstY][firstX] = Cell(capsule.firstColor, CellKind.CAPSULE, capsule.groupId)
        board[secondY][secondX] = Cell(capsule.secondColor, CellKind.CAPSULE, capsule.groupId)
        active = null
    }

    private fun resolveBoard() {
        var anyClear: Boolean
        do {
            anyClear = clearMatches()
            if (anyClear) {
                while (applyGravityStep()) {
                    // Continue falling until stable.
                }
            }
        } while (anyClear)
    }

    private fun clearMatches(): Boolean {
        val toClear = mutableSetOf<Pair<Int, Int>>()

        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val cell = board[y][x]
                if (cell == null) {
                    x++
                    continue
                }
                var end = x + 1
                while (end < width && board[y][end]?.color == cell.color) {
                    end++
                }
                if (end - x >= 4) {
                    for (cx in x until end) {
                        toClear += cx to y
                    }
                }
                x = end
            }
        }

        for (x in 0 until width) {
            var y = 0
            while (y < height) {
                val cell = board[y][x]
                if (cell == null) {
                    y++
                    continue
                }
                var end = y + 1
                while (end < height && board[end][x]?.color == cell.color) {
                    end++
                }
                if (end - y >= 4) {
                    for (cy in y until end) {
                        toClear += x to cy
                    }
                }
                y = end
            }
        }

        if (toClear.isEmpty()) return false

        for ((x, y) in toClear) {
            val cell = board[y][x] ?: continue
            if (cell.kind == CellKind.VIRUS) {
                virusesRemaining = (virusesRemaining - 1).coerceAtLeast(0)
            }
            board[y][x] = null
            score += 100
        }

        detachBrokenCapsules()

        if (virusesRemaining == 0) {
            gameOver = true
        }

        return true
    }

    private fun detachBrokenCapsules() {
        val groups = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cell = board[y][x] ?: continue
                val groupId = cell.groupId ?: continue
                groups.getOrPut(groupId) { mutableListOf() }.add(x to y)
            }
        }

        for ((_, cells) in groups) {
            if (cells.size != 2) {
                for ((x, y) in cells) {
                    board[y][x]?.groupId = null
                }
                continue
            }
            val (ax, ay) = cells[0]
            val (bx, by) = cells[1]
            if (kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by) != 1) {
                board[ay][ax]?.groupId = null
                board[by][bx]?.groupId = null
            }
        }
    }

    private fun applyGravityStep(): Boolean {
        var moved = false
        val processedGroups = mutableSetOf<Int>()

        for (y in height - 2 downTo 0) {
            for (x in 0 until width) {
                val cell = board[y][x] ?: continue
                if (cell.kind == CellKind.VIRUS) continue

                val groupId = cell.groupId
                if (groupId != null && processedGroups.add(groupId)) {
                    val partner = findPartner(x, y, groupId)
                    if (partner != null) {
                        val (px, py) = partner
                        if (canPairFall(x, y, px, py)) {
                            movePairDown(x, y, px, py)
                            moved = true
                        }
                        continue
                    } else {
                        cell.groupId = null
                    }
                }

                if (canCellFall(x, y)) {
                    board[y + 1][x] = board[y][x]
                    board[y][x] = null
                    moved = true
                }
            }
        }

        return moved
    }

    private fun findPartner(x: Int, y: Int, groupId: Int): Pair<Int, Int>? {
        val dirs = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
        for ((dx, dy) in dirs) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            if (board[ny][nx]?.groupId == groupId) {
                return nx to ny
            }
        }
        return null
    }

    private fun canPairFall(ax: Int, ay: Int, bx: Int, by: Int): Boolean {
        val cells = setOf(ax to ay, bx to by)
        for ((x, y) in cells) {
            val ny = y + 1
            if (ny >= height) return false
            if ((x to ny) in cells) continue
            if (board[ny][x] != null) return false
        }
        return true
    }

    private fun movePairDown(ax: Int, ay: Int, bx: Int, by: Int) {
        val first = board[ay][ax]
        val second = board[by][bx]

        board[ay][ax] = null
        board[by][bx] = null

        board[ay + 1][ax] = first
        board[by + 1][bx] = second
    }

    private fun canCellFall(x: Int, y: Int): Boolean {
        val ny = y + 1
        if (ny >= height) return false
        return board[ny][x] == null
    }
}
