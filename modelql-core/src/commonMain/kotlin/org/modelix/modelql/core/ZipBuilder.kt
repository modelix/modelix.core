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
