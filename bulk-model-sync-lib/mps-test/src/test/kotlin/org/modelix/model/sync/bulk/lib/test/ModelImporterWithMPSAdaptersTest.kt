package org.modelix.model.sync.bulk.lib.test

import jetbrains.mps.persistence.DefaultModelRoot
import org.jetbrains.mps.openapi.language.SConcept
import org.modelix.model.data.ModelData
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.asExported

class ModelImporterWithMPSAdaptersTest : MPSTestBase() {

    fun `test roots can be imported in different order (testdata oneEmptyModule)`() = runWrite {
        // This test was first motivated by noticing that MPS might return root nodes in a different order.
        // https://issues.modelix.org/issue/MODELIX-742/bulk-model-sync-attempts-to-reorder-unchaged-root-nodes-and-fails
        // A guess is that root nodes might be returned in a different order
        // when using file-per-root persistence.

        // Arrange
        val module = mpsProject.projectModules.single()
        val modelRoot = module.modelRoots.single() as DefaultModelRoot
        val model = modelRoot.createModel("someModel")!!
        val classConcept = resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept") as SConcept
        // Arrange: Create two roots
        val root1 = model.createNode(classConcept)
        model.addRootNode(root1)
        val root2 = model.createNode(classConcept)
        model.addRootNode(root2)
        val moduleAdapter = MPSModuleAsNode(module)
        // Arrange: Create export data
        val dataWithRootsInOneOrder = ModelData(root = moduleAdapter.asExported())
        val modelWitRootsInOneOrder = dataWithRootsInOneOrder.root.children[0]
        val rootsInOneOrder = modelWitRootsInOneOrder.children
        // Arrange: Create alternative export data with switched roots
        val rootsInDifferentOrder = listOf(rootsInOneOrder[1], rootsInOneOrder[0])
        val modelWithRootsInDifferentOrder = modelWitRootsInOneOrder.copy(children = rootsInDifferentOrder)
        val dataWithRootsInDifferentOrder = dataWithRootsInOneOrder.copy(
            root = dataWithRootsInOneOrder.root.copy(
                children = listOf(dataWithRootsInOneOrder.root.children[0], modelWithRootsInDifferentOrder),
            ),
        )

        // Act
        val importer = ModelImporter(moduleAdapter)
        // Importing the data should work for root nodes in any possible order.
        // Importing should never attempt to move root nodes and leave the order as is.
        // The tested behavior relies (a) on correctly specifying `IChildLink.isOrdered`
        // for `BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes`
        // and (b) the sync algorithms in `ModelImporter`.
        importer.import(dataWithRootsInOneOrder)
        importer.import(dataWithRootsInDifferentOrder)

        // Assert
        // We cannot assert the order of the root nodes,
        // as we make no guarantees about it.
        assertEquals(setOf(root1, root2), model.rootNodes.toSet())
    }
}
