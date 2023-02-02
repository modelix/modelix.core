package org.modelix.metamodel.generator

class NameConfig(
    private val languagePrefix: String = "",
    private val languageSuffix: String = "Lang",
    private val nodeWrapperInterfacePrefix: String = "",
    private val nodeWrapperInterfaceSuffix: String = "Node",
    private val nodeWrapperImplPrefix: String = "",
    private val nodeWrapperImplSuffix: String = "NodeTypedImpl",
    private val conceptObjectPrefix: String = "",
    private val conceptObjectSuffix: String = "ConceptUntypedImpl",
    private val conceptWrapperInterfacePrefix: String = "",
    private val conceptWrapperInterfaceSuffix: String = "Concept",
    private val conceptWrapperImplPrefix: String = "",
    private val conceptWrapperImplSuffix: String = "ConceptTypedImpl"
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

