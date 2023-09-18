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
