package org.modelix.editor

import kotlinx.browser.document
import org.w3c.dom.DOMRect
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.events.MouseEvent

data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun maxX() = x + width
    fun maxY() = y + height
    fun minX() = x
    fun minY() = y
}

fun HTMLElement.getAbsoluteBounds(): Bounds {
    return getBoundingClientRect().toBounds()
}
fun HTMLElement.getAbsoluteInnerBounds(): Bounds {
    return (getClientRects().asSequence().firstOrNull()?.toBounds() ?: ZERO_BOUNDS)
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

private fun getBodyAbsoluteBounds() = document.body?.getBoundingClientRect()?.toBounds() ?: ZERO_BOUNDS
fun MouseEvent.getAbsolutePositionX() = clientX - getBodyAbsoluteBounds().x
fun MouseEvent.getAbsolutePositionY() = clientY - getBodyAbsoluteBounds().y

fun Node.descendants(): Sequence<Node> = childNodes.asList().asSequence().flatMap { sequenceOf(it) + it.descendants() }

val ZERO_BOUNDS = Bounds(0.0, 0.0, 0.0, 0.0)