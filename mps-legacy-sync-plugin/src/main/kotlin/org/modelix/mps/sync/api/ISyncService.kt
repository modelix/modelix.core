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

package org.modelix.mps.sync.api

import com.intellij.openapi.Disposable
import io.ktor.client.HttpClient
import io.ktor.http.Url
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.project.Project
import org.modelix.model.api.INode
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId

interface ISyncService {
    fun getBindings(): List<IBinding>
    fun getConnections(): List<IModelServerConnection>
    fun connectServer(httpClient: HttpClient?, baseUrl: Url): IModelServerConnection
    fun connectServer(baseUrl: String) = connectServer(null, Url(baseUrl))
}

interface IModelServerConnection : Disposable {
    fun getActiveBranches(): List<IBranchConnection>
    fun newBranchConnection(branchRef: BranchReference): IBranchConnection
    fun listRepositories(): List<RepositoryId>
    fun listBranches(): List<BranchReference>
    fun listBranches(repository: RepositoryId): List<BranchReference>
}

interface IBranchConnection : Disposable {
    fun getServerConnection(): IModelServerConnection
    fun switchBranch(branchName: String)
    fun bindProject(mpsProject: Project, existingProjectNodeId: Long?): IBinding
    fun bindModule(mpsModule: SModule?, existingModuleNodeId: Long?): IModuleBinding
    fun bindTransientModule(existingModuleNodeId: Long): IModuleBinding
    fun <R> readModel(body: (INode) -> R): R
    fun <R> writeModel(body: (INode) -> R): R
}

interface IBinding : Disposable {
    fun getConnection(): IBranchConnection

    /**
     * Wait until all pending sync operations are done
     */
    suspend fun flush()
}

interface IModuleBinding : IBinding {
    fun getModule(): SModule
}
