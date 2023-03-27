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
package org.modelix.model.area

import org.modelix.model.api.*

/**
 * An IArea is similar to an IBranch. They both provide transactional access to nodes, but the IBranch can only be used
 * with an ITree. An IArea can be used with any INode. IArea is a higher level replacement for IBranch.
 * The goal is to provide access to different independent models through one virtual model.
 * It's like a unix filesystem with mount points. The model inside an area can also be an MPS model that is not a
 * persistent data structure.
 */
@Deprecated("Use IModel, IReferenceResolutionScope or IModelTransactionManager")
interface IArea : IModelTransactionManager, IModel {
    override fun getTransactionManager(): IModelTransactionManager = this

    /**
     * The root of an area is not allowed to change
     */
    fun getRoot(): INode

    override fun getRootNode(): INode? = getRoot()

    @Deprecated("use ILanguageRepository.resolveConcept")
    fun resolveConcept(ref: IConceptReference): IConcept?

    /**
     * The area should not delegate to INodeReference.resolveNode.
     * If it can't handle the type of reference it should just return null.
     *
     * This method requires resolveNode().getNode() == this
     */
    override fun resolveNode(ref: INodeReference): INode?

    /**
     * This method allows resolveOriginalNode().getArea() != this
     */
    fun resolveOriginalNode(ref: INodeReference): INode?
    fun resolveBranch(id: String): IBranch?
    fun collectAreas(): List<IArea>
    fun getReference(): IAreaReference
    fun resolveArea(ref: IAreaReference): IArea?

    override fun <T> executeRead(f: () -> T): T
    override fun <T> executeWrite(f: () -> T): T
    override fun canRead(): Boolean
    override fun canWrite(): Boolean
    /** bigger numbers are locked first */
    fun getLockOrderingPriority(): Long = 0

    fun addListener(l: IAreaListener)
    fun removeListener(l: IAreaListener)
}

fun IArea.asModelList(): IModelList {
    return ModelList(
        collectAreas()
            .filter { it !is CompositeArea && it !is AreaWithMounts }
            .sortedByDescending { it.getLockOrderingPriority() }
    )
}

data class ModelAsArea(val model: IModel) : IArea, IAreaReference {
    init {
        require(model !is IArea) { "$model is already an IArea" }
    }
    override fun getRoot(): INode {
        return model.getRootNode() ?: throw RuntimeException("Model $model has no root node")
    }

    override fun resolveConcept(ref: IConceptReference): IConcept? {
        return ILanguageRepository.resolveConcept(ref)
    }

    override fun resolveNode(ref: INodeReference): INode? {
        return model.resolveNode(ref)
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return model.resolveNode(ref)
    }

    override fun resolveBranch(id: String): IBranch? {
        return model.resolveBranch(id)
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        return this
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return this.takeIf { it == ref }
    }

    override fun <T> executeRead(f: () -> T): T {
        return model.getTransactionManager().executeRead(f)
    }

    override fun <T> executeWrite(f: () -> T): T {
        return model.getTransactionManager().executeWrite(f)
    }

    override fun canRead(): Boolean {
        return model.getTransactionManager().canRead()
    }

    override fun canWrite(): Boolean {
        return model.getTransactionManager().canWrite()
    }

    override fun addListener(l: IAreaListener) {
    }

    override fun removeListener(l: IAreaListener) {
    }
}

@Deprecated("IArea is deprecated")
fun IReferenceResolutionScope.asArea(): IArea {
    return when (this) {
        is IArea -> this
        is IModel -> ModelAsArea(this)
        is IModelList -> CompositeArea(getModels().map { it.asArea() })
        else -> throw IllegalArgumentException("Unsupported: $this")
    }
}
