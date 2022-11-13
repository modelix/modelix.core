package org.modelix.editor

import kotlinx.browser.document
import org.w3c.dom.DOMRect
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.asList
import org.w3c.dom.events.MouseEvent

fun HTMLElement.getAbsoluteBounds(): DOMRect {
    return getBoundingClientRect().relativeTo(getBodyAbsoluteBounds())
}

fun DOMRect.relativeTo(origin: DOMRect): DOMRect {
    return DOMRect(
        x - origin.x,
        y - origin.y,
        width,
        height
    )
}

private fun getBodyAbsoluteBounds() = document.body?.getBoundingClientRect() ?: ZERO_BOUNDS
fun MouseEvent.getAbsolutePositionX() = clientX - getBodyAbsoluteBounds().x
fun MouseEvent.getAbsolutePositionY() = clientY - getBodyAbsoluteBounds().y

fun Node.descendants(): Sequence<Node> = childNodes.asList().asSequence().flatMap { sequenceOf(it) + it.descendants() }

val ZERO_BOUNDS = DOMRect(0.0, 0.0, 0.0, 0.0)