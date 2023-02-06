package org.modelix.metamodel.generator

import java.io.Serializable

class NameConfig(
    languagePrefix: String = "L_",
    languageSuffix: String = "",
    nodeWrapperInterfacePrefix: String = "N_",
    nodeWrapperInterfaceSuffix: String = "",
    nodeWrapperImplPrefix: String = "_N_TypedImpl_",
    nodeWrapperImplSuffix: String = "",
    conceptObjectPrefix: String = "_C_UntypedImpl_",
    conceptObjectSuffix: String = "",
    conceptWrapperInterfacePrefix: String = "C_",
    conceptWrapperInterfaceSuffix: String = "",
    conceptWrapperImplPrefix: String = "_C_TypedImpl_",
    conceptWrapperImplSuffix: String = ""
) : Serializable {

    val language = NameConfigPair(languagePrefix, languageSuffix)
    val nodeWrapperInterface = NameConfigPair(nodeWrapperInterfacePrefix, nodeWrapperInterfaceSuffix)
    val nodeWrapperImpl = NameConfigPair(nodeWrapperImplPrefix, nodeWrapperImplSuffix)
    val conceptObject = NameConfigPair(conceptObjectPrefix, conceptObjectSuffix)
    val conceptWrapperInterface = NameConfigPair(conceptWrapperInterfacePrefix, conceptWrapperInterfaceSuffix)
    val conceptWrapperImpl = NameConfigPair(conceptWrapperImplPrefix, conceptWrapperImplSuffix)

    fun languageClassName(baseName: String) =
        language.prefix + baseName.replace(".", "_") + language.suffix

    fun nodeWrapperInterfaceName(baseName: String) =
        baseName.fqNamePrefix(nodeWrapperInterface.prefix, nodeWrapperInterface.suffix)

    fun nodeWrapperImplName(baseName: String) =
        baseName.fqNamePrefix(nodeWrapperImpl.prefix, nodeWrapperImpl.suffix)

    fun conceptObjectName(baseName: String) =
        baseName.fqNamePrefix(conceptObject.prefix, conceptObject.suffix)

    fun conceptWrapperInterfaceName(baseName: String) =
        baseName.fqNamePrefix(conceptWrapperInterface.prefix, conceptWrapperInterface.suffix)

    fun conceptWrapperImplName(baseName: String) =
        baseName.fqNamePrefix(conceptWrapperImpl.prefix, conceptWrapperImpl.suffix)

    private fun String.fqNamePrefix(prefix: String, suffix: String): String {
        return if (this.contains(".")) {
            this.substringBeforeLast(".") + "." + prefix + this.substringAfterLast(".")
        } else {
            prefix + this
        } + suffix
    }
}

data class NameConfigPair(var prefix: String, var suffix: String) : Serializable

