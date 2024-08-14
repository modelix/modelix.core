description = "Library that checks is allowed to do something"

plugins {
    `modelix-kotlin-jvm`
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.serialization.yaml)
    implementation(libs.keycloak.authz.client)
    implementation(libs.guava)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
        }
    }
}
