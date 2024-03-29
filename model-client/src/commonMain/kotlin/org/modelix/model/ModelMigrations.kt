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
package org.modelix.model

import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.lazy.unwrap
import org.modelix.model.metameta.MetaModelMigration

object ModelMigrations {

    fun useCanonicalReferences(branch: IBranch) {
        branch.runWriteT { t ->
            val tree = t.tree.unwrap()
            PrefetchCache.with(tree) {
                (tree as? CLTree)?.prefetchAll()
                useCanonicalReferences(t, PArea(branch), ITree.ROOT_ID)
            }
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
