/*
 * Copyright (c) 2023.
 *
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

package org.modelix.mps.sync

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.repository.CommandListener
import org.modelix.model.area.PArea
import org.modelix.model.client.IModelClient
import org.modelix.model.client.ReplicatedRepository
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.UndoOp
import org.modelix.model.operations.applyOperation

class MpsReplicatedRepository(
    client: IModelClient,
    repositoryId: RepositoryId,
    branchName: String,
    user: () -> String,
) : ReplicatedRepository(client, repositoryId, branchName, user) {

    companion object {
        private val INSTANCES = mutableSetOf<MpsReplicatedRepository>()
        private val affectedDocuments = mutableSetOf<DocumentReference>()

        fun documentChanged(refForDoc: DocumentReference) {
            affectedDocuments.add(refForDoc)
        }
    }

    private val commandListener: CommandListener = object : CommandListener {
        override fun commandStarted() {
            affectedDocuments.clear()
            startEdit()
        }

        override fun commandFinished() {
            val version = endEdit() ?: return
            val project = CommandProcessor.getInstance().currentCommandProject ?: return
            val undoManager = UndoManager.getInstance(project)
            undoManager.undoableActionPerformed(ModelixUndoableAction(version, affectedDocuments))
        }
    }

    init {
        MPSModuleRepository.getInstance().modelAccess.addCommandListener(commandListener)
        synchronized(INSTANCES) { INSTANCES.add(this) }
    }

    inner class ModelixUndoableAction(private val version: CLVersion, docs: Iterable<DocumentReference>) :
        UndoableAction {
        private val documents: Array<DocumentReference>

        init {
            documents = docs.toList().toTypedArray()
        }

        @Throws(UnexpectedUndoException::class)
        override fun undo() {
            PArea(branch).executeWrite {
                branch.writeTransaction.applyOperation(
                    UndoOp(KVEntryReference(version.data!!)),
                )
            }
        }

        @Throws(UnexpectedUndoException::class)
        override fun redo() {
            throw UnexpectedUndoException("Not supported yet")
        }

        override fun getAffectedDocuments(): Array<out DocumentReference>? = documents.ifEmpty { null }

        override fun isGlobal(): Boolean = documents.isEmpty()
    }
}
