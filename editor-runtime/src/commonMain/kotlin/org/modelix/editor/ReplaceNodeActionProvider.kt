package org.modelix.editor

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getAllSubConcepts
import org.modelix.model.api.getChildren
import org.modelix.model.api.getContainmentLink
import org.modelix.model.api.index
import org.modelix.model.api.remove

data class ReplaceNodeActionProvider(val location: INonExistingNode) : ICodeCompletionActionProvider {
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
