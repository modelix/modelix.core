package org.modelix.model.client2

import kotlin.js.Promise

/**
 * Represents a branch two-way live replicated to the model server.
 */
@JsExport
interface ReplicatedModelJS {
    /**
     * Returns the live replicated branch on which further operation on the model can be done.
     */
    fun getBranch(): MutableModelTreeJs

    /**
     * Queries information about the latest written version.
     * Provides a subset of the data returned by [ReplicatedModel.getCurrentVersion]
     */
    fun getCurrentVersionInformation(): Promise<VersionInformationJS>

    /**
     * Closes the replicated model and stops replicating data.
     */
    fun dispose()
}
