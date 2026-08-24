package com.qeytil.bubblepop

import kotlin.math.abs
import kotlin.math.floor

fun mulberry32(seed: Long): () -> Double {
    var a = seed
    return {
        a += 0x6D2B79F5L
        var t = a
        t = (t xor (t ushr 15)) * (t or 1L)
        t = t xor ((t xor (t ushr 7)) * (t or 61L))
        (((t xor (t ushr 14)) and 0xFFFFFFFFL).toDouble() / 4294967296.0)
    }
}

object Levels {

    const val COLS = 11
    const val MAX_LEVEL = 50

    fun colorCount(level: Int) = when {
        level <= 3 -> 3
        level <= 7 -> 4
        level <= 14 -> 5
        else -> 6
    }

    fun rowsFor(level: Int) = (5 + level / 2).coerceAtMost(11)

    fun shotsPerDrop(level: Int) = (9 - level / 3).coerceAtLeast(4)

    fun dailySeed(dateStr: String): Long {
        var h = 2166136261L
        for (ch in dateStr) {
            h = h xor ch.code.toLong()
            h *= 16777619L
        }
        return h
    }

    /** grid[r][c] >= 0 color index, -1 empty. Even r: COLS wide; odd r: COLS-1 (indented). */
    fun generate(level: Int, seedOverride: Long? = null): LevelData {
        val seed = seedOverride ?: (level * 7919L + 13L)
        val rng = mulberry32(seed)
        val numColors = colorCount(level)
        val rows = rowsFor(level)
        val shapes = listOf("solid", "pyramid", "checker", "stripes", "arch")
        val shape = shapes[(level - 1).mod(shapes.size)]

        val grid = Array(rows) { IntArray(COLS) { -1 } }
        for (r in 0 until rows) {
            val rowLen = if (r % 2 == 1) COLS - 1 else COLS
            for (c in 0 until rowLen) {
                var place = true
                when (shape) {
                    "pyramid" -> {
                        val half = rowLen / 2.0
                        val width = (r + 2.0) / (rows + 1.0) * half + 1.2
                        if (abs(c + 0.5 - half) > width) place = false
                    }
                    "checker" -> if (r > 0 && (r + c) % 3 == 0 && rng() < 0.85) place = false
                    "stripes" -> if (r > 0 && rng() < 0.12) place = false
                    "arch" -> {
                        val mid = if (rows > 1) r.toDouble() / (rows - 1) else 0.0
                        val cut = 1 + floor(abs(0.5 - mid) * COLS * 0.55).toInt()
                        place = c >= cut && c < rowLen - cut || r == 0
                    }
                }
                if (place) grid[r][c] = 0
            }
        }

        // cluster-biased coloring so matches are satisfying
        for (r in 0 until rows) {
            val rowLen = if (r % 2 == 1) COLS - 1 else COLS
            for (c in 0 until rowLen) {
                if (grid[r][c] != 0) continue
                var picked = floor(rng() * numColors).toInt().coerceIn(0, numColors - 1)
                val left = if (c > 0) grid[r][c - 1] else -1
                val up = if (r >= 2) grid[r - 2][c] else -1
                val roll = rng()
                picked = when {
                    roll < 0.40 && left in 0 until numColors -> left
                    roll < 0.60 && up in 0 until numColors -> up
                    roll < 0.72 -> maxOf(0, picked - 1)
                    roll > 0.92 -> (picked + 1) % numColors
                    else -> picked
                }
                grid[r][c] = picked
            }
        }

        return LevelData(grid, numColors, shotsPerDrop(level), shape, seed)
    }
}

data class LevelData(val grid: Array<IntArray>, val numColors: Int, val shotsPerDrop: Int, val shape: String, val seed: Long)
