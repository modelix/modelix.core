package org.modelix.model

import org.modelix.model.api.ITree

interface IVersion {
    fun getContentHash(): String
    fun getTree(): ITree
}
