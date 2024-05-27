plugins {
    kotlin("jvm")
    alias(libs.plugins.docker.compose)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":model-server"))
}

tasks.test {
    doFirst {
        val db = dockerCompose.servicesInfos.getValue("db")
        systemProperty("jdbc.url", "jdbc:postgresql://${db.host}:${db.port}/")
    }
}

dockerCompose {
    isRequiredBy(tasks.test)
    setProjectName("model-server-test-started-from-gradle")
}
