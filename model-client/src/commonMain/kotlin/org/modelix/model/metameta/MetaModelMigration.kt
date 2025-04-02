package org.modelix.model.metameta

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.getRootNode
import org.modelix.model.persistent.SerializationUtil

internal object MetaModelMigration {

    private val HEX_LONG_PATTERN = Regex("[a-fA-Z0-9]+")

    private val LOG = mu.KotlinLogging.logger {}

    private data class ConceptInformation(
        val nodeId: Long,
        val originalConcept: IConceptReference?,
        val newConcept: IConceptReference?,
    )

    fun useResolvedConceptsFromMetaModel(rawBranch: IBranch) {
        LOG.info { "Start migration for `$rawBranch`." }
        rawBranch.runWriteT { transaction ->
            val root = rawBranch.getRootNode()
            val languageNodes = root.getChildren(MetaModelIndex.LANGUAGES_ROLE).toList()
            val needsMigration = languageNodes.isNotEmpty()
            if (!needsMigration) {
                LOG.info { "Migration not needed for `$rawBranch` because no meta model information is stored." }
                return@runWriteT
            }
            val originalData = transaction.tree
            migrateChildren(transaction, originalData, ITree.ROOT_ID)
        }
        LOG.info { "Finish migration for `$rawBranch`." }
    }

    private fun migrateChildren(newData: IWriteTransaction, originalData: ITree, parentId: Long) {
        val childIds = newData.getAllChildren(parentId)

        val conceptInformationForChildren = childIds.map { childId ->
            val originalConcept = newData.getConceptReference(childId)
            val newConcept = resolveNewConceptReference(originalConcept, originalData)
            ConceptInformation(childId, originalConcept, newConcept)
        }

        val noConceptChanged = conceptInformationForChildren.all { it.newConcept == it.originalConcept }

        if (noConceptChanged) {
            childIds.forEach { childId ->
                migrateChildren(newData, originalData, childId)
            }
        } else {
            conceptInformationForChildren.forEach { conceptInformationForChild ->
                val childId = conceptInformationForChild.nodeId
                val originalConcept = conceptInformationForChild.originalConcept
                val newConcept = conceptInformationForChild.newConcept
                val keepConcept = originalConcept?.getUID() == newConcept?.getUID()

                if (keepConcept) {
                    migrateChildren(newData, originalData, childId)
                    newData.moveChild(parentId, originalData.getRole(childId), -1, childId)
                } else {
                    // Currently we have no mechanism to efficiently replace the concept on ITransaction.
                    // This will become available with https://issues.modelix.org/issue/MODELIX-710
                    // For this migration, we can just recreate the children with the same IDs.
                    newData.deleteNode(childId)
                    newData.addNewChild(parentId, originalData.getRole(childId), -1, childId, newConcept)
                    createChildren(newData, originalData, childId)
                }
            }
        }
    }

    private fun createChildren(newData: IWriteTransaction, originalData: ITree, parentId: Long) {
        writeProperties(newData, originalData, parentId)
        writeReferences(newData, originalData, parentId)
        for (childId in originalData.getAllChildren(parentId)) {
            val originalConcept = originalData.getConceptReference(childId)
            val newConcept = resolveNewConceptReference(originalConcept, originalData)
            newData.addNewChild(parentId, originalData.getRole(childId), -1, childId, newConcept)
            createChildren(newData, originalData, childId)
        }
    }

    private fun writeReferences(newData: IWriteTransaction, originalData: ITree, parentId: Long) {
        val referenceRoles = originalData.getReferenceRoles(parentId)
        for (referenceRole in referenceRoles) {
            val referenceValue = originalData.getReferenceTarget(parentId, referenceRole)
            newData.setReferenceTarget(parentId, referenceRole, referenceValue)
        }
    }

    private fun writeProperties(
        t: IWriteTransaction,
        originalData: ITree,
        parentId: Long,
    ) {
        val propertyRoles = originalData.getPropertyRoles(parentId)
        for (propertyRole in propertyRoles) {
            val propertyValue = originalData.getProperty(parentId, propertyRole)
            t.setProperty(parentId, propertyRole, propertyValue)
        }
    }

    fun resolveNewConceptReference(originalConceptRef: IConceptReference?, tree: ITree): IConceptReference? {
        if (originalConceptRef == null) {
            return originalConceptRef
        }

        val originalUID = originalConceptRef.getUID()
        if (!(originalUID matches HEX_LONG_PATTERN)) {
            return originalConceptRef
        }

        val conceptNodeId = SerializationUtil.longFromHex(originalUID)
        if (!tree.containsNode(conceptNodeId)) {
            return originalConceptRef
        }
        val uid = tree.getProperty(conceptNodeId, MetaMetaLanguage.property_IHasUID_uid.toReference().stringForLegacyApi())
            ?: return originalConceptRef

        return ConceptReference(uid)
    }
}
