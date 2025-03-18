package org.modelix.model.lazy

open class BulkQueryConfiguration {
    /**
     * The maximum number of objects that is requested in one request.
     */
    var requestBatchSize: Int = defaultRequestBatchSize

    companion object {
        var defaultRequestBatchSize: Int = 5_000
    }
}
