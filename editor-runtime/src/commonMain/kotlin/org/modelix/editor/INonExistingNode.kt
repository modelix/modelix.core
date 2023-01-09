/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.editor

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getChildren
import org.modelix.model.api.getContainmentLink
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRoot
import org.modelix.model.api.index
import org.modelix.model.api.isInstanceOf
import org.modelix.model.api.isInstanceOfSafe
import org.modelix.model.api.isSubConceptOf
import org.modelix.model.api.remove

interface INonExistingNode {
    fun getParent(): INonExistingNode?
    fun replaceNode(subConcept: IConcept?): INode
    fun getOrCreateNode(subConcept: IConcept?): INode
    fun expectedConcept(): IConcept?
    fun getVisibleReferenceTargets(link: IReferenceLink): List<INode>
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

    override fun getVisibleReferenceTargets(link: IReferenceLink): List<INode> {
        return node.getVisibleReferenceTargets(link)
    }
}

fun INonExistingNode.ofSubConcept(subConcept: IConcept): INonExistingNode {
    return if (expectedConcept().isSubConceptOf(subConcept)) {
        this
    } else {
        SpecializedNonExistingNode(this, subConcept)
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
        return if (node.isInstanceOf(outputConcept)) {
            node
        } else {
            replaceNode(subConcept)
        }
    }

    override fun expectedConcept(): IConcept? {
        return node.getContainmentLink()?.targetConcept
    }

    override fun getVisibleReferenceTargets(link: IReferenceLink): List<INode> {
        val targetConcept = link.targetConcept
        return node.getRoot().getDescendants(true).filter { it.isInstanceOfSafe(targetConcept) }.toList()
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

    override fun getVisibleReferenceTargets(link: IReferenceLink): List<INode> {
        return parent.getVisibleReferenceTargets(link)
    }
}