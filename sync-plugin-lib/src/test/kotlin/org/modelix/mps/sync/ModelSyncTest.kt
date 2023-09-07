package org.modelix.mps.sync

import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import java.net.URL

public class ModelSyncTest {

    fun `can sync nodes`() {
        val syncService = SyncServiceImpl().bindRepository(
            URL("http://127.0.0.1"),
            BranchReference(RepositoryId("0"), "name"),
            "JWT",
            null,
            this::afterActivate,
        )
    }

    fun afterActivate() {
        println("afterActivate")
    }
}
