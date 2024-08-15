/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.api.async

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import org.modelix.kotlin.utils.IStream
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.IMonoStream
import org.modelix.kotlin.utils.mapMono
import org.modelix.kotlin.utils.mapValue
import org.modelix.kotlin.utils.toMono

/**
 * Unlike Flows, IAsyncValue and IAsyncSequence are computed as soon a possible and not only when the value is accessed.
 * This gives IBulkQuery more control over the execution and allows it to effectively combine requests into bigger ones.
 */
interface IAsyncValue<out E> {
    fun onReceive(callback: (E) -> Unit)
    fun <R> map(body: (E) -> R): IAsyncValue<R>
    fun <R> flatMap(body: (E) -> Iterable<R>): IAsyncSequence<R>
    fun <R> thenRequest(body: (E) -> IAsyncValue<R>): IAsyncValue<R>
    fun awaitBlocking(): E
    suspend fun await(): E
    fun asFlow(): IMonoFlow<E>
    fun asStream(): IMonoStream<E>

    companion object {
        private val NULL_CONSTANT = NonAsyncValue<Any?>(null)
        val UNIT = NonAsyncValue<Unit>(Unit)
        fun <T> constant(value: T): IAsyncValue<T> = NonAsyncValue(value)
        fun <T> nullConstant(): IAsyncValue<T?> = NULL_CONSTANT as IAsyncValue<T?>
    }
}

interface IAsyncSequence<out E> {
    fun onEach(callback: (E) -> Unit)
    fun <R> map(transform: (E) -> R): IAsyncSequence<R>
    fun <R> thenRequest(transform: (E) -> IAsyncValue<R>): IAsyncSequence<R>
    fun <R> thenRequestMany(transform: (E) -> IAsyncSequence<R>): IAsyncSequence<R>
    fun toList(): IAsyncValue<List<E>>
    fun toSequence(): IAsyncValue<Sequence<E>>
    fun toSet(): IAsyncValue<Set<E>>
    fun visitAll(visitor: (E) -> Unit): IAsyncValue<Unit>
    fun asFlow(): Flow<E> = AsyncSequenceAsFlow(this)
    fun asFlowBuilder(): IStream<E>
}

fun <T> IAsyncSequence<T>.distinct(): IAsyncSequence<T> = TODO()
fun <T> IAsyncSequence<T>.filter(condition: (T) -> Boolean): IAsyncSequence<T> = TODO()
fun <T, R : Any> IAsyncSequence<T>.mapNotNull(transform: (T) -> R?): IAsyncSequence<R> = TODO()

fun <T> T.asAsync(): IAsyncValue<T> = IAsyncValue.constant(this)
fun <T> IAsyncValue<T>.asNonAsync(): T = (this as NonAsyncValue<T>).value

fun <T> List<IAsyncValue<T>>.requestAll(): IAsyncValue<List<T>> = requestAllAndMap { it }

fun <T, R> List<IAsyncValue<T>>.requestAllAndMap(body: (List<T>) -> R): IAsyncValue<R> {
    val input = this
    if (input.isEmpty()) {
        return body(emptyList()).asAsync()
    }
    val output = arrayOfNulls<Any>(input.size)
    val done = BooleanArray(input.size)
    var remaining = input.size
    val result = CompletableDeferred<R>()
    for (i in input.indices) {
        input[i].onReceive {
            if (done[i]) return@onReceive
            done[i] = true
            output[i] = it
            remaining--
            if (remaining == 0) {
                result.complete(body(output.asList() as List<T>))
            }
        }
    }
    return DeferredAsAsyncValue(result) { input.forEach { it.await() } }
}

fun <T1, T2, R> Pair<IAsyncValue<T1>, IAsyncValue<T2>>.flatMapBoth(body: (T1, T2) -> IAsyncValue<R>): IAsyncValue<R> {
    return mapBoth(body).flatten()
}

fun <T1, T2, R> Pair<IAsyncValue<T1>, IAsyncValue<T2>>.mapBoth(body: (T1, T2) -> R): IAsyncValue<R> {
    return toList().requestAllAndMap { body(it[0] as T1, it[1] as T2) }
}

fun <T> IAsyncValue<IAsyncValue<T>>.flatten(): IAsyncValue<T> {
    return thenRequest { it }
}

fun <T : Any> IAsyncValue<T?>.checkNotNull(message: () -> String): IAsyncValue<T> {
    return map { checkNotNull(it, message) }
}

class NonAsyncValue<out E>(val value: E) : IAsyncValue<E>, IMonoFlow<E> {
    override fun asStream(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun <R> thenRequest(body: (E) -> IAsyncValue<R>): IAsyncValue<R> {
        return body(value)
    }

    override fun onReceive(callback: (E) -> Unit) {
        callback(value)
    }

    override fun <R> map(body: (E) -> R): IAsyncValue<R> {
        return NonAsyncValue(body(value))
    }

    override fun awaitBlocking(): E {
        return value
    }

    override suspend fun await(): E {
        return value
    }

    override suspend fun collect(collector: FlowCollector<E>) {
        collector.emit(value)
    }

    override fun asFlow(): IMonoFlow<E> {
        return this
    }

    override fun <R> flatMap(body: (E) -> Iterable<R>): IAsyncSequence<R> {
        TODO("Not yet implemented")
    }
}

class MonoFlowAsAsyncValue<E>(val flow: IMonoFlow<E>): IAsyncValue<E> {
    override fun asFlow(): IMonoFlow<E> {
        return flow
    }

    override fun asStream(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun onReceive(callback: (E) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun <R> map(body: (E) -> R): IAsyncValue<R> {
        return MonoFlowAsAsyncValue(flow.mapValue(body))
    }

    override fun <R> thenRequest(body: (E) -> IAsyncValue<R>): IAsyncValue<R> {
        return MonoFlowAsAsyncValue(flow.mapMono { body(it).asFlow() })
    }

    override fun awaitBlocking(): E {
        TODO("Not yet implemented")
    }

    override suspend fun await(): E {
        return flow.single()
    }

    override fun <R> flatMap(body: (E) -> Iterable<R>): IAsyncSequence<R> {
        TODO("Not yet implemented")
    }
}

class DeferredAsAsyncValue<E>(val value: Deferred<E>, val enforceCompletion: suspend () -> Unit = {}) : IAsyncValue<E> {
    private val creationStack = Exception()
    override fun asStream(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun awaitBlocking(): E {
        return value.getCompleted()
    }

    override suspend fun await(): E {
        if (!value.isCompleted) enforceCompletion()
        return value.getCompleted()
    }

    override fun onReceive(callback: (E) -> Unit) {
        value.invokeOnCompletion {
            callback(value.getCompleted())
        }
    }

    override fun <R> map(body: (E) -> R): IAsyncValue<R> {
        return MappingAsyncValue(this, body)
    }

    override fun <R> thenRequest(body: (E) -> IAsyncValue<R>): IAsyncValue<R> {
        val result = CompletableDeferred<R>()
        onReceive {
            val fromBody = body(it)
            fromBody.onReceive { result.complete(it) }
        }
        return DeferredAsAsyncValue(result, enforceCompletion)
    }

    override fun asFlow(): IMonoFlow<E> {
        return flow<E> {
            if (!value.isCompleted) enforceCompletion()
            if (!value.isCompleted) enforceCompletion()
            emit(value.getCompleted())
        }.toMono()
    }

    override fun <R> flatMap(body: (E) -> Iterable<R>): IAsyncSequence<R> {
        TODO("Not yet implemented")
    }
}

class MappingAsyncValue<In, Out>(val input: IAsyncValue<In>, val mappingFunction: (In) -> Out): IAsyncValue<Out> {
    override fun asStream(): IMonoStream<Out> {
        TODO("Not yet implemented")
    }

    override fun asFlow(): IMonoFlow<Out> {
        return input.asFlow().map(mappingFunction).toMono()
    }

    override fun onReceive(callback: (Out) -> Unit) {
        input.onReceive { callback(mappingFunction(it)) }
    }

    override fun <R> map(body: (Out) -> R): IAsyncValue<R> {
        return MappingAsyncValue(this, body)
    }

    override fun <R> thenRequest(body: (Out) -> IAsyncValue<R>): IAsyncValue<R> {
        val result = CompletableDeferred<R>()
        onReceive { body(it).onReceive { result.complete(it) } }
        return DeferredAsAsyncValue(result)
    }

    override fun awaitBlocking(): Out {
        return mappingFunction(input.awaitBlocking())
    }

    override suspend fun await(): Out {
        return mappingFunction(input.await())
    }

    override fun <R> flatMap(body: (Out) -> Iterable<R>): IAsyncSequence<R> {
        TODO("Not yet implemented")
    }
}

class DeferredAsFlow<E>(val deferred: Deferred<E>): Flow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        collector.emit(deferred.await())
    }
}

fun <T> Deferred<T>.asFlow(): Flow<T> = DeferredAsFlow(this)

class AsyncSequenceAsFlow<E>(val sequence: IAsyncSequence<E>): Flow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        channelFlow<E> {
            sequence.visitAll { trySend(it) }.await()
        }.collect(collector)
    }
}

class AsyncSequence<E>(val sequence: IAsyncValue<Sequence<E>>): IAsyncSequence<E> {

    override fun toSequence(): IAsyncValue<Sequence<E>> {
        return sequence
    }

    override fun asFlowBuilder(): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun onEach(callback: (E) -> Unit) {
        sequence.onReceive {
            it.forEach(callback)
        }
    }

    override fun <R> map(transform: (E) -> R): IAsyncSequence<R> {
        return AsyncSequence(sequence.map { it.map(transform) })
    }

    override fun <R> thenRequest(transform: (E) -> IAsyncValue<R>): IAsyncSequence<R> {
        return AsyncSequence(sequence.map { it.toList().map(transform).requestAll().map { it.asSequence() } }.flatten())
    }

    override fun <R> thenRequestMany(transform: (E) -> IAsyncSequence<R>): IAsyncSequence<R> {
        return AsyncSequence(sequence.thenRequest { it.map { transform(it).toSequence() }.toList().requestAllAndMap { it.asSequence().flatten() } })
    }

    override fun toList(): IAsyncValue<List<E>> {
        return sequence.map { it.toList() }
    }

    override fun toSet(): IAsyncValue<Set<E>> {
        return sequence.map { it.toSet() }
    }

    override fun visitAll(visitor: (E) -> Unit): IAsyncValue<Unit> {
        return sequence.map {
            it.forEach(visitor)
        }
    }
}

fun <T> Sequence<T>.asAsyncSequence(): IAsyncSequence<T> = AsyncSequence(this.asAsync())
fun <T> List<T>.asAsyncSequence(): IAsyncSequence<T> = asSequence().asAsyncSequence()
