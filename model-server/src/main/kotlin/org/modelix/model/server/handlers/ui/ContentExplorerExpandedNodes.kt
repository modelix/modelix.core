package org.modelix.model.server.handlers.ui

import kotlinx.serialization.Serializable

@Serializable
data class ContentExplorerExpandedNodes(val expandedNodeIds: Set<String>, val expandAll: Boolean)
