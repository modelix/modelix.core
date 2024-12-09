import org.modelix.MODELIX_JDK_VERSION
import org.modelix.MODELIX_JVM_TARGET


plugins {
    kotlin("jvm")
    id("modelix-language-config")
}

kotlin {
    jvmToolchain(MODELIX_JDK_VERSION)
    compilerOptions {
        jvmTarget.set(MODELIX_JVM_TARGET)
    }
}
