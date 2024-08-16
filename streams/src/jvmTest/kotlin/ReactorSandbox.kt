import com.badoo.reaktive.completable.completable
import com.badoo.reaktive.maybe.maybe
import com.badoo.reaktive.observable.collect
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.observable
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.serialize
import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.observable.toObservable
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.flatMap
import com.badoo.reaktive.single.flatMapIterable
import com.badoo.reaktive.single.singleOf
import com.badoo.reaktive.single.subscribe
import com.badoo.reaktive.single.toSingle
import kotlinx.coroutines.CompletableDeferred
import org.modelix.streams.CompletableObservable
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.extra.math.sum
import reactor.kotlin.extra.math.sumAsLong
import reactor.kotlin.test.test
import kotlin.test.Test
import kotlin.test.assertEquals

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

class ReactorSandbox {
    @Test
    fun test() {
        val requests = ArrayList<CompletableObservable<Int>>()
        val value = CompletableObservable<Int>().also { requests.add(it) }
        value.flatMapIterable { (0..10) }.flatMapSingle { CompletableObservable<Int>().also { requests.add(it) } }.subscribe {
            println("result: $it")
        }
        value.complete(1)
        (1..11).forEach { requests[it].complete(it) }
        assertEquals(12, requests.size)
    }
}