package org.modelix.model.mpsadapters

import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.smodel.Generator
import jetbrains.mps.util.MacroHelper
import jetbrains.mps.util.MacrosFactory
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.Memento
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSJavaModuleFacetAsNode(val facet: JavaModuleFacet) : MPSGenericNodeAdapter<JavaModuleFacet>() {

    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<JavaModuleFacet>>>(
            BuiltinLanguages.MPSRepositoryConcepts.JavaModuleFacet.generated.toReference() to object : IPropertyAccessor<JavaModuleFacet> {
                override fun read(element: JavaModuleFacet): String? {
                    // Should always be true
                    // https://github.com/JetBrains/MPS/blob/2820965ff7b8836ed1d14adaf1bde29744c88147/core/project/source/jetbrains/mps/project/facets/JavaModuleFacetImpl.java
                    return true.toString()
                }

                override fun write(element: JavaModuleFacet, value: String?) {
                    if (value != "true") throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.JavaModuleFacet.path.toReference() to object : IPropertyAccessor<JavaModuleFacet> {
                override fun read(facet: JavaModuleFacet): String? {
                    // return facet.classesGen?.let { facet.macroHelper().shrinkPath(it.path) }
                    return null
                }

                override fun write(element: JavaModuleFacet, value: String?) {
//                    val memento = MementoImpl()
//                    element.save(memento)
//                    memento.getOrCreateChild("classes").let {
//                        it.put("generated", "true")
//                        it.put("path", value.also { println("${element.module} / path = $it") })
//                    }
//                    element.load(memento)
                }

                private fun JavaModuleFacet.macroHelper(): MacroHelper {
                    return module
                        .let { if (it is Generator) it.sourceLanguage().resolveSourceModule() else it }
                        .let { MacrosFactory().module(it) }
                }
            },
        )
        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<JavaModuleFacet>>>()
        private val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<JavaModuleFacet>>>()
    }

    override fun getElement(): JavaModuleFacet = facet

    override fun getRepository(): SRepository? = null

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

    override fun getParent(): IWritableNode? {
        return facet.module?.let { MPSModuleAsNode(it) }
    }

    override fun getNodeReference(): INodeReference {
        val module = checkNotNull(facet.module) { "Module of facet $facet not found" }
        return MPSJavaModuleFacetReference(module.moduleReference)
    }

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.JavaModuleFacet

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.facets.toReference()
    }
}

private fun Memento.getOrCreateChild(name: String) = getChild(name) ?: createChild(name)
