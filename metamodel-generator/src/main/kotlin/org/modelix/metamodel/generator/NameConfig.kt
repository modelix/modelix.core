package org.modelix.metamodel.generator

class NameConfig(
    private val languagePrefix: String = "L_",
    private val languageSuffix: String = "",
    private val nodeWrapperInterfacePrefix: String = "N_",
    private val nodeWrapperInterfaceSuffix: String = "",
    private val nodeWrapperImplPrefix: String = "_N_TypedImpl_",
    private val nodeWrapperImplSuffix: String = "",
    private val conceptObjectPrefix: String = "_C_UntypedImpl_",
    private val conceptObjectSuffix: String = "",
    private val conceptWrapperInterfacePrefix: String = "C_",
    private val conceptWrapperInterfaceSuffix: String = "",
    private val conceptWrapperImplPrefix: String = "_C_TypedImpl_",
    private val conceptWrapperImplSuffix: String = ""
) {

    fun languageClassName(baseName: String) =
        languagePrefix + baseName.replace(".", "_") + languageSuffix

    fun nodeWrapperInterfaceName(baseName: String) =
        baseName.fqNamePrefix(nodeWrapperInterfacePrefix, nodeWrapperInterfaceSuffix)

    fun nodeWrapperImplName(baseName: String) =
        baseName.fqNamePrefix(nodeWrapperImplPrefix, nodeWrapperImplSuffix)

    fun conceptObjectName(baseName: String) =
        baseName.fqNamePrefix(conceptObjectPrefix, conceptObjectSuffix)

    fun conceptWrapperInterfaceName(baseName: String) =
        baseName.fqNamePrefix(conceptWrapperInterfacePrefix, conceptWrapperInterfaceSuffix)

    fun conceptWrapperImplName(baseName: String) =
        baseName.fqNamePrefix(conceptWrapperImplPrefix, conceptWrapperImplSuffix)

    private fun String.fqNamePrefix(prefix: String, suffix: String): String {
        return if (this.contains(".")) {
            this.substringBeforeLast(".") + "." + prefix + this.substringAfterLast(".")
        } else {
            prefix + this
        } + suffix
    }
}

