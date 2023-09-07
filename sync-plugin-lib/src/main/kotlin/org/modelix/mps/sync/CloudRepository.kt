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
import org.modelix.mps.sync.connection.ModelServerConnection

class CloudRepository(private val modelServer: ModelServerConnection, private val repositoryId: RepositoryId) :
    ICloudRepository {

    override fun getBranch(): IBranch {
        TODO("Not yet implemented")
    }

    override fun getActiveBranch(): IIndirectBranch {
        TODO("Not yet implemented")
    }

    override fun completeId(): String {
        TODO("Not yet implemented")
    }

    override fun getRepositoryId(): RepositoryId {
        TODO("Not yet implemented")
    }
}
