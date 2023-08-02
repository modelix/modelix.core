package org.modelix.modelql.core

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertTrue

class SerializerTest {

    @OptIn(InternalSerializationApi::class)
    @Test
    fun allStepsRegistered() {
        val missingSerializers = CoreStepDescriptor::class.sealedSubclasses.filter { subclass ->
            try {
                UnboundQuery.serializersModule.getPolymorphic(StepDescriptor::class, subclass.serializer().descriptor.serialName) == null
            } catch (ex: Exception) {
                throw RuntimeException("subclass: $subclass", ex)
            }
        }
        missingSerializers.forEach { subclass -> println("""subclass(${subclass.qualifiedName}::class)""") }
        assertTrue(missingSerializers.isEmpty(), "Descriptor subclasses not registered: $missingSerializers")
    }
}
