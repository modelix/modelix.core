package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.key
import org.modelix.model.area.IArea
import kotlin.reflect.KClass

/**
 * Minimal hand-written "generated" language used by [UnknownConceptAccessorTest].
 *
 * It mimics what the metamodel generator emits: a [GeneratedLanguage] with one [GeneratedConcept],
 * a typed node interface and a [TypedNodeImpl] implementation.
 */
object TestLanguage : GeneratedLanguage("org.modelix.metamodel.test") {
    override fun getConcepts(): List<IConcept> = listOf(TestConcept)
}

const val TEST_CONCEPT_UID = "test:00000000-0000-0000-0000-000000000001/1"

/** UID that is deliberately not registered in the [TypedLanguagesRegistry]. */
const val UNREGISTERED_CONCEPT_UID = "mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1068390468198"

interface ITestNode : ITypedNode

object TestConcept : GeneratedConcept<TestNodeImpl, TestTypedConcept>("TestConcept", false) {
    override val language: ILanguage = TestLanguage
    override fun getUID(): String = TEST_CONCEPT_UID
    override fun getDirectSuperConcepts(): List<IConcept> = emptyList()
    override fun typed(): TestTypedConcept = TestTypedConcept
    override fun getInstanceClass(): KClass<out TestNodeImpl> = TestNodeImpl::class
    override fun wrap(node: INode): TestNodeImpl = TestNodeImpl(node)

    val children = newChildListLink<TestNodeImpl, TestTypedConcept>("children", null, true, this, TestNodeImpl::class)
    val target = newReferenceLink<TestNodeImpl, TestTypedConcept>("target", null, true, this, TestNodeImpl::class)
}

object TestTypedConcept : INonAbstractConcept<TestNodeImpl> {
    override fun untyped(): IConcept = TestConcept
    override fun getInstanceInterface(): KClass<out TestNodeImpl> = TestNodeImpl::class
}

class TestNodeImpl(node: INode) : TypedNodeImpl(node), ITestNode {
    override val _concept: ITypedConcept get() = TestTypedConcept
}

/**
 * In-memory [INode] that only implements what the accessors under test need.
 */
@Suppress("OVERRIDE_DEPRECATION", "TooManyFunctions")
class FakeNode(
    private val conceptRef: IConceptReference?,
    private val id: String,
) : INode {
    private val childrenByRole = LinkedHashMap<String?, MutableList<INode>>()
    private val referencesByRole = LinkedHashMap<String, INode?>()

    override val concept: IConcept? get() = conceptRef?.let { TypedLanguagesRegistry.resolveConcept(it.getUID()) }
    override fun getConceptReference(): IConceptReference? = conceptRef
    override val isValid: Boolean get() = true
    override val reference: INodeReference get() = SerializedNodeReference(id)
    override var roleInParent: String? = null
    override var parent: INode? = null
    override val allChildren: Iterable<INode> get() = childrenByRole.values.flatten()

    fun addChild(link: IChildLink, child: FakeNode): FakeNode {
        val role = link.key(this)
        childrenByRole.getOrPut(role) { mutableListOf() }.add(child)
        child.parent = this
        child.roleInParent = role
        return child
    }

    fun setReference(link: IReferenceLink, target: INode?) {
        referencesByRole[link.key(this)] = target
    }

    override fun getChildren(role: String?): Iterable<INode> = childrenByRole[role] ?: emptyList()

    override fun getReferenceTarget(role: String): INode? = referencesByRole[role]

    override fun getArea(): IArea = throw UnsupportedOperationException()
    override fun moveChild(role: String?, index: Int, child: INode) = throw UnsupportedOperationException()
    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode = throw UnsupportedOperationException()
    override fun removeChild(child: INode) = throw UnsupportedOperationException()
    override fun setReferenceTarget(role: String, target: INode?) = throw UnsupportedOperationException()
    override fun getPropertyValue(role: String): String? = null
    override fun setPropertyValue(role: String, value: String?) = throw UnsupportedOperationException()
    override fun getPropertyRoles(): List<String> = emptyList()
    override fun getReferenceRoles(): List<String> = referencesByRole.keys.toList()
}
