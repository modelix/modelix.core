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
            Query.serializersModule.getPolymorphic(StepDescriptor::class, subclass.serializer().descriptor.serialName) == null
        }
        missingSerializers.forEach { subclass -> println("""subclass(${subclass.qualifiedName}::class)""") }
        assertTrue(missingSerializers.isEmpty(), "Descriptor subclasses not registered: $missingSerializers")
    }

}