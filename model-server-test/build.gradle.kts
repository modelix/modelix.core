plugins {
    `modelix-kotlin-jvm-with-junit`
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":model-server"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}
