description = "Library that checks is allowed to do something"

plugins {
    `modelix-kotlin-jvm-with-junit`
    kotlin("plugin.serialization")
}

java {
    withSourcesJar()
}

dependencies {
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.serialization.yaml)
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
    api(libs.nimbus.jose.jwt)
    runtimeOnly(libs.bouncycastle.bcpkix)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.coroutines.test)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
