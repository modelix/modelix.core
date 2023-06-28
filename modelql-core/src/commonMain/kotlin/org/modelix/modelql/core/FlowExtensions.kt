package org.modelix.modelql.core

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

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

/**
 * The result is the same as .flattenConcat(), but each Flow is collected in a separate coroutine.
 * This allows the bulk query to collect all low level request into bigger batches.
 */
fun <T> Flow<Flow<T>>.flattenConcatConcurrent(): Flow<T> {
    val nested = this
    return channelFlow {
        val results = Channel<Deferred<List<T>>>()
        coroutineScope {
            launch {
                nested.collect { inner ->
                    results.send(async { inner.toList() })
                }
                results.close()
            }
            launch {
                for (result in results) {
                    val list = result.await()
                    for (item in list) {
                        send(item)
                    }
                }
            }
        }
    }
}

fun <T, R> Flow<T>.flatMapConcatConcurrent(transform: suspend (T) -> Flow<R>): Flow<R> {
    return map(transform).flattenConcatConcurrent()
}

/**
 * Like .single(), but also allows an empty input.
 */
suspend fun <T> Flow<T>.optionalSingle(): Optional<T> {
    var result = Optional.empty<T>()
    collect {
        require(!result.isPresent()) { "Didn't expect multiple elements" }
        result = Optional.of(it)
    }
    return result
}

fun <T> Flow<T>.assertNotEmpty(additionalMessage: () -> String = { "" }): Flow<T> {
    return onEmpty { throw IllegalArgumentException("At least one element was expected. " + additionalMessage()) }
}

suspend fun <T> Flow<T>.asSequence(): Sequence<T> = toList().asSequence()
