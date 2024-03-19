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

@file:OptIn(UnstableModelixFeature::class)

package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.ModelFacade
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.JSNodeConverter
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.withAutoTransactions
import kotlin.Unit
import kotlin.js.Promise

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
fun loadModelsFromJson(
    json: Array<String>,
    changeCallback: (ChangeJS) -> Unit,
): INodeJS {
    val branch = loadModelsFromJsonAsBranch(json, changeCallback)
    return branch.rootNode
}

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
fun loadModelsFromJsonAsBranch(
    json: Array<String>,
    changeCallback: (ChangeJS) -> Unit,
): BranchJS {
    val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
    json.forEach { ModelData.fromJson(it).load(branch) }
    branch.addListener(ChangeListener(branch, changeCallback))
    return BranchJSImpl({}, branch.withAutoTransactions())
}

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
@DelicateCoroutinesApi
fun connectClient(url: String): Promise<ClientJS> {
    return GlobalScope.promise {
        val client = ModelClientV2.builder().url(url).build()
        client.init()
        return@promise ClientJSImpl(client)
    }
}

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
interface ClientJS {
    fun dispose()

    fun connectBranch(
        repositoryId: String,
        branchId: String,
        changeCallback: (ChangeJS) -> Unit,
    ): Promise<BranchJS>

    fun fetchBranches(repositoryId: String): Promise<Array<String>>

    fun fetchRepositories(): Promise<Array<String>>
}

class ClientJSImpl(private val modelClient: ModelClientV2) : ClientJS {

    @DelicateCoroutinesApi
    override fun fetchRepositories(): Promise<Array<String>> {
        return GlobalScope.promise {
            return@promise modelClient.listRepositories()
                .map { it.id }.toTypedArray()
        }
    }

    @DelicateCoroutinesApi
    override fun fetchBranches(
        repositoryId: String,
    ): Promise<Array<String>> {
        return GlobalScope.promise {
            val repositoryIdObject = RepositoryId(repositoryId)
            return@promise modelClient.listBranches(repositoryIdObject)
                .map { it.branchName }.toTypedArray()
        }
    }

    @DelicateCoroutinesApi
    override fun connectBranch(
        repositoryId: String,
        branchId: String,
        changeCallback: (ChangeJS) -> Unit,
    ): Promise<BranchJS> {
        return GlobalScope.promise {
            val modelClient = modelClient
            val branchReference = RepositoryId(repositoryId).getBranchReference(branchId)
            val model: ReplicatedModel = modelClient.getReplicatedModel(branchReference)
            model.start()
            val branch = model.getBranch()
            branch.addListener(ChangeListener(branch, changeCallback))
            val branchWithAutoTransaction = branch.withAutoTransactions()
            return@promise BranchJSImpl({ model.dispose() }, branchWithAutoTransaction)
        }
    }

    override fun dispose() {
        modelClient.close()
    }
}

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
interface BranchJS {
    val rootNode: INodeJS
    fun dispose()
    fun resolveNode(reference: INodeReferenceJS): INodeJS?
}

class ChangeListener(private val branch: IBranch, private val changeCallback: (ChangeJS) -> Unit) : IBranchListener {

    fun nodeIdToInode(nodeId: Long): INodeJS {
        return toNodeJs(PNodeAdapter(nodeId, branch))
    }

    override fun treeChanged(oldTree: ITree?, newTree: ITree) {
        if (oldTree == null) {
            return
        }
        newTree.visitChanges(
            oldTree,
            object : ITreeChangeVisitor {
                override fun containmentChanged(nodeId: Long) {
                    changeCallback(ContainmentChanged(nodeIdToInode(nodeId)))
                }

                override fun conceptChanged(nodeId: Long) {
                    changeCallback(ConceptChanged(nodeIdToInode(nodeId)))
                }

                override fun childrenChanged(nodeId: Long, role: String?) {
                    changeCallback(ChildrenChanged(nodeIdToInode(nodeId), role))
                }

                override fun referenceChanged(nodeId: Long, role: String) {
                    changeCallback(ReferenceChanged(nodeIdToInode(nodeId), role))
                }

                override fun propertyChanged(nodeId: Long, role: String) {
                    changeCallback(PropertyChanged(nodeIdToInode(nodeId), role))
                }
            },
        )
    }
}

fun toNodeJs(rootNode: INode) = JSNodeConverter.nodeToJs(rootNode).unsafeCast<INodeJS>()
