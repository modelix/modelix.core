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

package org.modelix.streams

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.collect
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.firstOrError
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.flatten
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.repeat
import com.badoo.reaktive.observable.replay
import com.badoo.reaktive.observable.switchIfEmpty
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.observable.toObservable
import com.badoo.reaktive.observable.zipWith
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.repeat

fun Observable<*>.count(): Single<Int> = collect({ arrayOf(0) }) { acc, it -> acc[0]++ }.map { it[0] }
fun <T, R> Observable<T>.fold(initial: R, operation: (R, T) -> R): Single<R> = collect({ mutableListOf(initial) }) { acc, it -> acc[0] = operation(acc[0], it) }.map { it[0] }
fun <T> Sequence<T>.asObservable(): Observable<T> = asIterable().asObservable()
fun <T> Observable<T>.exactlyOne(): Single<T> = toList().map { it.single() }
fun <T> Observable<T>.firstOrNull(): Single<T?> = firstOrDefault(null)
fun Observable<*>.isEmpty(): Single<Boolean> = map { false }.firstOrDefault(true)
fun <T> Observable<T>.filterBySingle(condition: (T) -> Single<Boolean>): Observable<T> {
    return this.zipWith(this.flatMapSingle { condition(it) }) { it, c -> it to c }.filter { it.second }.map { it.first }
}
fun <T> Observable<T>.withIndex(): Observable<IndexedValue<T>> {
    var index = 0
    return map { IndexedValue(index++, it) }
}
fun <T> Observable<T>.assertNotEmpty(message: () -> String): Observable<T> {
    return this.switchIfEmpty { throw RuntimeException("Empty stream: " + message()) }
}
fun <T> Single<T>.cached(): Single<T> = this.asObservable().replay(1).firstOrError { RuntimeException() }
fun <T> Maybe<T>.orNull(): Single<T?> = defaultIfEmpty(null)
