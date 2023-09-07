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

package org.modelix.mps.sync.synchronization

import org.jetbrains.mps.openapi.model.SModel
import org.modelix.model.api.INode

/**
 * This represents just a pair of associated models: one physical and one on the cloud.
 */
class PhysicalToCloudModelMapping(physicalModel: SModel, cloudModel: INode) {
    private val physicalModel: SModel
    private val cloudModel: INode
    fun getPhysicalModel(): SModel {
        return physicalModel
    }

    fun getCloudModel(): INode {
        return cloudModel
    }

    init {
        this.physicalModel = physicalModel
        this.cloudModel = cloudModel
    }
}
