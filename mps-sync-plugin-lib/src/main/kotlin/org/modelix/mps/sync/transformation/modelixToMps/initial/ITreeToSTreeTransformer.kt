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

package org.modelix.mps.sync.transformation.modelixToMps.initial

import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.Collections

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ITreeToSTreeTransformer(branch: IBranch, mpsLanguageRepository: MPSLanguageRepository) {

    private val logger = KotlinLogging.logger {}

    private val moduleTransformer = ModuleTransformer(branch, mpsLanguageRepository)

    fun transform(entryPoint: INode): Iterable<IBinding> {
        require(entryPoint.isModule()) { "Transformation entry point (Node $entryPoint) must be a Module" }

        return try {
            @Suppress("UNCHECKED_CAST")
            moduleTransformer.transformToModuleCompletely(entryPoint.nodeIdAsLong(), true)
                .getResult().get() as Iterable<IBinding>
        } catch (ex: Exception) {
            logger.error(ex) { "Transformation of Node tree starting from Node $entryPoint failed." }
            Collections.emptyList()
        }
    }
}
