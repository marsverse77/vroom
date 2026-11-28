package com.vroombuddies.game.render

import kotlin.math.abs

/** Two-tone printed graphics projected onto each painted door. Dimensions are in model metres. */
internal enum class RaceLivery(rgb: Int, val spokes: Int, val stencil: List<String>) {
    Sprint(0xffbe27, 5, listOf("00111", "01110", "11100", "00110", "01100", "11000")),
    Pearl(0x54e3ec, 3, listOf("01110", "11011", "10001", "10001", "11011", "01110")),
    Summit(0xff783d, 8, listOf("00100", "01110", "11011", "10001", "10101", "11111")),
    Comet(0x92adff, 7, listOf("00011", "00111", "01110", "11100", "11000", "10000")),
    Courier(0x42eaba, 4, listOf("00010", "00111", "11111", "11111", "00111", "00010")),
    Rally(0xffff49, 6, listOf("10101", "01010", "10101", "01010", "10101", "01010")),
    Honey(0xffac42, 9, listOf("01110", "10001", "10101", "10101", "10001", "01110"));

    val color = Rgba.hex(rgb)

    fun apply(parts: CarParts, height: Float) {
        val body = parts.paint
        val ink = parts.trim
        val dark = Rgba.hex(0x172840)
        val tile = if (this == Comet || this == Rally) 0.072f else 0.079f
        // Find the outer painted surface at each artwork corner, including rounded doors.
        fun surface(y: Float, z: Float): Float {
            var x = 0f
            val pos = body.pos
            for (index in 0 until body.iCount step 3) {
                val a = body.idx[index] * 3
                val b = body.idx[index + 1] * 3
                val c = body.idx[index + 2] * 3
                val by = pos[b + 1] - pos[a + 1]
                val bz = pos[b + 2] - pos[a + 2]
                val cy = pos[c + 1] - pos[a + 1]
                val cz = pos[c + 2] - pos[a + 2]
                val area = by * cz - bz * cy
                if (abs(area) < 1e-7f) continue
                val dy = y - pos[a + 1]
                val dz = z - pos[a + 2]
                val u = (dy * cz - dz * cy) / area
                val v = (by * dz - bz * dy) / area
                if (u >= -0.0001f && v >= -0.0001f && u + v <= 1.0001f)
                    x = maxOf(x, pos[a] + u * (pos[b] - pos[a]) + v * (pos[c] - pos[a]))
            }
            return x
        }
        val samples = HashMap<Pair<Float, Float>, Float>()
        val rows = stencil.size
        val cols = stencil.first().length
        // One tessellated plate per door, with a swept outline and a trailing speed stripe.
        for (row in -1..rows) for (col in -4..cols) {
            val mark = row in 0 until rows && col in 0 until cols && stencil[row][col] == '1'
            if (col < -1 && row != 2 && row != 4) continue
            val fill = when {
                mark -> Rgba.WHITE
                col < -1 || row == rows || row == -1 -> color
                else -> dark
            }
            for (side in listOf(-1f, 1f)) {
                fun vertex(r: Int, k: Int): Int {
                    val y = height + (rows / 2f - r) * tile
                    val z = (k - cols / 2f + (rows / 2f - r) * 0.18f) * tile * 1.65f
                    return ink.vertex(side * (samples.getOrPut(y to z) { surface(y, z) } + 0.023f), y, z, side, 0f, 0f, fill)
                }
                val a = vertex(row, col)
                val b = vertex(row, col + 1)
                val c = vertex(row + 1, col + 1)
                val d = vertex(row + 1, col)
                ink.quadFacing(a, b, c, d)
            }
        }
    }
}
