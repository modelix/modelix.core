package org.modelix.model.lazy

import org.modelix.model.randomUUID
import kotlin.jvm.JvmStatic

data class RepositoryId(val id: String) {
    init {
        require(id.matches(VALID_ID_PATTERN)) { "Invalid repository ID: $id" }
    }

    @Deprecated("Use getBranchReference().getKey()", ReplaceWith("getBranchReference(branchName).getKey()"))
    fun getBranchKey(branchName: String?): String {
        return getBranchReference(branchName).getKey()
    }

    @Deprecated("Use getBranchReference().getKey()", ReplaceWith("getBranchReference().getKey()"))
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
