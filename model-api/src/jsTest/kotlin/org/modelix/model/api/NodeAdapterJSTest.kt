package org.modelix.model.api

import org.modelix.model.ModelFacade
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeAdapterJSTest {
    @Test
    fun nodesCanBeRemoved() {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val node = branch.computeWrite {
            branch.getRootNode().addNewChild("roleOfTheChildThatGetsRemoved")
        }
        val jsNode = NodeAdapterJS(node)

        jsNode.remove()

        branch.computeRead {
            assertEquals(0, branch.getRootNode().allChildren.count())
        }
    }
}
