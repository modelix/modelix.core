package org.modelix.model.api

import org.modelix.model.ModelFacade
import kotlin.test.Test
import kotlin.test.assertEquals

class MapWithRoleKeyTest {
    @Test
    fun referenceLinkMapCachesValueByRoleObject() {
        println("\r\n # referenceLinkMapCachesValueByRoleObject")
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val (source, target) = branch.computeWrite {
            val root = branch.getRootNode()
            val source = root.addNewChild("source")
            val target = root.addNewChild("target")
            source.setReferenceTarget(IReferenceLinkReference.fromId("testRef").toLegacy(), target)
            source to target
        }

        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()

        val first = map.getOrPut(roleObject) { jsTarget }
        assertEquals(jsTarget, first)

        val second = map.getOrPut(roleObject) { NodeAdapterJS(target) }
        assertEquals(jsTarget, second)
    }

    @Test
    fun IReferenceLinkReference_stringForLegacyApi() {
        val aNode = ModelFacade.toLocalBranch(ModelFacade.newLocalTree()).getRootNode()
        assertEquals(
            IReferenceLinkReference.fromIdAndName("refId", "refName").toLegacy().key(aNode),
            ":refId:refName",
        )
    }

    @Test
    fun mapFindsValueAcrossRoleRepresentations() {
        println("\r\n # mapFindsValueAcrossRoleRepresentations")
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val (source, target) = branch.computeWrite {
            val root = branch.getRootNode()
            val source = root.addNewChild("source")
            val target = root.addNewChild("target")
            source.setReferenceTarget(IReferenceLinkReference.fromIdAndName("refId", "refName").toLegacy(), target)
            source to target
        }

        println("source is a ${source::class}")
        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()

        map.getOrPut(roleObject) { jsTarget }

        // Once we've looked it up with a combined string, the entry remembers both as keys
        assertEquals(jsTarget, map.getOrPut(":refId:refName") { error("Miss with combined string") })

        // All these lookups should hit the same cached value
        assertEquals(jsTarget, map.getOrPut(IReferenceLinkReference.fromId("refId")) { error("Miss with id") })
        assertEquals(jsTarget, map.getOrPut(IReferenceLinkReference.fromName("refName")) { error("Miss with name") })
    }

    private fun setupReferencingNodes(usesRoleIds: Boolean = false): Triple<INode, INode, INode> {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree(usesRoleIds))
        return branch.computeWrite {
            val root = branch.getRootNode()
            val source = root.addNewChild("source")
            val target = root.addNewChild("target")
            source.setReferenceTarget(IReferenceLinkReference.fromIdAndName("refId", "refName").toLegacy(), target)
            Triple(source, target, root.addNewChild("another"))
        }
    }

    @Test
    fun usingRoleIds_lookup_by_name_only_misses() {
        println("\r\n # usingRoleIds_lookup_by_name_only_misses")
        // given I have some nodes
        val (source, target, another) = setupReferencingNodes(usesRoleIds = true)

        // and the cache has warmed up
        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()
        map.getOrPut(roleObject) { jsTarget }

        // then I still miss with a name-only lookup
        assertEquals(another, map.getOrPut(IReferenceLinkReference.fromName("refName")) { NodeAdapterJS(another) }.node)
    }

    @Test
    fun usingRoleIds_lookup_with_id_only_hits() {
        println("\r\n # usingRoleIds_lookup_with_id_only_hits")
        // given I have some nodes
        val (source, target, another) = setupReferencingNodes(usesRoleIds = true)

        // and the cache has warmed up
        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()
        map.getOrPut(roleObject) { jsTarget }

        // then I hit the cache with an id-only lookup
        assertEquals(target, map.getOrPut(IReferenceLinkReference.fromId("refId")) { error("Miss with id") }.node)
    }

    @Test
    fun notUsingRoleIds_lookup_by_id_only_misses() {
        println("\r\n # notUsingRoleIds_lookup_by_id_only_misses")
        // given I have some nodes
        val (source, target, another) = setupReferencingNodes(usesRoleIds = false)

        // and the cache has warmed up
        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()
        map.getOrPut(roleObject) { jsTarget }

        // then I still miss with an id-only lookup
        assertEquals(another, map.getOrPut(IReferenceLinkReference.fromId("refId")) { NodeAdapterJS(another) }.node)
    }

    @Test
    fun notUsingRoleIds_lookup_with_name_only_hits() {
        println("\r\n # notUsingRoleIds_lookup_with_name_only_hits")
        // given I have some nodes
        val (source, target, another) = setupReferencingNodes(usesRoleIds = false)

        // and the cache has warmed up
        val jsTarget = NodeAdapterJS(target)
        val roleObject = NodeAdapterJS(source).getReferenceRoles().first()
        val map = MapWithReferenceRoleKey<NodeAdapterJS>()
        map.getOrPut(roleObject) { jsTarget }

        // then I hit the cache with a name-only lookup
        assertEquals(target, map.getOrPut(IReferenceLinkReference.fromName("refName")) { error("Miss with name") }.node)
    }

    @Test
    fun childLinkMapCachesValueByRoleObject() {
        println("\r\n # childLinkMapCachesValueByRoleObject")
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val child = branch.computeWrite {
            branch.getRootNode().addNewChild("children")
        }

        val roleObject = NodeAdapterJS(child).getRoleInParent() ?: error("No role")
        val map = MapWithChildRoleKey<String>()

        val first = map.getOrPut(roleObject) { "cached" }
        assertEquals("cached", first)

        val second = map.getOrPut(roleObject) { "uncached" }
        assertEquals("cached", second)
    }

    @Test
    fun propertyMapFindsCachedValueByStringAndRoleObject() {
        println("\r\n # propertyMapFindsCachedValueByStringAndRoleObject")
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val node = branch.computeWrite {
            val n = branch.getRootNode()
            n.setPropertyValue("name", "testValue")
            n
        }

        val jsNode = NodeAdapterJS(node)
        val roleObject = jsNode.getPropertyRoles().first()
        val map = MapWithPropertyRoleKey<String>()

        val stored = map.getOrPut(roleObject) { "stored" }
        assertEquals("stored", stored)

        // Lookup by string should find the same cached value
        val found = map.getOrPut("name") { "uncached" }
        assertEquals("stored", found)
    }
}
