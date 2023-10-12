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

package org.modelix.mps.sync.tools.history

import com.intellij.openapi.application.ApplicationManager
import jetbrains.mps.baseLanguage.closures.runtime._FunctionTypes
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IterableUtils
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import org.modelix.model.LinearHistory
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.IOperation
import org.modelix.mps.sync.connection.ModelServerConnection
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.UUID
import java.util.Vector
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class HistoryView : JPanel() {
    private val tableModel: DefaultTableModel
    private val table: JTable
    private var versionGetter: _FunctionTypes._return_P0_E0<out CLVersion?>? = null
    private val versions: List<CLVersion> = ListSequence.fromList(ArrayList())
    private var modelServer: ModelServerConnection? = null
    private var repositoryId: RepositoryId? = null
    private var previousBranchName: String? = null
    private val resetButton: JButton
    private val revertButton: JButton

    init {
        setLayout(BorderLayout())
        tableModel = DefaultTableModel()
        tableModel.addColumn("ID")
        tableModel.addColumn("Author")
        tableModel.addColumn("Time")
        tableModel.addColumn("Operations")
        tableModel.addColumn("Hash")
        table = JTable(tableModel)
        val scrollPane = JScrollPane(table)
        scrollPane.setBorder(BorderFactory.createEmptyBorder())
        add(scrollPane, BorderLayout.CENTER)
        val buttonPanel = JPanel(FlowLayout())
        val loadButton = JButton("Load Selected Version")
        loadButton.addActionListener { loadSelectedVersion() }
        buttonPanel.add(loadButton)
        resetButton = JButton("Reset to ...")
        resetButton.setEnabled(false)
        resetButton.addActionListener { restoreBranch() }
        buttonPanel.add(resetButton)
        revertButton = JButton("Revert to Selected Version")
//        revertButton.addActionListener { revertToSelectedVersion() }
        buttonPanel.add(revertButton)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    fun loadSelectedVersion() {
        val index = table.selectedRow
        if (0 <= index && index < ListSequence.fromList(versions).count()) {
            val version = ListSequence.fromList(versions).getElement(index)
            val branch = modelServer!!.getActiveBranch(repositoryId!!)
            val branchName = "history" + UUID.randomUUID()
            val branchKey = repositoryId!!.getBranchKey(branchName)
            modelServer!!.getClient().put(branchKey, version.hash)
            branch.switchBranch(branchName)
            resetButton.setEnabled(true)
        }
    }

// TODO: fix function
//    fun revertToSelectedVersion() {
//        val index = table.selectedRow
//        if (0 <= index && index < ListSequence.fromList(versions).count()) {
//            val activeBranch = modelServer!!.getActiveBranch(repositoryId!!)
//            val versionToRevertTo = ListSequence.fromList(versions).getElement(index)
//            val latestKnownVersion = activeBranch.version
//            val branch = activeBranch.branch
//            branch.runWriteT { t: IWriteTransaction ->
//                t.applyOperation(
//                    RevertToOp(
//                        KVEntryReference(latestKnownVersion.data),
//                        KVEntryReference(versionToRevertTo.data)
//                    )
//                )
//                Unit
//            }
//            ApplicationManager.getApplication().invokeLater { refreshHistory() }
//        }
//    }

    fun restoreBranch() {
        modelServer!!.getActiveBranch(repositoryId!!).switchBranch(previousBranchName!!)
        resetButton.setEnabled(false)
    }

    fun refreshHistory() {
        loadHistory(modelServer, repositoryId, versionGetter)
    }

    fun loadHistory(
        modelServer: ModelServerConnection?,
        repositoryId: RepositoryId?,
        headVersion: _FunctionTypes._return_P0_E0<out CLVersion?>?,
    ) {
        versionGetter = headVersion
        this.modelServer = modelServer
        this.repositoryId = repositoryId
        previousBranchName = modelServer!!.getActiveBranch(repositoryId!!).branchName
        resetButton.setText("Reset to $previousBranchName")
        ThreadUtils.runInUIThreadAndWait {
            while (tableModel.rowCount > 0) {
                tableModel.removeRow(0)
            }
            ListSequence.fromList(versions).clear()
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            var version = headVersion!!.invoke()
            while (version != null) {
                createTableRow(version)
                if (version.isMerge()) {
                    val store = version.store
                    for (v in ListSequence.fromList(
                        LinearHistory(
                            version.baseVersion!!.hash,
                        ).load(
                            CLVersion(
                                version.data!!.mergedVersion1!!.getValue(store),
                                store,
                            ),
                            CLVersion(
                                version.data!!.mergedVersion2!!.getValue(store),
                                store,
                            ),
                        ),
                    )) {
                        createTableRow(v)
                    }
                }
                if (ListSequence.fromList(versions)
                        .count() >= 500
                ) {
                    break
                }
                version = version.baseVersion
            }
        }
    }

    private fun createTableRow(version: CLVersion) {
        ThreadUtils.runInUIThreadAndWait {
            val opsDescription: String
            opsDescription = if (version.isMerge()) {
                "merge " + version.getMergedVersion1()!!.id + " + " + version.getMergedVersion2()!!.id + " (base " + version.baseVersion + ")"
            } else {
                "(" + version.numberOfOperations + ") " + if (version.operationsInlined()) {
                    IterableUtils.join(
                        Sequence.fromIterable(version.operations)
                            .select(object :
                                ISelector<IOperation, String>() {
                                override fun select(it: IOperation): String {
                                    return it.toString()
                                }
                            }),
                        " # ",
                    )
                } else {
                    "..."
                }
            }

            tableModel.addRow(
                Vector(
                    ListSequence.fromListAndArray(
                        ArrayList<Any?>(),
                        java.lang.Long.toHexString(version.id),
                        version.author,
                        version.time,
                        opsDescription,
                        version.hash,
                    ),
                ),
            )
            ListSequence.fromList(versions).addElement(version)
        }
    }
}
