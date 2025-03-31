package org.modelix.model.async

class ContainmentCycleException(val newParentId: Any?, val childId: Any?) :
    RuntimeException(
        "$newParentId is a descendant of $childId." +
            " Moving the node would create a cycle in the containment hierarchy.",
    )
