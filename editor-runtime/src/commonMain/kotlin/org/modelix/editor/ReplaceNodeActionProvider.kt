package org.modelix.editor

import org.modelix.model.api.getAllSubConcepts

data class ReplaceNodeActionProvider(val location: INonExistingNode) : ICodeCompletionActionProvider {
    override fun getApplicableActions(parameters: CodeCompletionParameters): List<IActionOrProvider> {
        val engine = parameters.editor.engine ?: return emptyList()
        val expectedConcept = location.expectedConcept() ?: return emptyList()
        val allowedConcepts = expectedConcept.getAllSubConcepts(true).filterNot { it.isAbstract() }
        val cellModels = allowedConcepts.map { concept ->
            engine.createCellModel(concept)
        }
        return cellModels.flatMap {
            it.getInstantiationActions(location, parameters) ?: emptyList()
        }
    }
}
