package org.modelix.editor

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.getAllSubConcepts
import org.modelix.model.api.getContainmentLink
import org.modelix.model.api.index
import org.modelix.model.api.remove

data class ReplaceNodeActionProvider(val location: INodeLocation) : ICodeCompletionActionProvider {
    override fun getActions(parameters: CodeCompletionParameters): List<ICodeCompletionAction> {
        val engine = parameters.editor.engine ?: return emptyList()
        val expectedConcept = location.expectedConcept() ?: return emptyList()
        val allowedConcepts = expectedConcept.getAllSubConcepts(true).filterNot { it.isAbstract() }
        val cellModels = allowedConcepts.map { concept ->
            engine.createCellModel(concept)
        }
        return cellModels.flatMap {
            it.getInstantiationActions(location) ?: emptyList()
        }
    }
}

interface INodeLocation {
    fun createNode(subConcept: IConcept): INode
    fun expectedConcept(): IConcept?
}

data class LocationOfExistingNode(val node: INode) : INodeLocation {
    override fun createNode(subConcept: IConcept): INode {
        val parent = node.parent ?: throw RuntimeException("cannot replace the root node")
        val newNode = parent.addNewChild(node.roleInParent, node.index(), subConcept)
        node.remove()
        return newNode
    }

    override fun expectedConcept(): IConcept? {
        return node.getContainmentLink()?.targetConcept
    }
}