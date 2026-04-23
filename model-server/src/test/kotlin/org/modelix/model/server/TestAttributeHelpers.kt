package org.modelix.model.server

internal fun mergeAttributes(
    accumulated: Map<String, Set<String>>,
    incoming: Map<String, String>,
): Map<String, Set<String>> =
    (accumulated.keys + incoming.keys).associateWith { key ->
        accumulated[key].orEmpty() + setOfNotNull(incoming[key])
    }
