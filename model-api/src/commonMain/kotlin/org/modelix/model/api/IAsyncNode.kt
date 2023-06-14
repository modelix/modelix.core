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
package org.modelix.model.api

interface IAsyncNode : INode {
    fun visitContainmentLink(visitor: IVisitor<IChildLink?>)

    fun visitChildren(link: IChildLink, visitor: IVisitor<INode>)
    fun visitAllChildren(visitor: IVisitor<INode>)

    fun visitReferenceTarget(link: IReferenceLink, visitor: IVisitor<INode>)
    fun visitReferenceTargetRef(link: IReferenceLink, visitor: IVisitor<INodeReference>)
    fun visitAllReferenceTargets(visitor: IVisitor<Pair<IReferenceLink, INode>>)
    fun visitAllReferenceTargetRefs(visitor: IVisitor<Pair<IReferenceLink, INodeReference>>)

    fun visitPropertyValue(property: IProperty, visitor: IVisitor<String?>)
    fun visitAllPropertyValues(visitor: IVisitor<Pair<IProperty, String?>>)

    fun setPropertyValue(property: IProperty, value: String?, visitor: IVisitor<Unit>)
    fun setReferenceTarget(link: IReferenceLink, target: INodeReference?, visitor: IVisitor<Unit>)
    fun setReferenceTarget(link: IReferenceLink, target: INode?, visitor: IVisitor<Unit>)
    fun addNewChild(role: IChildLink, index: Int, concept: IConcept?, visitor: IVisitor<INode>)
    fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?, visitor: IVisitor<INode>)
    fun moveChild(role: IChildLink, index: Int, child: INode, visitor: IVisitor<Unit>)

    interface IVisitor<E> {
        fun onNext(it: E)
        fun onComplete()
    }
}

class NodeAsAsyncNode(val node: INode) : IAsyncNode, INode by node {
    override fun visitContainmentLink(visitor: IAsyncNode.IVisitor<IChildLink?>) {
        visitor.onNext(getContainmentLink())
        visitor.onComplete()
    }

    override fun visitChildren(link: IChildLink, visitor: IAsyncNode.IVisitor<INode>) {
        getChildren(link).forEach { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitAllChildren(visitor: IAsyncNode.IVisitor<INode>) {
        allChildren.forEach { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitReferenceTarget(link: IReferenceLink, visitor: IAsyncNode.IVisitor<INode>) {
        getReferenceTarget(link)?.let { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitReferenceTargetRef(link: IReferenceLink, visitor: IAsyncNode.IVisitor<INodeReference>) {
        getReferenceTargetRef(link)?.let { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitAllReferenceTargets(visitor: IAsyncNode.IVisitor<Pair<IReferenceLink, INode>>) {
        getAllReferenceTargets().forEach { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitAllReferenceTargetRefs(visitor: IAsyncNode.IVisitor<Pair<IReferenceLink, INodeReference>>) {
        getAllReferenceTargetRefs().forEach { visitor.onNext(it) }
        visitor.onComplete()
    }

    override fun visitPropertyValue(property: IProperty, visitor: IAsyncNode.IVisitor<String?>) {
        visitor.onNext(getPropertyValue(property))
        visitor.onComplete()
    }

    override fun visitAllPropertyValues(visitor: IAsyncNode.IVisitor<Pair<IProperty, String?>>) {
        getPropertyLinks().forEach { link -> getPropertyValue(link)?.let { visitor.onNext(link to it) } }
        visitor.onComplete()
    }

    override fun setPropertyValue(property: IProperty, value: String?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
        visitor.onComplete()
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INodeReference?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
        visitor.onComplete()
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
        visitor.onComplete()
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?, visitor: IAsyncNode.IVisitor<INode>) {
        TODO()
        visitor.onComplete()
    }

    override fun addNewChild(
        role: IChildLink,
        index: Int,
        concept: IConceptReference?,
        visitor: IAsyncNode.IVisitor<INode>
    ) {
        TODO()
        visitor.onComplete()
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
        visitor.onComplete()
    }
}

fun INode.asAsyncNode(): IAsyncNode = if (this is IAsyncNode) this else NodeAsAsyncNode(this)
