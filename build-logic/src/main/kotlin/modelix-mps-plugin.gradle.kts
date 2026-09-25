import org.modelix.MPS_PLUGIN_SINCE_BUILD
import org.modelix.MPS_PLUGIN_UNTIL_BUILD
import org.modelix.mpsPlatformVersion

// Builds an MPS plugin, which is published as a zip with the artifact ID of the project name.

plugins {
    id("modelix-mps-platform")
    `maven-publish`
}

group = "org.modelix.mps"

intellijPlatform {
    autoReload = true
    pluginConfiguration {
        ideaVersion {
            sinceBuild = MPS_PLUGIN_SINCE_BUILD
            untilBuild = MPS_PLUGIN_UNTIL_BUILD
        }
    }
}

// Copies the plugin into the plugins folder of an MPS installation,
// configured by the property `mps<platform version>.plugins.dir` (e.g. mps251.plugins.dir) or `mps.plugins.dir`.
val mpsPluginDir = (findProperty("mps$mpsPlatformVersion.plugins.dir") ?: findProperty("mps.plugins.dir"))
    ?.toString()?.let { file(it) }
if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
    tasks.register<Sync>("installMpsPlugin") {
        from(tasks.prepareSandbox.flatMap { it.pluginDirectory })
        into(mpsPluginDir.resolve(project.name))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
