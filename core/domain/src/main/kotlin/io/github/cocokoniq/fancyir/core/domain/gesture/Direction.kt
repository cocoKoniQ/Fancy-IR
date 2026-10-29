package io.github.cocokoniq.fancyir.core.domain.gesture

import kotlin.math.abs

/** Screen directions; y grows downward as in every Android coordinate space. */
enum class Direction {
    UP, RIGHT, DOWN, LEFT;

    companion object {
        fun of(dx: Float, dy: Float): Direction = when {
            abs(dx) > abs(dy) -> if (dx > 0) RIGHT else LEFT
            else -> if (dy < 0) UP else DOWN
        }
    }
}
