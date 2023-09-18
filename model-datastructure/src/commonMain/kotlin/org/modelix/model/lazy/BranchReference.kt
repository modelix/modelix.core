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
package org.modelix.model.lazy

data class BranchReference(val repositoryId: RepositoryId, val branchName: String) {
    init {
        require(branchName.matches(RepositoryId.VALID_ID_PATTERN)) { "Invalid branch name: $branchName" }
    }

    override fun toString(): String = getKey()

    @Deprecated("Use the new server API to access branches")
    fun getKey(): String = "branch_" + repositoryId.id + "_" + branchName

    companion object {
        private val LEGACY_PATTERN = Regex("""branch_(.+)_(.+)""")
        fun tryParseBranch(key: String): BranchReference? {
            val match = LEGACY_PATTERN.matchEntire(key) ?: return null
            return BranchReference(RepositoryId(match.groups[1]!!.value), match.groups[2]!!.value)
        }
    }
}
