package org.modelix.mps.sync.binding

import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SNodeChangeListener

class ModuleBinding : Binding {

    var nodeChangedListener = object : SNodeChangeListener {
        override fun propertyChanged(event: SPropertyChangeEvent) {
            TODO("Not yet implemented")
        }

        override fun referenceChanged(event: SReferenceChangeEvent) {
            TODO("Not yet implemented")
        }

        override fun nodeAdded(event: SNodeAddEvent) {
            TODO("Not yet implemented")
        }

        override fun nodeRemoved(event: SNodeRemoveEvent) {
            TODO("Not yet implemented")
        }
    }

    override fun deactivate() {
        TODO("Not yet implemented")
    }
}
