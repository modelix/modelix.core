package org.modelix.model.async

import org.modelix.streams.plus

class ContainmentCycleException(val newParentId: Long, val childId: Long) :
    RuntimeException(
        "${newParentId.toString(16)} is a descendant of ${childId.toString(16)}." +
            " Moving the node would create a cycle in the containment hierarchy.",
    )
