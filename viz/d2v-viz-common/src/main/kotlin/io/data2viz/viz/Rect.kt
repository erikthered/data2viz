package io.data2viz.viz

import io.data2viz.color.Color
import io.data2viz.color.colors


class Rect : Node {
    var x: Double = 10.0
    var y: Double = 10.0
    var width: Double = 10.0
    var height: Double = 10.0
    var fill: Color? = null
    var stroke: Color? = null
    var strokeWidth: Double? = 1.0
}