package org.modelix.modelql.core

interface IBulkQueryExecutor {
    fun flush()
    fun <ParameterT, ResultT> request(type: IBulkRequestType<ParameterT, ResultT>, parameter: ParameterT, callback: (Result<ResultT>) -> Unit)
}

interface IBulkRequestType<ParameterT, ResultT> {
    fun executeRequest(keys: Collection<ParameterT>): Map<ParameterT, Result<ResultT>>
    fun getMaxBatchSize(): Int
}

typealias BulkRequestCallback<ResultT> = (Result<ResultT>) -> Unit

class BulkQueryExecutor : IBulkQueryExecutor {
    private val pending: MutableMap<IBulkRequestType<Any?, Any?>, MergedRequests<Any?, Any?>> = HashMap()

    override fun flush() {
        while (pending.isNotEmpty()) {
            for (entry in pending) {
                entry.value.flush()
            }
            pending -= pending.asSequence().filter { it.value.isEmpty() }.map { it.key }.toSet()
        }
    }

    override fun <ParameterT, ResultT> request(type: IBulkRequestType<ParameterT, ResultT>, parameter: ParameterT, callback: BulkRequestCallback<ResultT>) {
        val type2merged = (pending as MutableMap<IBulkRequestType<ParameterT, ResultT>, MergedRequests<ParameterT, ResultT>>)
            .getOrPut(type) { MergedRequests(type) }
            .add(parameter, callback)
    }

    private class MergedRequests<ParameterT, ResultT>(val type: IBulkRequestType<ParameterT, ResultT>) {
        private val queue = ArrayList<Pair<ParameterT, BulkRequestCallback<ResultT>>>()
        private var processing = false

        fun add(parameter: ParameterT, callback: BulkRequestCallback<ResultT>) {
            queue += parameter to callback
        }

        fun flush() {
            if (processing) {
                throw IllegalStateException("Already processing")
            }
            processing = true
            try {
                while (queue.isNotEmpty()) {
                    val currentRequests: List<Pair<ParameterT, BulkRequestCallback<ResultT>>>
                    val batchSize = type.getMaxBatchSize().coerceAtMost(queue.size)

                    // The callback of a request usually enqueues new requests until it reaches the leafs of the
                    // data structure. By executing the latest (instead of the oldest) request we do a depth first
                    // traversal, which keeps the maximum size of the queue smaller.
                    currentRequests = ArrayList(queue.subList(queue.size - batchSize, queue.size))
                    for (i in 1..batchSize) queue.removeLast()

                    val result = type.executeRequest(currentRequests.map { it.first }.toSet())
                    for (request in currentRequests) {
                        request.second(result[request.first]!!)
                    }
                }
            } finally {
                processing = false
            }
        }

        fun isEmpty() = queue.isEmpty()
    }
}
