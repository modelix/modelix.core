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
package org.modelix.metamodel.generator

import java.io.Serializable

class NameConfig : Serializable {
    val languageClass = ConfigurableName(prefix = "L_", baseNameConversion = { it.replace(".", "_") })
    val typedNode = ConfigurableName(prefix = "N_")
    val typedNodeImpl = ConfigurableName(prefix = "_N_TypedImpl_")
    val untypedConcept = ConfigurableName(prefix = "_C_UntypedImpl_")
    val typedConcept = ConfigurableName(prefix = "C_")
    val typedConceptImpl = ConfigurableName(prefix = "_C_TypedImpl_")
    val conceptTypeAlias = ConfigurableName(prefix = "CN_")
}

private val UNMODIFIED_SIMPLE_NAME: (String) -> String = {
    require(!it.contains(".")) { "Simple name expected, but full-qualified name provided: $it" }
    it
}
class ConfigurableName(
    var prefix: String = "",
    var suffix: String = "",
    var baseNameConversion: (String) -> String = UNMODIFIED_SIMPLE_NAME,
) : Serializable {
    operator fun invoke(baseName: String): String {
        return prefix + baseNameConversion(baseName) + suffix
    }
}
