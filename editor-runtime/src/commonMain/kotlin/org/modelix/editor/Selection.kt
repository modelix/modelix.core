package org.modelix.editor

import kotlinx.html.TagConsumer

abstract class Selection {
    abstract fun toHtml(tagConsumer: TagConsumer<*>)
}