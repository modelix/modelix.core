package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

fun <T, R> Flow<T>.batchTransform(batchSize: Int, transform: (List<T>) -> List<R>): Flow<R> {
    return chunked(batchSize).flatMapConcat { transform(it).asFlow() }
}

fun <T> Flow<T>.chunked(chunkSize: Int): Flow<List<T>> {
    val input = this
    return flow<List<T>> {
        var list = ArrayList<T>(chunkSize)
        input.collect {
            if (list.size < chunkSize) {
                list.add(it)
            } else {
                emit(list)
                list = ArrayList<T>(chunkSize)
            }
        }
        if (list.isNotEmpty()) emit(list)
    }
}

