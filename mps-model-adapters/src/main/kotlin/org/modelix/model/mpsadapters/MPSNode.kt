package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SNode

fun MPSNode(node: SNode) = MPSWritableNode(node).asLegacyNode()
