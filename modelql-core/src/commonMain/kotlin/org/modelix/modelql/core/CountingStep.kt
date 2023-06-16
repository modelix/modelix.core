package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class CountingStep() : FoldingStep<Any?, Int>(0) {
    override fun fold(s: Int, a: Any?): Int = s + 1

    override fun createDescriptor() = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return CountingStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Int> {
        return serializersModule.serializer<Int>()
    }

    override fun toString(): String {
        return "${getProducers().single()}.count()"
    }
}

fun IProducingStep<Any?>.size() = count()
fun IProducingStep<Any?>.count() = CountingStep().also { connect(it) }
fun IProducingStep<Any?>.isEmpty(): IMonoStep<Boolean> = count().map { it.equalTo(0) }
fun IProducingStep<Any?>.isNotEmpty(): IMonoStep<Boolean> = !isEmpty()

