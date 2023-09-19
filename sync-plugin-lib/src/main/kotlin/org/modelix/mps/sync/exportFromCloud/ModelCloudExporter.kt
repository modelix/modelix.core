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

package org.modelix.mps.sync.exportFromCloud

import com.intellij.openapi.project.Project
import jetbrains.mps.project.Solution
import org.jetbrains.annotations.NonNls
import org.modelix.mps.sync.CloudRepository
import java.util.HashSet

class ModelCloudExporter(treeInRepository: CloudRepository) {
    fun setCheckoutMode() {
        TODO("Not yet implemented")
    }

    fun export(exportPath: @NonNls String?, moduleIds: HashSet<Long>, mpsProject: Project): List<Solution> {
        TODO("Not yet implemented")
    }
}
