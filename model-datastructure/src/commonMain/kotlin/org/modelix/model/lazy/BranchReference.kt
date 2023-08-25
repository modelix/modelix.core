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
