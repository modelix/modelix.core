package org.modelix.editor

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.DOMRect
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.events.MouseEvent
import kotlin.math.max
import kotlin.math.min

data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun maxX() = x + width
    fun maxY() = y + height
    fun minX() = x
    fun minY() = y
}

fun HTMLElement.getAbsoluteBounds(): Bounds {
    return getBoundingClientRect().toBounds().translated(window.scrollX, window.scrollY)
}

fun HTMLElement.setBounds(bounds: Bounds) {
    with(style) {
        left = "${bounds.x}px"
        top = "${bounds.y}px"
        width = "${bounds.width}px"
        height = "${bounds.height}px"
    }
}

fun HTMLElement.getAbsoluteInnerBounds(): Bounds {
    return (getClientRects().asSequence().firstOrNull()?.toBounds()?.translated(window.scrollX, window.scrollY) ?: ZERO_BOUNDS)
}

fun DOMRect.toBounds() = Bounds(x, y, width, height)

fun Bounds.relativeTo(origin: Bounds): Bounds {
    return Bounds(
        x - origin.x,
        y - origin.y,
        width,
        height
    )
}

fun Bounds?.union(other: Bounds?): Bounds? {
    return if (this == null) other else union(other)
}

fun Bounds.union(other: Bounds?): Bounds {
    if (other == null) return this
    val minX = min(minX(), other.minX())
    val maxX = max(maxX(), other.maxX())
    val minY = min(minY(), other.minY())
    val maxY = max(maxY(), other.maxY())
    return Bounds(minX, minY, maxX - minX, maxY - minY)
}

fun Bounds.translated(deltaX: Double, deltaY: Double) = copy(x = x + deltaX, y = y + deltaY)
fun Bounds.expanded(delta: Double) = copy(
    x = x - delta,
    y = y - delta,
    width = width + delta * 2.0,
    height = height + delta * 2.0
)

private fun getBodyAbsoluteBounds() = document.body?.getBoundingClientRect()?.toBounds() ?: ZERO_BOUNDS
fun MouseEvent.getAbsolutePositionX() = clientX - getBodyAbsoluteBounds().x
fun MouseEvent.getAbsolutePositionY() = clientY - getBodyAbsoluteBounds().y

fun Node.descendants(): Sequence<Node> = childNodes.asList().asSequence().flatMap { sequenceOf(it) + it.descendants() }

val ZERO_BOUNDS = Bounds(0.0, 0.0, 0.0, 0.0)