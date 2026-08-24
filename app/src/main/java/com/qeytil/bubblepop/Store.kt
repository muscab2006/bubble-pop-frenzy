package com.qeytil.bubblepop

import android.content.Context
import android.content.SharedPreferences

class Store(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("bpf", Context.MODE_PRIVATE)

    fun get(key: String, def: Int): Int = p.getInt(key, def)
    fun get(key: String, def: Long): Long = p.getLong(key, def)
    fun get(key: String, def: Boolean): Boolean = p.getBoolean(key, def)
    fun put(key: String, v: Int) { p.edit().putInt(key, v).apply() }
    fun put(key: String, v: Long) { p.edit().putLong(key, v).apply() }
    fun put(key: String, v: Boolean) { p.edit().putBoolean(key, v).apply() }

    var highScore: Int
        get() = get("highscore", 0)
        set(v) { if (v > highScore) put("highscore", v) }

    var totalScore: Long
        get() = get("totalscore", 0L)
        set(v) = put("totalscore", v)

    fun addTotalScore(v: Int) { totalScore = totalScore + v }

    var level: Int
        get() = get("level", 1)
        set(v) { if (v > level) put("level", v) }

    fun starsFor(levelNum: Int): Int {
        val map = p.getString("stars", "") ?: ""
        map.split(';').forEach { entry ->
            if (entry.isNotEmpty()) {
                val parts = entry.split(':')
                if (parts.size == 2 && parts[0].toIntOrNull() == levelNum) return parts[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

    fun totalStars(): Int {
        val map = p.getString("stars", "") ?: ""
        var t = 0
        map.split(';').forEach { e -> if (e.isNotEmpty()) t += e.split(':').last().toIntOrNull() ?: 0 }
        return t
    }

    fun setStars(levelNum: Int, n: Int) {
        if (n <= starsFor(levelNum)) return
        val map = (p.getString("stars", "") ?: "")
            .split(';')
            .filter { it.isNotEmpty() }
            .filter { it.split(':')[0].toIntOrNull() != levelNum }
            .toMutableList()
        map.add("$levelNum:$n")
        p.edit().putString("stars", map.joinToString(";")).apply()
    }

    fun maxStarLevel(): Int =
        (p.getString("stars", "") ?: "").split(';')
            .filter { it.isNotEmpty() }
            .mapNotNull { it.split(':').first().toIntOrNull() }
            .maxOrNull() ?: 0

    var soundOn: Boolean
        get() = get("sound", true)
        set(v) = put("sound", v)

    fun themeUnlocked(name: String): Boolean =
        (p.getStringSet("themes", setOf("classic")) ?: setOf("classic")).contains(name)

    fun unlockTheme(name: String) {
        val s = (p.getStringSet("themes", setOf("classic")) ?: setOf("classic")).toMutableSet()
        s.add(name)
        p.edit().putStringSet("themes", s).apply()
    }

    var activeTheme: String
        get() = p.getString("activeTheme", "classic") ?: "classic"
        set(v) { p.edit().putString("activeTheme", v).apply() }

    var dailyHigh: Int
        get() = get("dailyHigh", 0)
        set(v) { if (v > dailyHigh) put("dailyHigh", v) }

    var lastDailyDate: String
        get() = p.getString("dailyDate", "") ?: ""
        set(v) { p.edit().putString("dailyDate", v).apply() }

    var gamesPlayed: Int
        get() = get("gamesPlayed", 0)
        set(v) = put("gamesPlayed", v)

    var totalPops: Int
        get() = get("totalPops", 0)
        set(v) = put("totalPops", v)

    var bestCombo: Int
        get() = get("bestCombo", 0)
        set(v) { if (v > bestCombo) put("bestCombo", v) }
}
