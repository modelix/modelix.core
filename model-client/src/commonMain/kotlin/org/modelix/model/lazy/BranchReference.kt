package org.modelix.model.lazy

data class BranchReference(val repositoryId: RepositoryId, val branchName: String) {
    override fun toString(): String = getKey()

    fun getKey(): String = "branch_" + repositoryId.id + "_" + branchName
}