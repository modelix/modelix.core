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

package org.modelix.model.async

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

interface IAsyncValue<out E> {
    fun onReceive(callback: (E) -> Unit)
    fun <R> map(body: (E) -> R): IAsyncValue<R>
    fun <R> flatMap(body: (E) -> IAsyncValue<R>): IAsyncValue<R>
    fun awaitBlocking(): E
    suspend fun await(): E

    companion object {
        private val NULL_CONSTANT = NonAsyncValue<Any?>(null)
        val UNIT = NonAsyncValue<Unit>(Unit)
        fun <T> constant(value: T): IAsyncValue<T> = NonAsyncValue(value)
        fun <T> nullConstant(): IAsyncValue<T?> = NULL_CONSTANT as IAsyncValue<T?>
    }
}

fun <T> T.asAsync(): IAsyncValue<T> = IAsyncValue.constant(this)
fun <T> IAsyncValue<T>.asNonAsync(): T = (this as NonAsyncValue<T>).value

fun <T> IAsyncValue<T>.asFlow() = flow<T> { emit(await()) }
fun <T> IAsyncValue<Iterable<T>>.asFlattenedFlow() = asFlow().flatMapConcat { it.asFlow() }

fun <T> List<IAsyncValue<T>>.mapList(): IAsyncValue<List<T>> = mapList { it }

fun <T, R> List<IAsyncValue<T>>.mapList(body: (List<T>) -> R): IAsyncValue<R> {
    val input = this
    if (input.isEmpty()) {
        return body(emptyList()).asAsync()
    }
    val output = arrayOfNulls<Any>(input.size)
    val done = BooleanArray(input.size)
    var remaining = input.size
    val result = AsyncValue<R>()
    for (i in input.indices) {
        input[i].onReceive {
            if (done[i]) return@onReceive
            done[i] = true
            output[i] = it
            remaining--
            if (remaining == 0) {
                result.done(body(output.asList() as List<T>))
            }
        }
    }
    return result
}

fun <T1, T2, R> Pair<IAsyncValue<T1>, IAsyncValue<T2>>.flatMapBoth(body: (T1, T2) -> IAsyncValue<R>): IAsyncValue<R> {
    return mapBoth(body).flatten()
}

fun <T1, T2, R> Pair<IAsyncValue<T1>, IAsyncValue<T2>>.mapBoth(body: (T1, T2) -> R): IAsyncValue<R> {
    return toList().mapList { body(it[0] as T1, it[1] as T2) }
}

fun <T> IAsyncValue<IAsyncValue<T>>.flatten(): IAsyncValue<T> {
    return flatMap { it }
}

fun <T : Any> IAsyncValue<T?>.checkNotNull(message: () -> String): IAsyncValue<T> {
    return map { checkNotNull(it, message) }
}

class NonAsyncValue<out E>(val value: E) : IAsyncValue<E> {
    override fun <R> flatMap(body: (E) -> IAsyncValue<R>): IAsyncValue<R> {
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
}

class AsyncValue<E> : IAsyncValue<E> {
    private val value: CompletableDeferred<E> = CompletableDeferred()

    fun done(value: E) {
        this.value.complete(value)
    }

    override fun awaitBlocking(): E {
        return value.getCompleted()
    }

    override suspend fun await(): E {
        return value.await()
    }

    override fun onReceive(callback: (E) -> Unit) {
        value.invokeOnCompletion {
            callback(value.getCompleted())
        }
    }

    override fun <R> map(body: (E) -> R): IAsyncValue<R> {
        val output = AsyncValue<R>()
        onReceive {
            output.done(body(it))
        }
        return output
    }

    override fun <R> flatMap(body: (E) -> IAsyncValue<R>): IAsyncValue<R> {
        TODO("Not yet implemented")
    }
}
