plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kotlin-utils"))
                implementation(libs.kotlin.coroutines.core)

                api(libs.reaktive)
                api(libs.reaktive.coroutines.interop)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlin.coroutines.test)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.trove4j)
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
