/*
 * Copyright (c) 2024.
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

import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.js.Promise

/**
 * Represents a branch two-way live replicated to the model server.
 */
@UnstableModelixFeature(
    reason = "The overarching task https://issues.modelix.org/issue/MODELIX-500 is in development.",
    intendedFinalization = "The client is intended to be finalized when the overarching task is finished.",
)
@JsExport
interface ReplicatedModelJS {
    /**
     * Returns the live replicated branch on which further operation on the model can be done.
     */
    fun getBranch(): BranchJS

    /**
     * Queries information about the latest written version.
     * Provides a subset of the data returned by [ReplicatedModel.getCurrentVersion]
     */
    fun getCurrentVersionInformation(): Promise<VersionInformationJS>

    /**
     * Closes the replicated model and stops replicating data.
     */
    fun dispose()
}
