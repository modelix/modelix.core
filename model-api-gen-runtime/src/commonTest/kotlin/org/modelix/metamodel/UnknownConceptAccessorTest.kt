package org.modelix.metamodel

import org.modelix.model.api.ConceptReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnknownConceptAccessorTest {

    @BeforeTest
    fun registerLanguage() {
        TestLanguage.register()
    }

    @AfterTest
    fun unregisterLanguage() {
        TestLanguage.unregister()
    }

    private fun knownNode(id: String) = FakeNode(ConceptReference(TEST_CONCEPT_UID), id)
    private fun unknownNode(id: String) = FakeNode(ConceptReference(UNREGISTERED_CONCEPT_UID), id)

    private fun accessor(parent: FakeNode) =
        ChildListAccessor(parent, TestConcept.children, TestConcept, TestNodeImpl::class)

    @Test
    fun `strict iteration reports the node and its unknown concept`() {
        val parent = knownNode("parent")
        parent.addChild(TestConcept.children, unknownNode("child-1"))

        val exception = assertFailsWith<UnknownConceptException> { accessor(parent).toList() }

        assertEquals(UNREGISTERED_CONCEPT_UID, exception.conceptUID)
        assertEquals("child-1", exception.nodeReference)
        assertEquals("children", exception.containmentRole)
        val message = checkNotNull(exception.message)
        assertTrue(message.contains(UNREGISTERED_CONCEPT_UID), "concept UID missing in: $message")
        assertTrue(message.contains("child-1"), "node reference missing in: $message")
        assertTrue(message.contains("TestNodeImpl"), "expected type missing in: $message")
    }

    @Test
    fun `the unknown concept exception stays catchable as a ClassCastException`() {
        val parent = knownNode("parent")
        parent.addChild(TestConcept.children, unknownNode("child-1"))

        assertFailsWith<ClassCastException> { accessor(parent).toList() }
    }

    @Test
    fun `known and unknown split the children and keep the stored order`() {
        val parent = knownNode("parent")
        parent.addChild(TestConcept.children, knownNode("a"))
        parent.addChild(TestConcept.children, unknownNode("b"))
        parent.addChild(TestConcept.children, knownNode("c"))
        parent.addChild(TestConcept.children, unknownNode("d"))

        val accessor = accessor(parent)

        assertEquals(listOf("a", "c"), accessor.known().map { it.unwrap().reference.serialize() })
        assertEquals(listOf("b", "d"), accessor.unknown().map { it.unwrap().reference.serialize() })
        assertEquals(
            listOf("a", "b", "c", "d"),
            accessor.untypedNodes().map { it.reference.serialize() },
        )
    }

    @Test
    fun `the strict accessors stay strict when an unknown child is present`() {
        val parent = knownNode("parent")
        parent.addChild(TestConcept.children, knownNode("a"))
        parent.addChild(TestConcept.children, unknownNode("b"))

        val accessor = accessor(parent)
        assertFailsWith<UnknownConceptException> { accessor.getSize() }
        assertFailsWith<UnknownConceptException> { accessor.toList() }
    }

    @Test
    fun `known equals the strict iteration when all children are known`() {
        val parent = knownNode("parent")
        parent.addChild(TestConcept.children, knownNode("a"))
        parent.addChild(TestConcept.children, knownNode("b"))

        val accessor = accessor(parent)

        assertEquals(accessor.toList(), accessor.known().toList())
        assertEquals(emptyList(), accessor.unknown().toList())
        assertEquals(2, accessor.getSize())
    }

    @Test
    fun `a single child accessor offers a lenient view without changing get`() {
        val parentWithUnknown = knownNode("parent-1")
        parentWithUnknown.addChild(TestConcept.children, unknownNode("child-1"))
        val single = SingleChildAccessor(parentWithUnknown, TestConcept.children, TestConcept, TestNodeImpl::class)
        assertNull(single.knownOrNull())
        assertFailsWith<UnknownConceptException> { single.get() }

        val parentWithKnown = knownNode("parent-2")
        parentWithKnown.addChild(TestConcept.children, knownNode("child-2"))
        val singleKnown = SingleChildAccessor(parentWithKnown, TestConcept.children, TestConcept, TestNodeImpl::class)
        assertEquals("child-2", singleKnown.knownOrNull()?.unwrap()?.reference?.serialize())
    }

    @Test
    fun `a reference to an unknown concept is lenient only through the known accessor`() {
        val source = knownNode("source")
        source.setReference(TestConcept.target, unknownNode("target-unknown"))

        assertFailsWith<UnknownConceptException> { source.getReferenceTargetOrNull(TestConcept.target) }
        assertNull(source.getKnownReferenceTargetOrNull(TestConcept.target))

        val accessor = OptionalReferenceAccessor<Any, TestNodeImpl>(source, TestConcept.target, TestNodeImpl::class)
        assertNull(accessor.knownTargetOrNull())

        source.setReference(TestConcept.target, knownNode("target-known"))
        assertEquals(
            "target-known",
            source.getKnownReferenceTargetOrNull(TestConcept.target)?.unwrap()?.reference?.serialize(),
        )
        assertEquals("target-known", accessor.knownTargetOrNull()?.unwrap()?.reference?.serialize())
    }
}
