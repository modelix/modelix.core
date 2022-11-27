package org.modelix.model.client

import org.modelix.model.api.IBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId

actual class ReplicatedRepository actual constructor(
    client: IModelClient,
    branchReference: BranchReference,
    user: () -> String
) {
    actual constructor(client: IModelClient, repositoryId: RepositoryId, branchName: String, user: () -> String) :
            this(client, repositoryId.getBranchReference(branchName), user) {
    }

    actual var localVersion: CLVersion?
        get() = TODO("Not yet implemented")
        set(value) {}
    actual val branch: IBranch
        get() = TODO("Not yet implemented")

    actual fun dispose() {
    }

    actual fun isDisposed(): Boolean = TODO("Not yet implemented")
}
