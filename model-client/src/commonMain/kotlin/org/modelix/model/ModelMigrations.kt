package org.modelix.model

import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import org.modelix.model.lazy.unwrap
import org.modelix.model.metameta.MetaModelMigration

object ModelMigrations {

    fun useCanonicalReferences(branch: IBranch) {
        branch.runWriteT { t ->
            val tree = t.tree.unwrap()
            useCanonicalReferences(t, PArea(branch), ITree.ROOT_ID)
        }
    }

    private fun useCanonicalReferences(t: IWriteTransaction, area: IArea, node: Long) {
        for (role in t.getReferenceRoles(node)) {
            val original = t.getReferenceTarget(node, role) ?: continue
            val canonical = original.resolveNode(area)?.reference ?: continue
            if (canonical != original) {
                t.setReferenceTarget(node, role, canonical)
            }
        }
        for (child in t.getAllChildren(node)) {
            useCanonicalReferences(t, area, child)
        }
    }

    /**
     * Migrates data created with a metamodel to data
     * that is readable without the metamodel.
     *
     * The migration is skipped if no metamodel information exists.
     * In a migration, the concept references of all nodes that relied on metamodel information to be resolved
     * are changed to concept references that do not need the metamodel.
     * In a migration, nodes are recreated with the same ID.
     * After the migration, the metamodel is kept for future reference.
     */
    fun useResolvedConceptsFromMetaModel(rawBranch: IBranch) {
        MetaModelMigration.useResolvedConceptsFromMetaModel(rawBranch)
    }
}
