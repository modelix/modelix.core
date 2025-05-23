plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":model-api"))

                implementation(kotlin("stdlib"))
                implementation(project(":model-datastructure"))
                implementation(libs.kotlin.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
            }
        }
        jvmTest {
            dependencies {
            }
        }
        jsMain {
            dependencies {
            }
        }
        jsTest {
            dependencies {
            }
        }
    }
}
