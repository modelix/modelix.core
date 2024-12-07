import org.gradle.kotlin.dsl.invoke


plugins {
    id("modelix-kotlin-jvm")
}

tasks.test {
    useJUnitPlatform()
}
