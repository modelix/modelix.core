import com.github.gradle.node.NodeExtension
import com.github.gradle.node.NodePlugin

plugins {
    base
    alias(libs.plugins.node) apply false
}

subprojects {
    plugins.withType<NodePlugin> {
        project.extensions.configure<NodeExtension> {
            version.set(libs.versions.node)
            download.set(true)
        }
    }
}
