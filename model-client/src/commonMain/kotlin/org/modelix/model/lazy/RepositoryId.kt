/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License. 
 */

package org.modelix.model.lazy

import org.modelix.model.randomUUID
import kotlin.jvm.JvmStatic

data class RepositoryId(val id: String) {
    init {
        require(id.matches(VALID_ID_PATTERN)) { "Invalid repository ID: $id" }
    }

    @Deprecated("Use getBranchReference().getKey()")
    fun getBranchKey(branchName: String?): String {
        return getBranchReference(branchName).getKey()
    }

    @Deprecated("Use getBranchReference().getKey()")
    fun getBranchKey(): String = getBranchKey(null)

    fun getBranchReference(branchName: String? = DEFAULT_BRANCH): BranchReference {
        return BranchReference(this, (branchName ?: DEFAULT_BRANCH).ifEmpty { DEFAULT_BRANCH })
    }

    override fun toString(): String {
        return id
    }

    companion object {
        val VALID_ID_PATTERN = Regex("""[A-Za-z0-9_\-./]+""")
        const val DEFAULT_BRANCH = "master"

        @JvmStatic
        fun random(): RepositoryId {
            return RepositoryId(randomUUID())
        }
    }
}
