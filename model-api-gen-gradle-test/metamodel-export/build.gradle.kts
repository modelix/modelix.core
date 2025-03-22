plugins {
    base
    id("org.modelix.model-api-gen")
    java
}

val mpsDir = layout.buildDirectory.dir("mps")
val resolveMps by tasks.registering(Sync::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}
val mps by configurations.creating
val kotlinGenDir = project(":kotlin-generation").layout.buildDirectory.dir("kotlin_gen")
val typescriptGenDir = project(":typescript-generation").layout.buildDirectory.dir("typescript_src")

metamodel {
    mpsHeapSize = "2g"
    dependsOn(resolveMps)
    mpsHome = mpsDir.get().asFile
    kotlinDir = kotlinGenDir.get().asFile
    modelqlKotlinDir = kotlinGenDir.get().asFile
    kotlinProject = project
    typescriptDir = typescriptGenDir.get().asFile
    includeNamespace("jetbrains")
    exportModules("jetbrains.mps.baseLanguage")

    names {
        languageClass.prefix = "L_"
        languageClass.baseNameConversion = { it.replace(".", "_") }
        typedNode.prefix = ""
        typedNodeImpl.suffix = "Impl"
    }
    registrationHelperName = "org.modelix.apigen.test.ApigenTestLanguages"
    conceptPropertiesInterfaceName = "org.modelix.apigen.test.IMetaConceptProperties"
}

dependencies {
    mps("com.jetbrains:mps:2021.1.4")
}
