package org.modelix.metamodel.generator

class NameConfig(
    private val languagePrefix: String = "L_",
    private val nodeWrapperInterfacePrefix: String = "N_",
    private val nodeWrapperImplPrefix: String = "_N_TypedImpl_",
    private val conceptObjectPrefix: String = "_C_UntypedImpl_",
    private val conceptWrapperInterfacePrefix: String = "C_",
    private val conceptWrapperImplPrefix: String = "_C_TypedImpl_"
) {

    fun languageClassName(baseName: String) = languagePrefix + baseName.replace(".", "_")
    fun nodeWrapperInterfaceName(baseName: String) = baseName.fqNamePrefix(nodeWrapperInterfacePrefix)
    fun nodeWrapperImplName(baseName: String) = baseName.fqNamePrefix(nodeWrapperImplPrefix)
    fun conceptObjectName(baseName: String) = baseName.fqNamePrefix(conceptObjectPrefix)
    fun conceptWrapperInterfaceName(baseName: String) = baseName.fqNamePrefix(conceptWrapperInterfacePrefix)
    fun conceptWrapperImplName(baseName: String) = baseName.fqNamePrefix(conceptWrapperImplPrefix)

    private fun String.fqNamePrefix(prefix: String, suffix: String = ""): String {
        return if (this.contains(".")) {
            this.substringBeforeLast(".") + "." + prefix + this.substringAfterLast(".")
        } else {
            prefix + this
        } + suffix
    }
}

