package com.qeytil.bubblepop

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Sound(ctx: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()
    var enabled = true

    init {
        val dir = File(ctx.cacheDir, "sfx")
        if (!dir.exists()) dir.mkdirs()
        loadAll(dir)
    }

    private fun makeWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val header = ByteArray(44)
        val total = pcm.size + 36
        fun le(off: Int, v: Int) {
            header[off] = (v and 0xFF).toByte(); header[off+1] = ((v shr 8) and 0xFF).toByte()
            header[off+2] = ((v shr 16) and 0xFF).toByte(); header[off+3] = ((v shr 24) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        le(4, total); "WAVE".forEachIndexed { i, c -> header[8+i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12+i] = c.code.toByte() }
        le(16, 16); le(20, 1); le(22, 1); le(24, sampleRate); le(28, sampleRate * 2); le(32, 2); le(34, 16)
        "data".forEachIndexed { i, c -> header[36+i] = c.code.toByte() }
        le(40, pcm.size)
        return header + pcm
    }

    private fun writeIfAbsent(f: File, bytes: ByteArray): File {
        if (!f.exists()) FileOutputStream(f).use { it.write(bytes) }
        return f
    }

    private fun loadAll(dir: File) {
        fun put(name: String, bytes: ByteArray) {
            val f = writeIfAbsent(File(dir, name), bytes)
            ids[name.substringBefore('.')] = pool.load(f.absolutePath, 1)
        }

        fun tone(name: String, f0: Double, f1: Double, ms: Int, type: Int, vol: Double) {
            put("$name.wav", wavBytes(f0, f1, ms, type, vol))
        }

        repeat(6) { i ->
            tone("pop$i", 420.0 + i * 90, 180.0, 95, 0, 0.30)
        }
        tone("shoot", 340.0, 130.0, 70, 0, 0.22)
        tone("bounce", 190.0, 140.0, 45, 0, 0.14)
        tone("snap", 240.0, 200.0, 50, 0, 0.18)
        tone("drop", 120.0, 55.0, 260, 2, 0.22)
        tone("click", 700.0, 520.0, 45, 0, 0.16)
        tone("combo", 520.0, 780.0, 140, 1, 0.20)
        tone("star", 900.0, 1250.0, 190, 0, 0.22)
        listOf(523.0, 659.0, 784.0, 1047.0).forEachIndexed { i, f ->
            put("win$i.wav", wavBytes(f, f, 170, 0, 0.26))
        }
        listOf(330.0, 262.0, 208.0, 156.0).forEachIndexed { i, f ->
            put("lose$i.wav", wavBytes(f, f * 0.94, 210, 2, 0.16))
        }
    }

    private fun wavBytes(f0: Double, f1: Double, ms: Int, type: Int, vol: Double): ByteArray {
        val sr = 22050
        val n = max(1.0, sr * ms / 1000.0).toInt()
        val samples = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val frac = i.toDouble() / n
            val freq = f0 + (f1 - f0) * frac
            val phase = 2 * PI * freq * t
            var s = when (type) {
                1 -> asin(sin(phase)) * 2.0 / PI                       // triangle
                2 -> 2.0 * (t * freq - (t * freq + 0.5).toInt())       // saw
                else -> sin(phase)
            }
            val env = exp(-4.0 * frac) * min(1.0, i / max(1.0, sr * 0.004))
            samples[i] = (s * env * vol * 32767.0 * 0.85).toInt().toShort()
        }
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            pcm[i*2] = (v and 0xFF).toByte()
            pcm[i*2+1] = ((v shr 8) and 0xFF).toByte()
        }
        return makeWav(pcm, sr)
    }

    fun play(name: String) {
        if (!enabled) return
        ids[name]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun pop(count: Int) {
        val k = min(count, 6) - 1
        play("pop$k")
    }
}
