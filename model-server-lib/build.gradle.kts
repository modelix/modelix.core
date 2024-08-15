plugins {
    `modelix-kotlin-jvm-with-junit`
    `maven-publish`
}

dependencies {
    implementation(project(":model-api"))
    implementation(project(":model-server-api"))
    implementation(project(":modelql-core"))
    implementation(project(":modelql-server"))
    implementation(project(":modelql-untyped"))

    implementation(kotlin("stdlib"))
    implementation(libs.modelix.incremental)

    implementation(libs.kotlin.serialization.json)

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.swing)
    implementation(libs.kotlin.logging)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
