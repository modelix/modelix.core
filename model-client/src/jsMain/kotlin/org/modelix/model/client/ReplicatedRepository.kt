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
package org.modelix.model.client

import org.modelix.model.api.IBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId

actual class ReplicatedRepository actual constructor(
    client: IModelClient,
    branchReference: BranchReference,
    user: () -> String,
) {
    actual constructor(client: IModelClient, repositoryId: RepositoryId, branchName: String, user: () -> String) :
        this(client, repositoryId.getBranchReference(branchName), user) {
    }

    actual var localVersion: CLVersion?
        get() = TODO("Not yet implemented")
        private set(value) {}
    actual val branch: IBranch
        get() = TODO("Not yet implemented")

    actual fun dispose() {
    }

    actual fun isDisposed(): Boolean = TODO("Not yet implemented")
}
