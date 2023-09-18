/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.GlobalModelAccess
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference

data class MPSArea(val repository: SRepository) : IArea, IAreaReference {
    override fun getRoot(): INode {
        return MPSRepositoryAsNode(repository)
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolveConcept(ref: IConceptReference): IConcept? {
        return MPSLanguageRepository(repository).resolveConcept(ref.getUID())
    }

    override fun resolveNode(ref: INodeReference): INode? {
        return MPSNodeReference.tryConvert(ref)?.ref?.resolve(repository)?.let { MPSNode(it) }
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return resolveNode(ref)
    }

    override fun resolveBranch(id: String): IBranch? {
        return null
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        return this
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return takeIf { ref == it }
    }

    override fun <T> executeRead(f: () -> T): T {
        var result: T? = null
        repository.modelAccess.runReadAction {
            result = f()
        }
        return result!!
    }

    override fun <T> executeWrite(f: () -> T): T {
        var result: T? = null
        if (repository.modelAccess is GlobalModelAccess) {
            repository.modelAccess.runWriteAction { result = f() }
        } else {
            repository.modelAccess.executeCommand { result = f() }
        }
        return result!!
    }

    override fun canRead(): Boolean {
        return repository.modelAccess.canRead()
    }

    override fun canWrite(): Boolean {
        return repository.modelAccess.canWrite()
    }

    override fun addListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun removeListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }
}
