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

@Deprecated("use flow based methods in INode")
interface IAsyncNode : INode {
    fun visitContainmentLink(visitor: IVisitor<IChildLink?>)

    fun visitParent(visitor: IVisitor<INode>)
    fun visitChildren(link: IChildLink, visitor: IVisitor<INode>)
    fun visitAllChildren(visitor: IVisitor<INode>)
    fun visitDescendants(visitor: IVisitor<INode>)

    fun visitReferenceTarget(link: IReferenceLink, visitor: IVisitor<INode>)
    fun visitReferenceTargetRef(link: IReferenceLink, visitor: IVisitor<INodeReference>)
    fun visitAllReferenceTargets(visitor: IVisitor<Pair<IReferenceLink, INode>>)
    fun visitAllReferenceTargetRefs(visitor: IVisitor<Pair<IReferenceLink, INodeReference>>)

    fun visitPropertyValue(property: IProperty, visitor: IVisitor<String?>)
    fun visitAllPropertyValues(visitor: IVisitor<Pair<IProperty, String>>)

    fun setPropertyValue(property: IProperty, value: String?, visitor: IVisitor<Unit>)
    fun setReferenceTarget(link: IReferenceLink, target: INodeReference?, visitor: IVisitor<Unit>)
    fun setReferenceTarget(link: IReferenceLink, target: INode?, visitor: IVisitor<Unit>)
    fun addNewChild(role: IChildLink, index: Int, concept: IConcept?, visitor: IVisitor<INode>)
    fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?, visitor: IVisitor<INode>)
    fun moveChild(role: IChildLink, index: Int, child: INode, visitor: IVisitor<Unit>)

    interface IVisitor<E> {
        fun onNext(it: E)
        fun onComplete()
        fun onError(ex: Throwable)
    }
}

fun <T> IAsyncNode.IVisitor<T>.visitAll(body: () -> Sequence<T>) {
    try {
        body().forEach { onNext(it) }
    } catch (ex: Throwable) {
        onError(ex)
    } finally {
        onComplete()
    }
}

class NodeAsAsyncNode(val node: INode) : IAsyncNode, INode by node {

    override fun visitContainmentLink(visitor: IAsyncNode.IVisitor<IChildLink?>) {
        visitor.visitAll { sequenceOf(getContainmentLink()) }
    }

    override fun visitParent(visitor: IAsyncNode.IVisitor<INode>) {
        visitor.visitAll { sequenceOf(parent).filterNotNull() }
    }

    override fun visitChildren(link: IChildLink, visitor: IAsyncNode.IVisitor<INode>) {
        visitor.visitAll { getChildren(link).asSequence() }
    }

    override fun visitAllChildren(visitor: IAsyncNode.IVisitor<INode>) {
        visitor.visitAll { allChildren.asSequence() }
    }

    override fun visitDescendants(visitor: IAsyncNode.IVisitor<INode>) {
        visitor.visitAll { getDescendants(false) }
    }

    override fun visitReferenceTarget(link: IReferenceLink, visitor: IAsyncNode.IVisitor<INode>) {
        visitor.visitAll { sequenceOf(getReferenceTarget(link)).filterNotNull() }
    }

    override fun visitReferenceTargetRef(link: IReferenceLink, visitor: IAsyncNode.IVisitor<INodeReference>) {
        visitor.visitAll { sequenceOf(getReferenceTargetRef(link)).filterNotNull() }
    }

    override fun visitAllReferenceTargets(visitor: IAsyncNode.IVisitor<Pair<IReferenceLink, INode>>) {
        visitor.visitAll { getAllReferenceTargets().asSequence() }
    }

    override fun visitAllReferenceTargetRefs(visitor: IAsyncNode.IVisitor<Pair<IReferenceLink, INodeReference>>) {
        visitor.visitAll { getAllReferenceTargetRefs().asSequence() }
    }

    override fun visitPropertyValue(property: IProperty, visitor: IAsyncNode.IVisitor<String?>) {
        visitor.visitAll { sequenceOf(getPropertyValue(property)) }
    }

    override fun visitAllPropertyValues(visitor: IAsyncNode.IVisitor<Pair<IProperty, String>>) {
        visitor.visitAll {
            getPropertyLinks().map { link -> link to getPropertyValue(link) }.filterSecondNotNull().asSequence()
        }
    }

    override fun setPropertyValue(property: IProperty, value: String?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INodeReference?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?, visitor: IAsyncNode.IVisitor<INode>) {
        TODO()
    }

    override fun addNewChild(
        role: IChildLink,
        index: Int,
        concept: IConceptReference?,
        visitor: IAsyncNode.IVisitor<INode>
    ) {
        TODO()
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode, visitor: IAsyncNode.IVisitor<Unit>) {
        TODO()
    }
}

@Deprecated("IAsyncNode is deprecated")
fun INode.asAsyncNode(): IAsyncNode = if (this is IAsyncNode) this else NodeAsAsyncNode(this)
