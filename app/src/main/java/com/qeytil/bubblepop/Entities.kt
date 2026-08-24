package com.qeytil.bubblepop

class Bubble(var colorIdx: Int) {
    var x = 0f
    var y = 0f
}

class Row(val indent: Boolean, val cells: Array<Bubble?>)

class Fly(var x: Float, var y: Float, var vx: Float, var vy: Float, val colorIdx: Int)

class FallingFx(var x: Float, var y: Float, var vx: Float, var vy: Float, val colorIdx: Int)

class PopFx(val x: Float, val y: Float, val colorIdx: Int, var life: Float = 1f)

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
               var life: Float, val color: Int, val size: Float)

class FxText(var x: Float, var y: Float, val str: String, var life: Float,
             val vy: Float, val size: Float, val big: Boolean)
