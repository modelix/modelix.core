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

package permissions

import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test

class UnknownPermissionGrantTest {
    /**
     * A token may contain granted permission of other services. They should not result in an exception.
     */
    @Test
    fun `unknown permission in token is ignored`() {
        val evaluator = PermissionEvaluator(SchemaInstance(ModelServerPermissionSchema.SCHEMA))
        for (i in 0..5) {
            evaluator.grantPermission(PermissionParts(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").push.parts.take(i)) + "some-non-existent-permission")
        }
    }
}
