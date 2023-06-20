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
    var baseNameConversion: (String) -> String = UNMODIFIED_SIMPLE_NAME
) : Serializable {
    operator fun invoke(baseName: String): String {
        return prefix + baseNameConversion(baseName) + suffix
    }
}

