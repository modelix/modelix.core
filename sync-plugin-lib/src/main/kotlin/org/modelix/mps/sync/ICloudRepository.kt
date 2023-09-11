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

import org.modelix.model.api.IBranch
import org.modelix.model.client.IIndirectBranch
import org.modelix.model.lazy.RepositoryId

// status: ready to test
interface ICloudRepository {

    fun getBranch(): IBranch
    fun getActiveBranch(): IIndirectBranch
    fun completeId(): String
    fun getRepositoryId(): RepositoryId
}
