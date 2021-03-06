package io.data2viz.geom

data class Point(val x: Double = 0.0, val y: Double = 0.0) {

    companion object {
        val origin = Point()
    }
    operator fun plus(vector: Vector) = Point(x + vector.vx, y + vector.vy)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun div(value:Number) = Point(x / value.toDouble(), y / value.toDouble())
    operator fun times(value:Number) = Point(x * value.toDouble(), y * value.toDouble())
}