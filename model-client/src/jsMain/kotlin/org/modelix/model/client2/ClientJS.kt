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

@file:OptIn(UnstableModelixFeature::class, UnstableModelixFeature::class)

package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.JSNodeConverter
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
fun loadModelsFromJson(json: Array<String>): INodeJS {
    val branch = loadModelsFromJsonAsBranch(json)
    return branch.rootNode
}

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
fun loadModelsFromJsonAsBranch(json: Array<String>): BranchJS {
    val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
    json.forEach { ModelData.fromJson(it).load(branch) }
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

    fun connectBranch(repositoryId: String, branchId: String): Promise<BranchJS>

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
    override fun connectBranch(repositoryId: String, branchId: String): Promise<BranchJS> {
        return GlobalScope.promise {
            val modelClient = modelClient
            val branchReference = RepositoryId(repositoryId).getBranchReference(branchId)
            val model: ReplicatedModel = modelClient.getReplicatedModel(branchReference)
            model.start()
            val branch = model.getBranch()
            val branchWithAutoTransaction = branch.withAutoTransactions()
            return@promise BranchJSImpl({ model.dispose() }, branchWithAutoTransaction)
        }
    }

    override fun dispose() {
        modelClient.close()
    }
}

typealias ChangeHandler = (ChangeJS) -> Unit

@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
interface BranchJS {
    val rootNode: INodeJS
    fun dispose()
    fun resolveNode(reference: INodeReferenceJS): INodeJS?
    fun addListener(handler: ChangeHandler)
    fun removeListener(handler: ChangeHandler)
}

fun toNodeJs(rootNode: INode) = JSNodeConverter.nodeToJs(rootNode).unsafeCast<INodeJS>()
