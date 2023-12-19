/*
 * Copyright (c) 2023.
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

import org.modelix.model.api.PNodeReference
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceSerializationTests {

    @Test
    fun deserializePNodeReferenceOldFormat() {
        assertEquals(
            PNodeReference(0xabcd1234, "2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
            PNodeReference.deserialize("pnode:abcd1234@2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
        )
    }

    @Test
    fun deserializePNodeReferenceNewFormat() {
        assertEquals(
            PNodeReference(0xabcd1234, "2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
            PNodeReference.deserialize("modelix:2bfd9f5e-95d0-11ee-b9d1-0242ac120002/abcd1234"),
        )
    }
}
