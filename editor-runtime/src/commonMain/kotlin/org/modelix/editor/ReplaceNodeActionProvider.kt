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

interface INonExistingNode {
    fun getParent(): INonExistingNode?
    fun replaceNode(subConcept: IConcept?): INode
    fun getOrCreateNode(subConcept: IConcept?): INode
    fun expectedConcept(): IConcept?
}

data class SpecializedNonExistingNode(val node: INonExistingNode, val subConcept: IConcept): INonExistingNode {
    override fun getParent(): INonExistingNode? {
        TODO("Not yet implemented")
    }

    override fun replaceNode(subConcept: IConcept?): INode {
        return node.replaceNode(coerceOutputConcept(subConcept))
    }

    override fun getOrCreateNode(subConcept: IConcept?): INode {
        return node.getOrCreateNode(coerceOutputConcept(subConcept))
    }

    override fun expectedConcept(): IConcept {
        return subConcept
    }
}

fun INonExistingNode.coerceOutputConcept(subConcept: IConcept?): IConcept? {
    val expectedConcept = expectedConcept()
    return if (subConcept != null) {
        require(subConcept.isSubConceptOf(expectedConcept)) {
            "$subConcept is not a sub-concept of $expectedConcept"
        }
        subConcept
    } else {
        expectedConcept
    }
}

data class ExistingNode(val node: INode) : INonExistingNode {
    override fun getParent(): INonExistingNode? = node.parent?.let { ExistingNode(it) }

    override fun replaceNode(subConcept: IConcept?): INode {
        val parent = node.parent ?: throw RuntimeException("cannot replace the root node")
        val newNode = parent.addNewChild(node.roleInParent, node.index(), coerceOutputConcept(subConcept))
        node.remove()
        return newNode
    }

    override fun getOrCreateNode(subConcept: IConcept?): INode {
        val outputConcept = coerceOutputConcept(subConcept)
        return if (node.concept?.isSubConceptOf(outputConcept) == true) {
            node
        } else {
            replaceNode(subConcept)
        }
    }

    override fun expectedConcept(): IConcept? {
        return node.getContainmentLink()?.targetConcept
    }
}

fun INode.toNonExisting() = ExistingNode(this)

data class NonExistingChild(private val parent: INonExistingNode, val link: IChildLink, val index: Int = 0) : INonExistingNode {
    override fun getParent() = parent

    override fun replaceNode(subConcept: IConcept?): INode {
        val parentNode = parent.getOrCreateNode(null)
        val existing = parentNode.getChildren(link).toList().getOrNull(index)
        val newNode = parentNode.addNewChild(link, index, coerceOutputConcept(subConcept))
        existing?.remove()
        return newNode
    }

    override fun getOrCreateNode(subConcept: IConcept?): INode {
        return replaceNode(subConcept)
    }

    override fun expectedConcept(): IConcept {
        return link.targetConcept
    }
}