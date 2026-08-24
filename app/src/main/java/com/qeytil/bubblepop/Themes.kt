package com.qeytil.bubblepop

import android.graphics.Color

data class Theme(
    val key: String,
    val name: String,
    val unlockScore: Long,
    val colors: List<Int>,
    val bgTop: Int,
    val bgBottom: Int,
    val wall: Int,
    val text: Int
)

object Themes {
    val all = listOf(
        Theme("classic", "CLASSIC", 0L, listOf(0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFF2ECC71.toInt(), 0xFF3498DB.toInt(), 0xFF9B59B6.toInt(), 0xFFFF6B9D.toInt()),
            0xFF1C2452.toInt(), 0xFF10142B.toInt(), 0xFF232B5C.toInt(), 0xFFEAF0FF.toInt()),
        Theme("neon", "NEON", 4000L, listOf(0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFF2E88.toInt(), 0xFF9DFF00.toInt(), 0xFFFFA500.toInt(), 0xFF4DA6FF.toInt()),
            0xFF14002E.toInt(), 0xFF05000F.toInt(), 0xFF3A1060.toInt(), 0xFF7DFFE9.toInt()),
        Theme("ocean", "OCEAN", 12000L, listOf(0xFF00B4D8.toInt(), 0xFF48CAE4.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF118AB2.toInt()),
            0xFF04395E.toInt(), 0xFF001529.toInt(), 0xFF0B5378.toInt(), 0xFFBDE0FE.toInt()),
        Theme("sunset", "SUNSET", 25000L, listOf(0xFFFF477E.toInt(), 0xFFFF7B54.toInt(), 0xFFFFD166.toInt(), 0xFFFF5400.toInt(), 0xFFEF476F.toInt(), 0xFFFFA07A.toInt()),
            0xFF4A1942.toInt(), 0xFF23102E.toInt(), 0xFF6E2B57.toInt(), 0xFFFFE3E3.toInt()),
        Theme("galaxy", "GALAXY", 45000L, listOf(0xFF9B59B6.toInt(), 0xFFE74C3C.toInt(), 0xFF3AA0FF.toInt(), 0xFF2EE6A8.toInt(), 0xFFF4B942.toInt(), 0xFF7C83FD.toInt()),
            0xFF1A0A2E.toInt(), 0xFF07030F.toInt(), 0xFF2D1854.toInt(), 0xFFD4B8FF.toInt())
    )

    fun byKey(key: String): Theme = all.firstOrNull { it.key == key } ?: all[0]
}

class ThemeManager(private val store: Store) {
    var current: Theme = Themes.byKey(store.activeTheme)
        private set

    fun reload() { current = Themes.byKey(store.activeTheme) }

    fun select(t: Theme) {
        current = t
        store.activeTheme = t.key
    }

    fun palette(numColors: Int): List<Int> = current.colors.take(numColors)

    /** returns newly unlocked theme or null */
    fun checkUnlocks(totalScore: Long): Theme? {
        var newly: Theme? = null
        for (t in Themes.all) {
            if (!store.themeUnlocked(t.key) && totalScore >= t.unlockScore) {
                store.unlockTheme(t.key)
                if (newly == null) newly = t
            }
        }
        return newly
    }
}
