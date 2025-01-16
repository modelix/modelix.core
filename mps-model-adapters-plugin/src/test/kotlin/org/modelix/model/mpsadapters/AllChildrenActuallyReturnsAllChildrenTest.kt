package org.modelix.model.mpsadapters

import org.modelix.model.api.INode

class AllChildrenActuallyReturnsAllChildrenTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test repository adapter consistency`() {
        readAction {
            checkAdapterConsistence(MPSRepositoryAsNode(mpsProject.repository))
        }
    }

    fun `test module adapter consistency`() {
        readAction {
            for (module in mpsProject.repository.modules.map { MPSModuleAsNode(it).asLegacyNode() }) {
                checkAdapterConsistence(module)
            }
        }
    }

    fun `test model adapter consistency`() {
        readAction {
            for (model in mpsProject.repository.modules.flatMap { it.models }.map { MPSModelAsNode(it).asLegacyNode() }) {
                checkAdapterConsistence(model)
            }
        }
    }

    private fun checkAdapterConsistence(adapter: INode) {
        val concept = checkNotNull(adapter.concept)
        val expected = concept.getAllChildLinks().flatMap { adapter.getChildren(it) }.toSet()
        val actual = adapter.allChildren.toSet()
        assertEquals(expected, actual)
    }
}
