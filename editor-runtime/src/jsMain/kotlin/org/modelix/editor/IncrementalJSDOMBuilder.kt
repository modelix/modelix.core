package org.modelix.editor

import kotlinx.html.*
import kotlinx.html.org.w3c.dom.events.Event
import org.w3c.dom.*

var IProducesHtml.generatedHtml: HTMLElement?
    get() = asDynamic().generatedHtml
    set(value) { asDynamic().generatedHtml = value }

var HTMLElement.generatedBy: IProducesHtml?
    get() = asDynamic().generatedBy
    set(value) { asDynamic().generatedBy = value }

private class ReusableChildren {
    val reusableChildren: MutableList<Node>
    constructor(children: Iterable<Node>) {
        reusableChildren = children.toMutableList()
    }
    constructor(parent: HTMLElement) {
        reusableChildren = parent.childNodes.asList()
            //.filter { it.generatedBy == null }
            .toMutableList()
    }
    fun processStillUsed(childProducers: List<IProducesHtml>) {
        val stillUsedElements: HashSet<HTMLElement> = childProducers.mapNotNull { it.generatedHtml }.toHashSet()
        reusableChildren.removeAll(stillUsedElements)
        reusableChildren.filterIsInstance<HTMLElement>().forEach { it.generatedBy = null }
    }
    fun findReusable(tag: Tag): HTMLElement? {
        // TODO only reuse those where the element in .generatedBy was removed/replaced (this is only known after generating all children)
        val foundIndex = reusableChildren.indexOfFirst { it is HTMLElement && it.generatedBy == null && it.tagName.lowercase() == tag.tagName.lowercase() }
        return if (foundIndex >= 0) {
            reusableChildren.removeAt(foundIndex) as HTMLElement
        } else {
            null
        }
    }
    fun findReusabeTextNode(text: String): Text? {
        val foundIndex = reusableChildren.indexOfFirst { it is Text && it.textContent == text }
        return if (foundIndex >= 0) {
            reusableChildren.removeAt(foundIndex) as Text
        } else {
            null
        }
    }
}

class NodeOrProducer(val producer: IProducesHtml?, val node: Node?) {
    companion object {
        fun producer(producer: IProducesHtml) = NodeOrProducer(producer, null)
        fun node(node: Node) = NodeOrProducer(null, node)
    }
}

class IncrementalJSDOMBuilder(val document: Document, existingRootElement: HTMLElement?) : IIncrementalTagConsumer<HTMLElement> {
    private inner class StackFrame {
        var reusableChildren: ReusableChildren? = null
        var resultingHtml: HTMLElement? = null
        var tag: Tag? = null
        val generatedChildren: MutableList<NodeOrProducer> = ArrayList()

        fun close() {
            if (resultingHtml != null) applyAttributes()
            applyChildren()
        }

        fun applyAttributes() {
            val attributesToRemove = resultingHtml!!.getAttributeNames().toMutableSet()
            tag!!.attributesEntries.forEach {
                if (resultingHtml!!.getAttribute(it.key) != it.value) {
                    resultingHtml!!.setAttribute(it.key, it.value)
                }
                attributesToRemove.remove(it.key)
            }
            attributesToRemove.forEach { resultingHtml!!.removeAttribute(it) }
        }

        fun applyChildren() {
            val parent: HTMLElement? = resultingHtml
            reusableChildren?.processStillUsed(generatedChildren.mapNotNull { it.producer })
            val generatedNodes = ArrayList(generatedChildren).map {
                it.node ?: runProducer(it.producer!!)
            }

            if (parent != null) {
                parent.childNodes.asList().minus(generatedNodes.toSet()).forEach { parent.removeChild(it) }
                generatedNodes.forEachIndexed { index, expectedChild ->
                    if (index < parent.childNodes.length) {
                        val actualChild = parent.childNodes.get(index)
                        if (actualChild != expectedChild) {
                            parent.insertBefore(expectedChild, actualChild)
                        }
                    } else {
                        parent.appendChild(expectedChild)
                    }

                }
            }
        }
    }

    private var lastClosed : StackFrame? = null
    private val stack = arrayListOf(StackFrame().also { it.reusableChildren = ReusableChildren(listOfNotNull(existingRootElement)) })
    private val childHandler: (IProducesHtml)->Unit = { produce(it) }

    override fun produce(producer: IProducesHtml): ()->HTMLElement {
        stack.last().generatedChildren.add(NodeOrProducer.producer(producer))
        if (stack.size == 1) {
            stack.last().close()
            stack.clear()
            stack.add(StackFrame())
        }
        return { producer.generatedHtml ?: throw IllegalStateException("No HTML generated yet") }
    }

    private fun runProducer(producer: IProducesHtml): HTMLElement {
        var childHtml = producer.generatedHtml
        if (childHtml == null) {
            producer.toHtml(this@IncrementalJSDOMBuilder, childHandler)
            childHtml = finalize()
            childHtml.generatedBy = producer
            producer.generatedHtml = childHtml
        }
        return childHtml
    }

    override fun onTagStart(tag: Tag) {
        val parentFrame = stack.last()
        val frame = StackFrame()
        stack.add(frame)
        frame.tag = tag

        val reusable: HTMLElement? = parentFrame.reusableChildren?.findReusable(tag)
        if (reusable != null) {
            frame.reusableChildren = ReusableChildren(reusable)
        }

        val element: HTMLElement = reusable ?: when {
            tag.namespace != null -> document.createElementNS(tag.namespace!!, tag.tagName) as HTMLElement
            else -> document.createElement(tag.tagName) as HTMLElement
        }

        frame.resultingHtml = element
        parentFrame.generatedChildren.add(NodeOrProducer.node(element))
    }

    override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
        // handled in StackFrame.applyAttributes
    }

    override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
        throw UnsupportedOperationException("Use DelayedConsumer")
    }

    override fun onTagEnd(tag: Tag) {
        val frame = stack.last()
        frame.close()
        lastClosed = frame
        if (stack.last() !== frame) throw RuntimeException("$frame !== ${stack.last()}")
        stack.remove(frame)
    }

    override fun onTagContent(content: CharSequence) {
        val frame = stack.lastOrNull() ?: throw IllegalStateException("No current DOM node")
        val reusable: Text? = frame.reusableChildren?.findReusabeTextNode(content.toString())

        val element = reusable ?: document.createTextNode(content.toString())
        frame.generatedChildren.add(NodeOrProducer.node(element))
    }

    override fun onTagContentEntity(entity: Entities) {
        throw UnsupportedOperationException()
    }

    override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
        throw UnsupportedOperationException()
    }


    override fun onTagComment(content: CharSequence) {
        throw UnsupportedOperationException()
    }

    override fun finalize(): HTMLElement = lastClosed!!.resultingHtml!!
}