import org.modelix.MODELIX_JDK_VERSION

plugins {
    kotlin("multiplatform")
    id("modelix-language-config")
}

kotlin {
    jvmToolchain(MODELIX_JDK_VERSION)
    js(IR) {
        browser {
            testTask {
                useMocha {
                    timeout = "60s"
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "60s"
                }
            }
        }
        useCommonJs()
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
}
