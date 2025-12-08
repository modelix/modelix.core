package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReferenceConverter

data class MPSJavaModuleFacetReference(val moduleReference: MPSModuleReference) : INodeReference() {

    companion object : NodeReferenceConverter<MPSJavaModuleFacetReference> {
        const val PREFIX = "mps-java-facet"
        const val PREFIX_COLON = "$PREFIX:"

        override fun tryConvert(ref: INodeReference): MPSJavaModuleFacetReference? {
            if (ref is MPSJavaModuleFacetReference) return ref

            val serialized = ref.serialize()
            if (!serialized.startsWith(PREFIX_COLON)) return null

            return MPSJavaModuleFacetReference(MPSModuleReference.parseSModuleReference(serialized.substringAfter(PREFIX_COLON)))
        }
    }

    override fun serialize(): String {
        return "$PREFIX:${moduleReference.toMPSString()}"
    }
}
