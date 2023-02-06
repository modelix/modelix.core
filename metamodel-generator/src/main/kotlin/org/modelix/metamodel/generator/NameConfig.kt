package org.modelix.metamodel.generator

import java.io.Serializable

class NameConfig : Serializable {
    val languageClass = ConfigurableName(baseNameConversion = { it.replace(".", "_") })
    val nodeWrapperInterface = ConfigurableName(prefix = "N_")
    val nodeWrapperImpl = ConfigurableName(prefix = "_N_TypedImpl_")
    val conceptObject = ConfigurableName(prefix = "_C_UntypedImpl_")
    val conceptWrapperInterface = ConfigurableName(prefix = "C_")
    val conceptWrapperImpl = ConfigurableName(prefix = "_C_TypedImpl_")
}

private val UNMODIFED_SIMPLE_NAME: (String) -> String = {
    require(!it.contains(".")) { "Simple name expected, but full-qualified name provided: $it" }
    it
}
class ConfigurableName(
    var prefix: String = "",
    var suffix: String = "",
    var baseNameConversion: (String) -> String = UNMODIFED_SIMPLE_NAME
) : Serializable {
    operator fun invoke(baseName: String): String {
        return prefix + baseNameConversion(baseName) + suffix
    }
}

