plugins {
    `modelix-kotlin-jvm-with-junit`
    `maven-publish`
}

dependencies {
    implementation(project(":model-api"))
    implementation(project(":model-server-api"))
    implementation(project(":modelql-core"))
    implementation(project(":modelql-untyped"))

    implementation(kotlin("stdlib"))

    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.logging)
    implementation(libs.ktor.server.core)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
