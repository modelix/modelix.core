/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.core

class ZipBuilder {
    private val valueRequests = ArrayList<ValueRequest<Any?>>()

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        val allRequestSteps: List<IMonoStep<Any?>> = valueRequests.map { it.step }
        return zipList(*allRequestSteps.toTypedArray())
    }

    fun <T> withResult(result: IZipOutput<*>, body: () -> T): T {
        val allRequests: List<Request<Any?>> = valueRequests
        require(valueRequests.size == result.values.size) { "${valueRequests.size} values expected, but only ${result.values.size} received" }

        // for recursive calls save and restore the current values
        val savedValues = allRequests.map { it.result }
        try {
            allRequests.zip(result.values).forEach { (request, value) ->
                request.set(value)
            }
            return body()
        } finally {
            allRequests.zip(savedValues).forEach { (request, value) ->
                request.result = value
            }
        }
    }

    fun <T> request(input: IMonoStep<T>): IValueRequest<T> {
        return ValueRequest(input).also { valueRequests.add(it.downcast()) }
    }

    private abstract class Request<E> {
        var result: Optional<E> = Optional.empty()
        fun set(value: E) {
            result = Optional.of(value)
        }
        fun get(): E {
            require(result.isPresent()) { "Value not received for $this" }
            return result.get()
        }
    }

    private class ValueRequest<E>(step: IMonoStep<E>) : Request<E>(), IValueRequest<E> {
        val step: IMonoStep<E> = if (step.canBeEmpty()) step.assertNotEmpty() else step
        fun downcast(): ValueRequest<Any?> = this as ValueRequest<Any?>
    }
}
