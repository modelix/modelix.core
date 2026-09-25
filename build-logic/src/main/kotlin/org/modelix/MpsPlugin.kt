package org.modelix

import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

/**
 * Copies the META-INF folder of the resources, with the patched plugin.xml, into the plugin folder of the sandbox.
 */
fun PrepareSandboxTask.includeMetaInfFolder() {
    from(project.layout.projectDirectory.dir("src/main/resources/META-INF")) {
        exclude("plugin.xml")
        into(pluginName.map { "$it/META-INF" })
    }
    from(project.tasks.named("patchPluginXml", PatchPluginXmlTask::class.java).flatMap { it.outputFile }) {
        into(pluginName.map { "$it/META-INF" })
    }
}
