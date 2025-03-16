package org.modelix.model.api

import org.modelix.model.ModelFacade
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.client.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals

class TreePointerTest {

    @Test
    fun references_can_be_resolved() {
        val branch = TreePointer(ModelFacade.newLocalTree(useRoleIds = false), IdGenerator.newInstance(1))
        val rootNode = branch.getRootNode()
        val role = IReferenceLinkReference.fromName("refA").toLegacy()
        rootNode.setReferenceTarget(role, rootNode)
        assertEquals(rootNode, rootNode.getReferenceTarget(role))
    }

    @Test
    fun references_can_be_resolved_async() {
        val branch = TreePointer(ModelFacade.newLocalTree(useRoleIds = false), IdGenerator.newInstance(1))
        val rootNode = branch.getRootNode()
        val role = IReferenceLinkReference.fromName("refA")
        rootNode.setReferenceTarget(role.toLegacy(), rootNode)
        assertEquals(rootNode, rootNode.asAsyncNode().let { n -> n.getStreamExecutor().query { n.getReferenceTarget(role).orNull() } }?.asRegularNode())
    }
}
