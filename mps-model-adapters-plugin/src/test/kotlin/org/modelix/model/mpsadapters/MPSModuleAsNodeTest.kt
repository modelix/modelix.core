package org.modelix.model.mpsadapters

import org.modelix.model.api.INode
import org.modelix.model.api.NodeReference

class MPSModuleAsNodeTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test resolve language dependency from reference`() {
        val repositoryNode: INode = mpsProject.repository.asLegacyNode()
        val languageDependencyNodeReference = NodeReference(
            "mps-lang:f3061a53-9226-4cc5-a443-f952ceaf5816#IN#mps-module:6517ba0d-f632-49c5-a166-401587c2c3ca",
        )

        val resolvedLanguageDependency = readAction {
            repositoryNode.getArea().resolveNode(languageDependencyNodeReference)
        }

        assertNotNull(resolvedLanguageDependency)
        assertEquals(languageDependencyNodeReference.serialize(), resolvedLanguageDependency!!.reference.serialize())
    }
}
