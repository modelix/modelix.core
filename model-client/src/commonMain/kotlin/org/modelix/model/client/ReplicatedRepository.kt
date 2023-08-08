package org.modelix.model.client

import org.modelix.model.api.IBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId

expect class ReplicatedRepository(client: IModelClient, branchReference: BranchReference, user: () -> String) {
    var localVersion: CLVersion?
        private set
    val branch: IBranch
    fun dispose()
    fun isDisposed(): Boolean

    constructor(client: IModelClient, repositoryId: RepositoryId, branchName: String, user: () -> String)
}
