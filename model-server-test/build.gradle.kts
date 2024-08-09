plugins {
    `modelix-kotlin-jvm-with-junit`
    alias(libs.plugins.docker.compose)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":model-server"))
}

tasks.test {
    dependsOn(":model-server:compileTestKotlin")
    doFirst {
        val db = dockerCompose.servicesInfos.getValue("db")
        systemProperty("jdbc.url", "jdbc:postgresql://${db.host}:${db.port}/")
    }
}

dockerCompose {
    isRequiredBy(tasks.test)
    setProjectName("model-server-test-started-from-gradle")
}
