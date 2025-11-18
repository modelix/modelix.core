package org.modelix.model.area

import org.modelix.model.api.AreaAsModel
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.ITransactionManager

/**
 * An IArea is similar to an IBranch. They both provide transactional access to nodes, but the IBranch can only be used
 * with an ITree. An IArea can be used with any INode. IArea is a higher level replacement for IBranch.
 * The goal is to provide access to different independent models through one virtual model.
 * It's like a unix filesystem with mount points. The model inside an area can also be an MPS model that is not a
 * persistent data structure.
 */
@Deprecated("Use IMutableModel")
interface IArea : INodeResolutionScope, ITransactionManager {
    /**
     * The root of an area is not allowed to change
     */
    fun getRoot(): INode

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

    override fun <T> executeRead(body: () -> T): T
    override fun <T> executeWrite(body: () -> T): T
    override fun canRead(): Boolean
    override fun canWrite(): Boolean

    /** bigger numbers are locked first */
    fun getLockOrderingPriority(): Long = 0

    fun addListener(l: IAreaListener)
    fun removeListener(l: IAreaListener)

    override fun asModel(): IMutableModel = AreaAsModel(this)
}
