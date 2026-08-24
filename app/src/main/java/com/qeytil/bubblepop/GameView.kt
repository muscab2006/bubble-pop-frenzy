package com.qeytil.bubblepop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    enum class State { MENU, THEMES, STATS, PLAYING, PAUSED, OVER, COMPLETE }

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var surfaceReady = false

    val store = Store(context)
    val sound = Sound(context)
    val themeManager = ThemeManager(store)

    var state = State.MENU

    // metrics (px)
    var W = 0f; var H = 0f
    var R = 20f; var rowH = 34f
    var left = 0f; var right = 0f
    var top = 64f; var deadlineY = 0f
    var cannonX = 0f; var cannonY = 0f
    var flySpeed = 12f

    // grid
    val grid = ArrayList<Row>()
    var numColors = 4
    var palette: List<Int> = emptyList()
    var current: Bubble? = null
    var next: Bubble? = null
    var flying: Fly? = null

    var aimAngle = (-Math.PI / 2).toFloat()
    var mode = "level"
    var levelNum = 1
    var dailyTarget = 0

    var score = 0
    var comboStreak = 0
    var shotsUntilDrop = 8
    var shotsPerDrop = 8
    var popsThisLevel = 0
    var shake = 0f
    var time = 0f
    var lastHudScore = -1
    var completeAt = 0f

    val fallingFx = ArrayList<FallingFx>()
    val popFx = ArrayList<PopFx>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FxText>()
    val previewPts = ArrayList<FloatArray>()
    var previewGhostI = -1
    var previewGhostC = -1

    val buttons = HashMap<String, RectF>()

    // paints
    private val pBg = Paint()
    private val pWall = Paint()
    private val pLine = Paint().apply {
        color = Color.argb(255, 255, 70, 70)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 9f), 0f)
    }
    private val pWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    private lateinit var pTitle: Paint
    private lateinit var pHudBig: Paint
    private lateinit var pHudSmall: Paint
    private lateinit var pBtn: Paint
    private lateinit var pBtnPrimary: Paint
    private lateinit var pBtnAccent: Paint
    private lateinit var pBtnText: Paint
    private lateinit var pPanel: Paint
    private lateinit var pStarOn: Paint
    private lateinit var pStarOff: Paint

    private val sprites = HashMap<Int, Bitmap>()
    private var spriteR = 0f

    init {
        holder.addCallback(this)
        focusable = FOCUSABLE
    }

    // ---------------- input ----------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sound.enabled = store.soundOn
                when (state) {
                    State.PLAYING -> {
                        val x = e.x; val y = e.y
                        if (hitSwap(x, y)) { doSwap(); return true }
                        setAim(x, y)
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.PLAYING) setAim(e.x, e.y)
            }
            MotionEvent.ACTION_UP -> {
                val x = e.x; val y = e.y
                when (state) {
                    State.PLAYING -> {
                        if (isPauseTap(x, y)) { sound.play("click"); state = State.PAUSED }
                        else if (!hitSwap(x, y)) { setAim(x, y); shoot() }
                    }
                    else -> tapScreen(x, y)
                }
            }
        }
        return true
    }

    fun isPauseTap(x: Float, y: Float): Boolean =
        buttons["pause"]?.contains(x, y) == true

    fun hitSwap(x: Float, y: Float): Boolean {
        val sx = W - 46.dp(); val sy = H - 46.dp()
        val dx = x - sx; val dy = y - sy
        return dx * dx + dy * dy < 36.dp() * 36.dp()
    }

    fun setAim(x: Float, y: Float) {
        var a = atan2(y - cannonY, x - cannonX).toFloat()
        if (a >= 0f) a = if (a > Math.PI / 2) (-Math.PI + 0.2).toFloat() else -0.2f
        if (a > -0.2f) a = -0.2f
        if (a < -Math.PI + 0.2f) a = (-Math.PI + 0.2f).toFloat()
        aimAngle = a
    }

    fun todayString(): String {
        val c = Calendar.getInstance()
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun dailyAvailable(): Boolean = store.lastDailyDate != todayString()

    fun tapScreen(x: Float, y: Float) {
        for ((id, rect) in buttons) {
            if (rect.contains(x, y)) {
                sound.play("click")
                onButton(id)
                return
            }
        }
    }

    fun onButton(id: String) {
        when (id) {
            "continue" -> startLevel(min(store.level, Levels.MAX_LEVEL), "level")
            "new" -> startLevel(1, "level")
            "daily" -> if (dailyAvailable()) startLevel(1, "daily")
            "themes" -> state = State.THEMES
            "stats" -> state = State.STATS
            "sound" -> { store.soundOn = !store.soundOn; sound.enabled = store.soundOn }
            "back" -> state = State.MENU
            "pause" -> state = State.PAUSED
            "resume" -> state = State.PLAYING
            "prestart" -> restartLevel()
            "pmenu" -> state = State.MENU
            "again" -> restartLevel()
            "omenu" -> state = State.MENU
            "next" -> startLevel(levelNum + 1, "level")
            "cmenu" -> state = State.MENU
            "theme_classic" -> pickTheme("classic")
            "theme_neon" -> pickTheme("neon")
            "theme_ocean" -> pickTheme("ocean")
            "theme_sunset" -> pickTheme("sunset")
            "theme_galaxy" -> pickTheme("galaxy")
        }
    }

    fun pickTheme(key: String) {
        val t = Themes.byKey(key)
        if (store.themeUnlocked(key) && t.key != themeManager.current.key) {
            themeManager.select(t)
            sprites.clear()
        }
    }

    // ---------------- surface / loop ----------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        layout(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    fun resumeGame() {
        if (running) return
        running = true
        thread = Thread(this, "game-loop")
        thread!!.start()
    }

    fun pauseLoop() {
        running = false
        try { thread?.join() } catch (_: Exception) {}
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1000000.0 / 16.6667).toFloat().coerceIn(0f, 2.5f)
            last = now
            time += dt
            update(dt)

            if (W == 0f) { try { Thread.sleep(8) } catch (_: InterruptedException) { return }; continue }

            var c: Canvas? = null
            if (surfaceReady) {
                try {
                    c = holder.lockCanvas()
                    if (c != null) { synchronized(holder) { render(c) } }
                } finally {
                    if (c != null) try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {}
                }
            }
            val spent = (System.nanoTime() - last) / 1000000.0
            val sleep = (16.0 - spent).toLong().coerceAtLeast(2)
            try { Thread.sleep(sleep) } catch (_: InterruptedException) { return }
        }
    }

    fun layout(w: Float, h: Float) {
        W = w; H = h
        R = ((W - 12f) / (Levels.COLS * 2)).coerceAtLeast(13f)
        rowH = (R * 1.7320508f)
        left = (W - Levels.COLS * 2 * R) / 2f
        right = left + Levels.COLS * 2 * R
        top = 64.dp()
        deadlineY = H - 150.dp()
        cannonX = W / 2f
        cannonY = H - 58.dp()
        flySpeed = (R * 0.62f).coerceAtLeast(9f)
        sprites.clear()
        initPaints()
        refreshPositions()
    }

    fun Float.dp(): Float = this * resources.displayMetrics.density
    fun Int.dp(): Float = this * resources.displayMetrics.density

    fun initPaints() {
        pBg.shader = LinearGradient(0f, 0f, 0f, H,
            themeManager.current.bgTop, themeManager.current.bgBottom, Shader.TileMode.CLAMP)
        pWall.color = themeManager.current.wall

        pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 46.dp()
        }
        pHudBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL); textSize = 19.dp(); color = Color.WHITE }
        pHudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = 11.dp(); color = Color.WHITE }
        pBtn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
        pBtnPrimary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 200.dp(), 60.dp(),
                Color.rgb(55, 214, 122), Color.rgb(43, 167, 232), Shader.TileMode.CLAMP) }
        pBtnAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 200.dp(), 60.dp(),
                Color.rgb(255, 107, 107), Color.rgb(255, 169, 77), Shader.TileMode.CLAMP) }
        pBtnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL)
            textSize = 14.dp(); color = Color.WHITE }
        pPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 20, 26, 56) }
        pStarOn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = 40.dp(); color = Color.rgb(255, 217, 61) }
        pStarOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = 40.dp(); color = Color.argb(90, 44, 51, 87) }
    }

    // ---------------- sprites ----------------

    fun sprite(colorIdx: Int): Bitmap? {
        val col = palette.getOrNull(colorIdx) ?: return null
        if (spriteR != R || !sprites.containsKey(colorIdx)) {
            spriteR = R
            val pad = (R * 0.25f).toInt() + 2
            val d = (R * 2 + pad * 2).toInt()
            val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = d / 2f; val cy = d / 2f
            val num = col
            val lr = min(255, (num shr 16 and 0xFF) + 70)
            val lg = min(255, (num shr 8 and 0xFF) + 70)
            val lb = min(255, (num and 0xFF) + 70)
            val dr = maxOf(0, (num shr 16 and 0xFF) - 45)
            val dg = maxOf(0, (num shr 8 and 0xFF) - 45)
            val db = maxOf(0, (num and 0xFF) - 45)
            val g = RadialGradient(cx - R * 0.35f, cy - R * 0.4f, R,
                intArrayOf(Color.rgb(lr, lg, lb), num, Color.rgb(dr, dg, db)),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g }
            c.drawCircle(cx, cy, R, paint)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = R * 0.07f
                color = Color.argb(50, 0, 0, 0)
            }
            c.drawCircle(cx, cy, R - ring.strokeWidth / 2, ring)
            val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
            c.save()
            c.rotate(-36f, cx - R * 0.32f, cy - R * 0.38f)
            c.drawOval(RectF(cx - R * 0.6f, cy - R * 0.56f, cx - R * 0.04f, cy - R * 0.2f), gloss)
            c.restore()
            sprites[colorIdx] = bmp
        }
        return sprites[colorIdx]
    }

    fun drawBubbleAt(c: Canvas, x: Float, y: Float, colorIdx: Int, scale: Float = 1f, alpha: Int = 255) {
        val s = sprite(colorIdx) ?: return
        val half = s.width / 2f * scale
        val p = if (alpha == 255) null else Paint().apply { this.alpha = alpha }
        if (p == null) c.drawBitmap(s, x - half, y - half, null)
        else c.drawBitmap(s, x - half, y - half, p)
    }

    // ---------------- level flow ----------------

    fun startLevel(n: Int, m: String) {
        mode = m
        levelNum = n
        val data = if (mode == "daily") {
            val seed = Levels.dailySeed(todayString())
            dailyTarget = (600 + (seed % 900)).toInt()
            Levels.generate(3 + ((seed % 18).toInt()), seed)
        } else {
            dailyTarget = 0
            Levels.generate(n)
        }
        numColors = data.numColors
        palette = themeManager.palette(numColors)

        synchronized(grid) {
            grid.clear()
            for (r in data.grid.indices) {
                val row = Row(r % 2 == 1, arrayOfNulls(Levels.COLS))
                for (c in 0 until min(data.grid[r].size, Levels.COLS)) {
                    val v = data.grid[r][c]
                    if (v in 0 until numColors) row.cells[c] = Bubble(v)
                }
                grid.add(row)
            }
        }

        score = 0; comboStreak = 0; popsThisLevel = 0
        shotsPerDrop = data.shotsPerDrop
        shotsUntilDrop = shotsPerDrop
        fallingFx.clear(); popFx.clear(); particles.clear(); texts.clear()
        flying = null; current = null
        shake = 0f

        refreshPositions(); trimEmptyRows()
        if (countBubbles() == 0) {
            val row = Row(false, arrayOfNulls(Levels.COLS))
            for (c in 0 until Levels.COLS) row.cells[c] = Bubble((0 until numColors).random())
            grid.add(row); refreshPositions()
        }
        spawnFromBoard()
        state = State.PLAYING
    }

    fun restartLevel() {
        if (mode == "daily") startLevel(1, "daily") else startLevel(levelNum, "level")
    }

    fun validLen(row: Row): Int = if (row.indent) Levels.COLS - 1 else Levels.COLS
    fun xOf(i: Int, c: Int): Float = left + R + c * 2 * R + if (grid[i].indent) R else 0f
    fun yOf(i: Int): Float = top + R + i * rowH

    fun cellAt(i: Int, c: Int): Bubble? {
        if (i < 0 || i >= grid.size) return null
        val row = grid[i]
        if (c < 0 || c >= row.cells.size || c >= validLen(row)) return null
        return row.cells[c]
    }

    fun setCell(i: Int, c: Int, b: Bubble?) {
        if (i < 0 || i >= grid.size) return
        val row = grid[i]
        if (c in 0 until row.cells.size) row.cells[c] = b
    }

    fun neighborsOf(i: Int, c: Int): List<IntArray> {
        val row = if (i in grid.indices) grid[i] else null
        val out = ArrayList<IntArray>(6)
        out.add(intArrayOf(i, c - 1)); out.add(intArrayOf(i, c + 1))
        if (row != null && row.indent) {
            out.add(intArrayOf(i - 1, c)); out.add(intArrayOf(i - 1, c + 1))
            out.add(intArrayOf(i + 1, c)); out.add(intArrayOf(i + 1, c + 1))
        } else {
            out.add(intArrayOf(i - 1, c - 1)); out.add(intArrayOf(i - 1, c))
            out.add(intArrayOf(i + 1, c - 1)); out.add(intArrayOf(i + 1, c))
        }
        return out
    }

    fun refreshPositions() {
        for (i in grid.indices) {
            val row = grid[i]
            for (c in row.cells.indices) {
                val b = row.cells[c] ?: continue
                b.x = xOf(i, c); b.y = yOf(i)
            }
        }
    }

    fun growTo(depth: Int) {
        while (grid.size <= depth) {
            val prev = grid.lastOrNull()
            grid.add(Row(prev?.indent != true, arrayOfNulls(Levels.COLS)))
        }
    }

    fun trimEmptyRows() {
        while (grid.isNotEmpty()) {
            val row = grid.last()
            var any = false
            for (b in row.cells) if (b != null) { any = true; break }
            if (!any) grid.removeAt(grid.size - 1) else break
        }
    }

    fun countBubbles(): Int {
        var n = 0
        for (row in grid) for (b in row.cells) if (b != null) n++
        return n
    }

    fun boardColors(): List<Int> {
        val set = LinkedHashSet<Int>()
        for (row in grid) for (b in row.cells) if (b != null) set.add(b.colorIdx)
        val arr = set.filter { it < numColors }
        return arr.ifEmpty { (0 until numColors).toList() }
    }

    fun pickColor(): Int {
        val pool = boardColors()
        return pool.random()
    }

    fun spawnFromBoard() {
        current = Bubble(pickColor())
        next = Bubble(pickColor())
    }

    fun doSwap() {
        val cu = current ?: return
        val nx = next ?: return
        sound.play("click")
        val t = cu.colorIdx; cu.colorIdx = nx.colorIdx; nx.colorIdx = t
        texts.add(FxText(cannonX, cannonY - 42.dp(), "SWAP", 0.9f, -0.7f, 14.dp(), false))
    }

    fun shoot() {
        if (state != State.PLAYING || flying != null) return
        val cur = current ?: return
        sound.play("shoot")
        flying = Fly(cannonX, cannonY,
            cos(aimAngle).toFloat() * flySpeed,
            sin(aimAngle).toFloat() * flySpeed,
            cur.colorIdx)
        current = null
        shotsUntilDrop--
    }

    fun findSnapCell(px: Float, py: Float): IntArray? {
        val depthGuess = ((py - top) / rowH).toInt().coerceAtLeast(grid.size - 1) + 2
        growTo(depthGuess)
        var best: IntArray? = null
        var bd = Float.MAX_VALUE
        for (pass in 0..1) {
            for (i in grid.indices) {
                val vl = validLen(grid[i])
                for (c in 0 until vl) {
                    if (grid[i].cells[c] != null) continue
                    var adj = i == 0
                    if (!adj && pass == 0) {
                        for (nb in neighborsOf(i, c)) if (cellAt(nb[0], nb[1]) != null) { adj = true; break }
                    }
                    if (pass == 0 && !adj) continue
                    val dx = px - xOf(i, c); val dy = py - yOf(i)
                    val d = dx * dx + dy * dy
                    if (d < bd) { bd = d; best = intArrayOf(i, c) }
                }
            }
            if (best != null) break
        }
        return best
    }

    fun land(fly: Fly) {
        val cell = findSnapCell(fly.x, fly.y)
        if (cell == null) { afterShotLanded(); return }
        growTo(cell[0] + 1)
        val b = Bubble(fly.colorIdx)
        grid[cell[0]].cells[cell[1]] = b
        b.x = xOf(cell[0], cell[1]); b.y = yOf(cell[0])
        sound.play("snap")

        val cluster = matchCluster(cell[0], cell[1], fly.colorIdx)
        if (cluster.size >= 3) {
            comboStreak++
            val mult = min(comboStreak, 5)
            val pts = cluster.size * 10 * mult
            score += pts
            popsThisLevel += cluster.size
            for (p in cluster) {
                val bb = cellAt(p[0], p[1]) ?: continue
                popFx.add(PopFx(bb.x, bb.y, bb.colorIdx))
                burst(bb.x, bb.y, palette.getOrNull(bb.colorIdx) ?: Color.WHITE)
                setCell(p[0], p[1], null)
            }
            sound.pop(cluster.size)
            if (mult >= 2) sound.play("combo")
            texts.add(FxText(b.x, b.y - 14.dp(),
                "+$pts" + (if (mult > 1) "  x$mult" else ""), 1.15f, -1.1f, 17.dp(), false))
            if (mult >= 3) {
                texts.add(FxText(W / 2, H * 0.38f, "COMBO x$mult!", 1.3f, -0.55f, 30.dp(), true))
            }
            shake = (2 + cluster.size * 0.7f).coerceAtMost(11f)
            dropFloating()
        }

        afterShotLanded()
    }

    fun matchCluster(si: Int, sc: Int, colorIdx: Int): List<IntArray> {
        val seen = HashSet<String>()
        val out = ArrayList<IntArray>()
        val q = ArrayDeque<IntArray>()
        q.add(intArrayOf(si, sc))
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            val key = "${p[0]},${p[1]}"
            if (!seen.add(key)) continue
            val b = cellAt(p[0], p[1]) ?: continue
            if (b.colorIdx != colorIdx) continue
            out.add(p)
            for (nb in neighborsOf(p[0], p[1])) q.add(nb)
        }
        return out
    }

    fun dropFloating() {
        if (grid.isEmpty()) return
        val keep = HashSet<String>()
        for (c in 0 until validLen(grid[0])) if (cellAt(0, c) != null) markConnected(0, c, keep)

        var fallen = 0; var gain = 0
        for (i in grid.indices) {
            val vl = validLen(grid[i])
            for (c in 0 until vl) {
                val b = cellAt(i, c) ?: continue
                if (!keep.contains("$i,$c")) {
                    fallen++; gain += 20
                    fallingFx.add(FallingFx(b.x, b.y, (Math.random() - 0.5).toFloat() * 1.6f,
                        (-1 - Math.random()).toFloat(), b.colorIdx))
                    setCell(i, c, null)
                }
            }
        }
        if (fallen > 0) {
            score += gain
            popsThisLevel += fallen
            sound.play("drop")
            texts.add(FxText(W / 2, deadlineY - 60.dp(), "+$gain DROP", 1.2f, -1f, 19.dp(), false))
            trimEmptyRows(); refreshPositions()
        }
    }

    fun markConnected(i: Int, c: Int, keep: MutableSet<String>) {
        if (cellAt(i, c) == null) return
        val key = "$i,$c"
        if (!keep.add(key)) return
        val stack = ArrayDeque<IntArray>()
        stack.add(intArrayOf(i, c))
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            for (nb in neighborsOf(p[0], p[1])) {
                if (cellAt(nb[0], nb[1]) == null) continue
                val k2 = "${nb[0]},${nb[1]}"
                if (keep.add(k2)) stack.add(nb)
            }
        }
    }

    fun insertTopRow() {
        val indent = if (grid.isEmpty()) false else !grid[0].indent
        val row = Row(indent, arrayOfNulls(Levels.COLS))
        val vl = validLen(row)
        for (c in 0 until vl) {
            if (Math.random() < 0.86) row.cells[c] = Bubble((0 until numColors).random())
        }
        grid.add(0, row)
        refreshPositions()
        shake = maxOf(shake, 5f)
        sound.play("drop")
    }

    fun afterShotLanded() {
        if (shotsUntilDrop <= 0) {
            insertTopRow()
            shotsUntilDrop = shotsPerDrop
        }
        spawnFromBoard()
        checkLose()
        if (state == State.PLAYING && countBubbles() == 0) levelComplete()
    }

    fun checkLose() {
        for (i in grid.indices) {
            if (yOf(i) + R <= deadlineY) continue
            val vl = validLen(grid[i])
            for (c in 0 until vl) {
                if (cellAt(i, c) != null) { gameOver(); return }
            }
        }
    }

    fun gameOver() {
        if (state != State.PLAYING) return
        state = State.OVER
        sound.play("lose0"); sound.play("lose1")
        commitScores(false)
    }

    fun levelComplete() {
        if (state != State.PLAYING) return
        state = State.COMPLETE
        completeAt = time
        sound.play("win0")
        var stars = 1
        if (score >= popsThisLevel * 14) stars = 2
        if (score >= popsThisLevel * 22) stars = 3

        commitScores(true)

        if (mode == "level") {
            store.level = levelNum + 1
            store.setStars(levelNum, stars)
        }
    }

    fun commitScores(won: Boolean) {
        store.highScore = score
        store.addTotalScore(score)
        val unlocked = themeManager.checkUnlocks(store.totalScore)
        if (unlocked != null) {
            texts.add(FxText(W / 2, H * 0.5f, "${unlocked.name} UNLOCKED!", 2.2f, -0.4f, 22.dp(), true))
        }
        store.gamesPlayed++
        store.totalPops += popsThisLevel
        if (comboStreak > store.bestCombo) store.bestCombo = comboStreak
        if (mode == "daily") {
            store.lastDailyDate = todayString()
            store.dailyHigh = score
        } else if (!won) {
            store.level = levelNum
        }
    }

    fun burst(x: Float, y: Float, color: Int) {
        repeat(7) {
            val a = Math.random() * Math.PI * 2
            val s = (1.5 + Math.random() * 3).toFloat()
            particles.add(Particle(x, y, (cos(a).toFloat()) * s, sin(a).toFloat() * s - 1f,
                1f, color, (2 + Math.random() * 3).toFloat()))
        }
    }

    fun hitTest(px: Float, py: Float): Boolean {
        val lim = 2 * R - 4
        for (i in grid.indices) {
            val vl = validLen(grid[i])
            for (c in 0 until vl) {
                val b = cellAt(i, c) ?: continue
                val dx = px - b.x; val dy = py - b.y
                if (dx * dx + dy * dy < lim * lim) return true
            }
        }
        return false
    }

    fun computePreview() {
        previewPts.clear(); previewGhostI = -1; previewGhostC = -1
        if (state != State.PLAYING || flying != null || current == null) return
        var x = cannonX; var y = cannonY
        val stepLen = R * 0.45f
        var vx = cos(aimAngle).toFloat() * stepLen
        var vy = sin(aimAngle).toFloat() * stepLen
        var steps = 0
        while (steps < 500) {
            x += vx; y += vy
            if (x <= left + R) { x = left + R; vx = -vx }
            else if (x >= right - R) { x = right - R; vx = -vx }
            if (steps % 4 == 0) previewPts.add(floatArrayOf(x, y))
            val hit = y <= top + R || hitTest(x, y)
            if (hit) {
                previewPts.add(floatArrayOf(x, y))
                val cell = findSnapCell(x, y)
                if (cell != null && cell[0] < grid.size) { previewGhostI = cell[0]; previewGhostC = cell[1] }
                break
            }
            steps++
        }
    }

    // ---------------- update ----------------

    fun update(dt: Float) {
        if (state == State.PLAYING) {
            val fly = flying
            if (fly != null) {
                val steps = maxOf(1, (flySpeed * dt / (R * 0.4f)).toInt() + 1)
                val sx = fly.vx * dt / steps
                val sy = fly.vy * dt / steps
                for (s in 0 until steps) {
                    if (flying == null) break
                    fly.x += sx; fly.y += sy
                    if (fly.x <= left + R) { fly.x = left + R; fly.vx = -fly.vx; sound.play("bounce") }
                    else if (fly.x >= right - R) { fly.x = right - R; fly.vx = -fly.vx; sound.play("bounce") }
                    if (fly.y <= top + R || hitTest(fly.x, fly.y)) {
                        flying = null
                        land(fly)
                        break
                    }
                }
            }
            computePreview()
        }

        val it1 = fallingFx.iterator()
        while (it1.hasNext()) {
            val f = it1.next()
            f.vy += 0.42f * dt
            f.x += f.vx * dt; f.y += f.vy * dt
            if (f.y > H + R * 2) it1.remove()
        }

        val it2 = popFx.iterator()
        while (it2.hasNext()) { val p = it2.next(); p.life -= 0.085f * dt; if (p.life <= 0) it2.remove() }

        val it3 = particles.iterator()
        while (it3.hasNext()) {
            val p = it3.next()
            p.vy += 0.16f * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.life -= 0.03f * dt
            if (p.life <= 0) it3.remove()
        }

        val it4 = texts.iterator()
        while (it4.hasNext()) {
            val t = it4.next()
            t.y += t.vy * dt; t.life -= 0.02f * dt
            if (t.life <= 0) it4.remove()
        }

        if (shake > 0) { shake *= Math.pow(0.86, dt.toDouble()).toFloat(); if (shake < 0.15f) shake = 0f }
    }

    // ---------------- render ----------------

    fun render(c: Canvas) {
        currentCanvas = c
        c.save()
        if (shake > 0) {
            c.translate((Math.random() - 0.5).toFloat() * shake, (Math.random() - 0.5).toFloat() * shake)
        }
        c.drawRect(-20f, -20f, W + 20f, H + 20f, pBg)

        when (state) {
            State.MENU -> drawMenu(c)
            State.THEMES -> drawThemesScreen(c)
            State.STATS -> drawStatsScreen(c)
            else -> {
                drawWorld(c)
                when (state) {
                    State.PAUSED -> drawPanelOverlay(c)
                    State.OVER -> drawOver(c)
                    State.COMPLETE -> drawComplete(c)
                    else -> {}
                }
            }
        }
        c.restore()
    }

    fun drawMenuBg(c: Canvas) {
        for (i in 0 until 14) {
            val t = time / 90f + i * 1.7f
            val x = W / 2 + cos(t.toDouble()).toFloat() * W * 0.38f
            val y = H / 2 + sin((t * 0.8 + i).toDouble()).toFloat() * H * 0.3f
            drawBubbleAt(c, x, y, i % themeManager.current.colors.size, 0.9f, 40)
        }
    }

    fun button(id: String, l: Float, t: Float, r: Float, b: Float, label: String,
               style: Int = 0, enabled: Boolean = true): RectF {
        val rect = RectF(l, t, r, b)
        buttons[id] = rect
        val paint = when (style) {
            1 -> pBtnPrimary; 2 -> pBtnAccent; else -> pBtn
        }
        cRound(rect, paint)
        if (!enabled) {
            val dim = Paint().apply { color = Color.argb(120, 10, 12, 24) }
            cRound(rect, dim)
        }
        pBtnText.textAlign = Paint.Align.CENTER
        c.drawText(label, rect.centerX(), rect.centerY() - (pBtnText.descent() + pBtnText.ascent()) / 2, pBtnText)
        return rect
    }

    private fun cRound(rect: RectF, paint: Paint) {
        val canvas = currentCanvas ?: return
        canvas.drawRoundRect(rect, 14.dp(), 14.dp(), paint)
    }

    var currentCanvas: Canvas? = null

    fun fmt(n: Int): String = String.format("%,d", n)

    fun watermark(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 255, 255, 255); textSize = 11.dp(); textAlign = Paint.Align.CENTER }
        c.drawText("Developed by Qeytil", W / 2, H - 10.dp(), p)
    }

    // ---------------- drawing: world ----------------

    fun drawWorld(c: Canvas) {
        val th = themeManager.current

        pWall.alpha = 230
        c.drawRect(left - 8.dp(), top - 10.dp(), left, H, pWall)
        c.drawRect(right, top - 10.dp(), right + 8.dp(), H, pWall)
        c.drawRect(left - 8.dp(), top - 10.dp(), right + 8.dp(), top, pWall)
        pWall.alpha = 255

        val pulse = (0.35 + 0.3 * sin(time / 6.0)).toFloat()
        pLine.alpha = (pulse * 255).toInt()
        c.drawLine(left, deadlineY, right, deadlineY, pLine)

        synchronized(grid) {
            for (i in grid.indices) {
                val vl = validLen(grid[i])
                for (col in 0 until vl) {
                    val b = cellAt(i, col) ?: continue
                    drawBubbleAt(c, b.x, b.y, b.colorIdx)
                }
            }
        }

        for (f in fallingFx) drawBubbleAt(c, f.x, f.y, f.colorIdx, 1f, 242)
        for (p in popFx) drawBubbleAt(c, p.x, p.y, p.colorIdx, 1f + (1f - p.life) * 0.7f, (p.life * 255).toInt())

        for (p in particles) {
            pDot.color = p.color
            pDot.alpha = (p.life * 255).toInt()
            c.drawCircle(p.x, p.y, (p.size * p.life).coerceAtLeast(0.5f), pDot)
        }
        pDot.alpha = 255

        if (state == State.PLAYING && flying == null && current != null) {
            val aimCol = palette.getOrNull(current!!.colorIdx) ?: Color.WHITE
            pGhost.color = aimCol
            pDot.color = aimCol
            previewPts.forEachIndexed { idx, pt ->
                pDot.alpha = ((0.6 - idx * 0.02).coerceAtLeast(0.08) * 255).toInt()
                c.drawCircle(pt[0], pt[1], R * 0.16f, pDot)
            }
            pDot.alpha = 255
            if (previewGhostI >= 0 && previewGhostI < grid.size) {
                pGhost.alpha = 128
                c.drawCircle(xOf(previewGhostI, previewGhostC), yOf(previewGhostI), R * 0.92f, pGhost)
            }
        }

        flying?.let { drawBubbleAt(c, it.x, it.y, it.colorIdx) }

        drawCannon(c)
        drawHud(c)
        drawTexts(c)
    }

    fun drawCannon(c: Canvas) {
        val th = themeManager.current
        c.save()
        c.rotate(Math.toDegrees((aimAngle + Math.PI / 2).toDouble()).toFloat(), cannonX, cannonY)
        cRound(RectF(cannonX - 11.dp(), cannonY - R - 12.dp(), cannonX + 11.dp(), cannonY + R + 14.dp()), Paint().apply { color = th.wall })
        c.restore()

        val hub = Paint(Paint.ANTI_ALIAS_FLAG)
        hub.color = th.wall
        c.drawCircle(cannonX, cannonY, 17.dp(), hub)

        current?.let { drawBubbleAt(c, cannonX, cannonY, it.colorIdx) }

        next?.let {
            val nx = cannonX + 64.dp(); val ny = cannonY + 26.dp()
            drawBubbleAt(c, nx, ny, it.colorIdx, 0.6f, 242)
            pHudSmall.textAlign = Paint.Align.CENTER
            pHudSmall.alpha = 180
            c.drawText("NEXT", nx, ny + R * 0.6f + 13.dp(), pHudSmall)
            pHudSmall.alpha = 255
        }

        val sx = W - 46.dp(); val sy = H - 46.dp()
        val swapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = th.wall }
        c.drawCircle(sx, sy, 27.dp(), swapPaint)
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = th.text; textSize = 22.dp(); textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("\u21C4", sx, sy + 8.dp(), arrow)

        if (state == State.PAUSED || state == State.OVER || state == State.COMPLETE) return
        buttons["pause"] = RectF(W - 56.dp(), 10.dp(), W - 12.dp(), 54.dp())
        val pb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 255, 255, 255) }
        cRound(buttons["pause"]!!, pb)
        val bar = Paint().apply { color = Color.WHITE }
        c.drawRect(W - 44.dp(), 20.dp(), W - 40.dp(), 44.dp(), bar)
        c.drawRect(W - 28.dp(), 20.dp(), W - 24.dp(), 44.dp(), bar)
    }

    fun drawHud(c: Canvas) {
        if (lastHudScore != score) lastHudScore = score
        pHudBig.textAlign = Paint.Align.LEFT
        c.drawText(fmt(score), 16.dp(), 34.dp(), pHudBig)
        pHudSmall.textAlign = Paint.Align.LEFT
        pHudSmall.alpha = 190
        c.drawText(if (mode == "daily") "DAILY" else "LV $levelNum", 16.dp(), 50.dp(), pHudSmall)
        pHudSmall.alpha = 255

        var x = W / 2 - (shotsPerDrop * 13.dp()) / 2
        for (i in 0 until shotsPerDrop) {
            val on = i < shotsUntilDrop
            pDot.color = if (on) Color.rgb(255, 217, 61) else Color.argb(40, 255, 255, 255)
            c.drawCircle(x, 26.dp(), if (on) 4.5f.dp() else 3.5f.dp(), pDot)
            x += 13.dp()
        }

        if (comboStreak > 1) {
            val comboP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 217, 61); textSize = 12.dp(); textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL)
            }
            c.drawText("COMBO x${min(comboStreak, 5)}", W / 2, 48.dp(), comboP)
        }
    }

    fun drawTexts(c: Canvas) {
        for (t in texts) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL)
                if (t.big) typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
                textSize = t.size
                textAlign = Paint.Align.CENTER
                alpha = (t.life.coerceAtMost(1f) * 255).toInt()
                color = if (t.big) Color.rgb(255, 217, 61) else Color.WHITE
            }
            val stroke = Paint(p).apply {
                style = Paint.Style.STROKE; strokeWidth = 4.dp(); color = Color.argb(120, 0, 0, 0)
            }
            c.drawText(t.str, t.x, t.y, stroke)
            c.drawText(t.str, t.x, t.y, p)
        }
    }

    // ---------------- drawing: screens ----------------

    fun drawMenu(c: Canvas) {
        buttons.clear()
        drawMenuBg(c)
        val th = themeManager.current

        val titleP = Paint(pTitle).apply {
            textAlign = Paint.Align.CENTER
        }
        var ty = 130.dp()
        titleP.shader = LinearGradient((W/2 - 140).dp(), ty, (W/2 + 140).dp(), ty,
            Color.rgb(255, 107, 107), Color.rgb(77, 163, 255), Shader.TileMode.CLAMP)
        c.drawText("BUBBLE", W / 2, ty, titleP)
        ty += 52.dp()
        titleP.shader = LinearGradient((W/2 - 120).dp(), ty, (W/2 + 120).dp(), ty,
            Color.rgb(255, 217, 61), Color.rgb(55, 214, 122), Shader.TileMode.CLAMP)
        c.drawText("POP", W / 2, ty, titleP)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = th.text; alpha = 160; textSize = 14.dp(); textAlign = Paint.Align.CENTER
            letterSpacing = 0.35f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("F R E N Z Y", W / 2, ty + 26.dp(), sub)

        val statB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL); textSize = 19.dp()
            color = Color.rgb(255, 217, 61); textAlign = Paint.Align.CENTER }
        val statS = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.dp(); color = th.text; alpha = 150; textAlign = Paint.Align.CENTER; letterSpacing = 0.2f }

        val sy0 = ty + 62.dp()
        val colX = floatArrayOf(W / 2 - 90.dp(), W / 2, W / 2 + 90.dp())
        val labels = arrayOf(fmt(store.highScore), "${store.totalStars()}", fmt(store.totalScore.toInt()))
        val caps = arrayOf("BEST", "STARS", "TOTAL")
        for (i in 0..2) { c.drawText(labels[i], colX[i], sy0, statB); c.drawText(caps[i], colX[i], sy0 + 16.dp(), statS) }

        val bx = 40.dp(); val bw = W - 80.dp(); var by = sy0 + 44.dp()
        button("continue", bx, by, bx + bw, by + 50.dp(), "CONTINUE  LV.${min(store.level, Levels.MAX_LEVEL)}", 1); by += 60.dp()
        button("new", bx, by, bx + bw, by + 44.dp(), "NEW GAME"); by += 54.dp()

        if (dailyAvailable()) {
            button("daily", bx, by, bx + bw, by + 46.dp(), "DAILY CHALLENGE", 2)
        } else {
            pBtnText.alpha = 150
            button("daily_done", bx, by, bx + bw, by + 40.dp(), "DAILY DONE \u2014 SEE YOU TOMORROW")
            pBtnText.alpha = 255
        }
        by += 58.dp()

        button("themes", bx, by, bx + bw / 2 - 6.dp(), by + 42.dp(), "THEMES")
        button("stats", bx + bw / 2 + 6.dp(), by, bx + bw, by + 42.dp(), "STATS"); by += 52.dp()

        button("sound", bx, by, bx + bw, by + 38.dp(), if (store.soundOn) "SOUND ON" else "SOUND OFF")

        watermark(c)
    }

    fun dimScreen(c: Canvas, alpha: Int = 210) {
        val d = Paint().apply { color = Color.argb(alpha, 4, 6, 16) }
        c.drawRect(0f, 0f, W, H, d)
    }

    fun drawPanelOverlay(c: Canvas) {
        dimScreen(c)
        buttons.clear()
        drawWorldBehindPanel()
        val pw = 300.dp(); val ph = 300.dp()
        val pl = W / 2 - pw / 2; val pt = H / 2 - ph / 2
        cRound(RectF(pl, pt, pl + pw, pt + ph), pPanel)
        pHudBig.textAlign = Paint.Align.CENTER
        val tp = Paint(pHudBig).apply { textSize = 21.dp() }
        c.drawText("PAUSED", W / 2, pt + 48.dp(), tp)

        val bx = W / 2 - 110.dp(); val bw = 220.dp()
        var by = pt + 76.dp()
        button("resume", bx, by, bx + bw, by + 46.dp(), "RESUME", 1); by += 56.dp()
        button("prestart", bx, by, bx + bw, by + 42.dp(), "RESTART"); by += 52.dp()
        button("pmenu", bx, by, bx + bw, by + 42.dp(), "MENU")
    }

    fun drawWorldBehindPanel() {
        // world is drawn before overlay already; nothing extra needed
    }

    fun drawOver(c: Canvas) {
        dimScreen(c)
        buttons.clear()
        val pw = 310.dp(); val ph = 330.dp()
        val pl = W / 2 - pw / 2; val pt = H / 2 - ph / 2
        cRound(RectF(pl, pt, pl + pw, pt + ph), pPanel)

        val tp = Paint(pHudBig).apply { textSize = 20.dp(); color = Color.rgb(255, 107, 107) }
        c.drawText("BUBBLES OVERFLOWED!", W / 2, pt + 46.dp(), tp)

        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 46.dp()
            color = Color.rgb(255, 217, 61); textAlign = Paint.Align.CENTER }
        c.drawText(fmt(score), W / 2, pt + 108.dp(), sp)

        val subp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.dp(); color = th_textColor(); textAlign = Paint.Align.CENTER; letterSpacing = 0.15f }
        c.drawText("BEST ${fmt(store.highScore)}", W / 2, pt + 132.dp(), subp)

        val bx = W / 2 - 115.dp(); val bw = 230.dp()
        var by = pt + 158.dp()
        button("again", bx, by, bx + bw, by + 48.dp(), "TRY AGAIN", 1); by += 58.dp()
        button("omenu", bx, by, bx + bw, by + 42.dp(), "MENU")
    }

    fun drawComplete(c: Canvas) {
        dimScreen(c)
        buttons.clear()
        val pw = 320.dp(); val ph = 360.dp()
        val pl = W / 2 - pw / 2; val pt = H / 2 - ph / 2
        cRound(RectF(pl, pt, pl + pw, pt + ph), pPanel)

        val tp = Paint(pHudBig).apply { textSize = 22.dp() }
        val titleTxt = if (mode == "daily") "DAILY COMPLETE!" else "LEVEL $levelNum CLEAR!"
        c.drawText(titleTxt, W / 2, pt + 44.dp(), tp)

        var stars = store.starsFor(levelNum)
        if (mode == "daily") stars = min(3, 1 + score / popsThisLevel.coerceAtLeast(1) / 8)
        val anim = ((time - completeAt) * 3f).coerceIn(0f, 1f)
        val starP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = 42.dp(); textAlign = Paint.Align.CENTER }
        val xs = floatArrayOf(W / 2 - 64.dp(), W / 2, W / 2 + 64.dp())
        for (i in 0..2) {
            val on = i < stars
            val show = when (i) { 0 -> anim >= 0.33f; 1 -> anim >= 0.66f; else -> anim >= 1f }
            starP.color = if (on && show) Color.rgb(255, 217, 61) else Color.argb(90, 44, 51, 87)
            c.drawText("\u2605", xs[i], pt + 118.dp(), starP)
        }

        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 42.dp()
            color = Color.rgb(255, 217, 61); textAlign = Paint.Align.CENTER }
        c.drawText(fmt(score), W / 2, pt + 172.dp(), sp)

        val subp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.dp(); color = th_textColor(); textAlign = Paint.Align.CENTER }
        val subt = if (mode == "daily") "TARGET $dailyTarget" else "+${250 + shotsUntilDrop * 25} CLEAR BONUS"
        c.drawText(subt, W / 2, pt + 196.dp(), subp)

        val bx = W / 2 - 120.dp(); val bw = 240.dp()
        var by = pt + 222.dp()
        if (mode == "level") { button("next", bx, by, bx + bw, by + 48.dp(), "NEXT LEVEL", 1); by += 58.dp() }
        button("cmenu", bx, by, bx + bw, by + 42.dp(), "MENU")
    }

    fun drawThemesScreen(c: Canvas) {
        buttons.clear()
        drawMenuBg(c)
        val tp = Paint(pHudBig).apply { textSize = 24.dp(); textAlign = Paint.Align.CENTER }
        c.drawText("THEMES", W / 2, 70.dp(), tp)

        val total = store.totalScore
        var y = 100.dp()
        val iw = (W - 60.dp())
        Themes.all.forEach { t ->
            val unlocked = store.themeUnlocked(t.key)
            val active = themeManager.current.key == t.key
            val h = 58.dp()
            val rect = RectF(30.dp(), y, 30.dp() + iw, y + h)
            val bgp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (active) Color.argb(60, 55, 214, 122) else Color.argb(20, 255, 255, 255) }
            cRound(rect, bgp)
            if (active) { val b2 = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2.dp(); color = Color.rgb(55, 214, 122) }; cRound(rect, b2) }

            var dx = rect.left + 18.dp()
            t.colors.take(4).forEach { col ->
                pDot.color = col
                c.drawCircle(dx, rect.centerY(), 8.dp(), pDot)
                dx += 22.dp()
            }

            val nameP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL); textSize = 15.dp(); color = Color.WHITE }
            c.drawText(t.name, dx + 8.dp(), rect.centerY() - 4.dp(), nameP)
            val lockP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10.dp(); color = Color.argb(180, 200, 205, 235) }
            c.drawText(if (unlocked) "" else "${fmt(t.unlockScore.toInt())} PTS TO UNLOCK", dx + 8.dp(), rect.centerY() + 13.dp(), lockP)

            val tag = when { active -> "ACTIVE"; unlocked -> "SELECT"; else -> "" }
            val tagP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL); textSize = 11.dp()
                color = if (active) Color.rgb(55, 214, 122) else Color.WHITE; textAlign = Paint.Align.RIGHT }
            c.drawText(tag, rect.right - 14.dp(), rect.centerY() + 4.dp(), tagP)

            if (unlocked && !active) buttons["theme_${t.key}"] = rect
            y += h + 10.dp()
        }

        val bw = 220.dp()
        button("back", W / 2 - bw / 2, H - 74.dp(), W / 2 + bw / 2, H - 30.dp(), "BACK")
    }

    fun drawStatsScreen(c: Canvas) {
        buttons.clear()
        drawMenuBg(c)
        val tp = Paint(pHudBig).apply { textSize = 24.dp(); textAlign = Paint.Align.CENTER }
        c.drawText("STATS", W / 2, 70.dp(), tp)

        val rows = listOf(
            "Games played" to "${store.gamesPlayed}",
            "Bubbles popped" to fmt(store.totalPops),
            "Best combo" to (if (store.bestCombo > 0) "${store.bestCombo}x" else "-"),
            "High score" to fmt(store.highScore),
            "Total score" to fmt(store.totalScore.toInt()),
            "Stars earned" to "${store.totalStars()} / ${Levels.MAX_LEVEL * 3}",
            "Daily best" to fmt(store.dailyHigh),
            "Current level" to "${store.level}"
        )
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14.dp(); color = Color.WHITE }
        val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL); textSize = 15.dp()
            color = Color.rgb(255, 217, 61); textAlign = Paint.Align.RIGHT }
        var y = 104.dp()
        rows.forEach { row ->
            val rect = RectF(30.dp(), y, W - 30.dp(), y + 40.dp())
            cRound(rect, Paint().apply { color = Color.argb(18, 255, 255, 255) })
            c.drawText(row.first, rect.left + 16.dp(), rect.centerY() + 5.dp(), lp)
            c.drawText(row.second, rect.right - 16.dp(), rect.centerY() + 5.dp(), vp)
            y += 48.dp()
        }

        val bw = 220.dp()
        button("back", W / 2 - bw / 2, H - 74.dp(), W / 2 + bw / 2, H - 30.dp(), "BACK")
    }

    fun th_textColor(): Int = themeManager.current.text

}
