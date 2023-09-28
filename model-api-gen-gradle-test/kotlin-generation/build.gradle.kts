plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.modelix.model-api-gen") apply false
}

val modelixCoreVersion: String = projectDir.resolve("../../version.txt").readText()

dependencies {
    implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
    implementation("org.modelix:modelql-typed:$modelixCoreVersion")
    implementation("org.modelix:modelql-untyped:$modelixCoreVersion")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))

    testImplementation("org.modelix:model-api:$modelixCoreVersion")
    testImplementation("org.modelix:model-client:$modelixCoreVersion")
    testImplementation("org.modelix:model-server-lib:$modelixCoreVersion")
    testImplementation("org.modelix:modelql-client:$modelixCoreVersion")

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cors)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.html.builder)
    testImplementation(libs.ktor.server.auth)
    testImplementation(libs.ktor.server.auth.jwt)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.server.forwarded.header)
    testImplementation(libs.ktor.server.websockets)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets["main"].kotlin {
    srcDir(layout.buildDirectory.dir("kotlin_gen"))
}

tasks.compileKotlin {
    dependsOn(":metamodel-export:generateMetaModelSources")
}
