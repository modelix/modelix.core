/*
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
package org.modelix.modelql.core

abstract class AsyncTransformingStep<In, Out> : IConsumingStep<In>, ProducingStep<Out>() {
    private val pendingElements = ArrayList<PendingElement>()
    private var inputComplete = false
    private var producer: IProducingStep<In>? = null

    override fun addProducer(producer: IProducingStep<In>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    abstract fun transformAsync(inputElement: In, outputConsumer: IConsumer<Out>)

    override fun onNext(element: In, producer: IProducingStep<In>) {
        val pendingElement = PendingElement(element)
        pendingElements.add(pendingElement)
        transformAsync(element, pendingElement)
    }

    override fun onComplete(producer: IProducingStep<In>) {
        inputComplete = true
        process()
    }

    private fun process() {
        // TODO does this class need to be thread safe?
        while (pendingElements.isNotEmpty()) {
            val first = pendingElements.first()
            while (first.pendingOutputElements.isNotEmpty()) {
                forwardToConsumers(first.pendingOutputElements.removeFirst())
            }
            if (first.isComplete) {
                pendingElements.removeFirst()
            } else {
                break
            }
        }
        if (pendingElements.isEmpty() && inputComplete) {
            completeConsumers()
        }
    }

    private inner class PendingElement(val inputElement: In) : IConsumer<Out> {
        val pendingOutputElements = ArrayList<Out>()
        var isComplete = false

        override fun onNext(element: Out) {
            pendingOutputElements.add(element)
            process()
        }

        override fun onComplete() {
            isComplete = true
            process()
        }

        override fun onException(ex: Exception) {
            TODO("Not yet implemented")
        }
    }
}