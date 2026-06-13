plugins {
    `maven-publish`
    `modelix-kotlin-multiplatform`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
