plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.junit)
    testImplementation(libs.detekt.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
