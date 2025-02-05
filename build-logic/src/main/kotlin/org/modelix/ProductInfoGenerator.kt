package org.modelix

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class ProductInfoGenerator(val mpsHome: File) {
    fun generate(): String {
        val buildProperties = mpsHome.resolve("build.properties").readLines()
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
        val buildNumber = buildProperties["mps.build.number"]!!.removePrefix("MPS-")
        val majorVersion = buildProperties["mpsBootstrapCore.version"]!!
        val fullVersion = majorVersion + buildProperties["mpsBootstrapCore.version.bugfixNr"]!!

        return buildObject {
            primitive("name", "JetBrains MPS")
            primitive("version", fullVersion)
            primitive("buildNumber", buildNumber)
            primitive("productCode", "MPS")
            primitive("envVarBaseName", "MPS")
            primitive("dataDirectoryName", "MPS$majorVersion")
            primitive("svgIconPath", "bin/mps.svg")
            primitive("productVendor", "JetBrains")
            array("launch") {
                obj {
                    primitive("os", "Linux")
                    primitive("arch", "amd64")
                    primitive("launcherPath", "bin/mps.sh")
                    primitive("javaExecutablePath", "jbr/bin/java")
                    primitive("vmOptionsFilePath", "bin/mps64.vmoptions")
                    array("bootClassPathJarNames") {
                        listOf(
                            "annotations.jar",
                            "app.jar",
                            "bouncy-castle.jar",
                            "branding.jar",
                            "byte-buddy-agent.jar",
                            "eclipse.jar",
                            "error-prone-annotations.jar",
                            "external-system-rt.jar",
                            "externalProcess-rt.jar",
                            "forms_rt.jar",
                            "groovy.jar",
                            "grpc.jar",
                            "idea_rt.jar",
                            "intellij-coverage-agent-1.0.723.jar",
                            "intellij-test-discovery.jar",
                            "java-frontback.jar",
                            "java-impl.jar",
                            "javac2.jar",
                            "jetbrains-annotations.jar",
                            "jps-model.jar",
                            "junit4.jar",
                            "kotlin-compiler-client-embeddable-1.9.20.jar",
                            "kotlinx-metadata-jvm-0.7.0.jar",
                            "lib.jar",
                            "mps-annotations.jar",
                            "mps-behavior-api.jar",
                            "mps-behavior-runtime.jar",
                            "mps-boot-util.jar",
                            "mps-boot.jar",
                            "mps-closures.jar",
                            "mps-collections.jar",
                            "mps-constraints-runtime.jar",
                            "mps-context.jar",
                            "mps-core.jar",
                            "mps-editor-api.jar",
                            "mps-editor-runtime.jar",
                            "mps-editor.jar",
                            "mps-environment.jar",
                            "mps-feedback-api.jar",
                            "mps-generator.jar",
                            "mps-icons.jar",
                            "mps-messages-api.jar",
                            "mps-messages-for-legacy-constraints.jar",
                            "mps-messages-for-rules.jar",
                            "mps-messages-for-structure.jar",
                            "mps-openapi.jar",
                            "mps-persistence.jar",
                            "mps-platform.jar",
                            "mps-problem.jar",
                            "mps-project-check.jar",
                            "mps-references.jar",
                            "mps-resources.jar",
                            "mps-resources_en.jar",
                            "mps-scripts-rt.jar",
                            "mps-test.jar",
                            "mps-textgen.jar",
                            "mps-tips.jar",
                            "mps-tuples.jar",
                            "mps-workbench.jar",
                            "nio-fs.jar",
                            "opentelemetry.jar",
                            "platform-loader.jar",
                            "protobuf.jar",
                            "pty4j.jar",
                            "rd.jar",
                            "stats.jar",
                            "testFramework.jar",
                            "trove.jar",
                            "util.jar",
                            "util-8.jar",
                            "util_rt.jar",
                            "ant/lib/ant.jar",
                            "boot-macos.jar"
                        ).forEach { primitive(it) }
                    }
                    array("additionalJvmArguments") {
                        listOf(
                            "-XX:+UseCompressedOops",
                            "-XX:ErrorFile=\$USER_HOME/java_error_in_idea_%p.log",
                            "-XX:HeapDumpPath=\$USER_HOME/java_error_in_idea.hprof",
                            "--add-opens=java.base/java.io=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                            "--add-opens=java.base/java.net=ALL-UNNAMED",
                            "--add-opens=java.base/java.nio=ALL-UNNAMED",
                            "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
                            "--add-opens=java.base/java.text=ALL-UNNAMED",
                            "--add-opens=java.base/java.time=ALL-UNNAMED",
                            "--add-opens=java.base/java.util=ALL-UNNAMED",
                            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                            "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                            "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
                            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                            "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
                            "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
                            "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
                            "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
                            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                            "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
                            "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
                            "--add-opens=java.management/sun.management=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
                            "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
                            "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
                            "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
                            "-Didea.vendor.name=JetBrains",
                            "-Didea.paths.selector=MPS$majorVersion",
                            "-Djna.boot.library.path=\$APP_PACKAGE/Contents/lib/jna",
                            "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
                            "-Dintellij.platform.load.app.info.from.resources=true",
                            "-Didea.executables=mps",
                            "-Didea.home.path=\$APP_PACKAGE/Contents",
                            "-Dpty4j.preferred.native.folder=\$APP_PACKAGE/Contents/lib/pty4j",
                            "-DjbScreenMenuBar.enabled=true",
                            "-DjbScreenMenuBar.useStubItem=true",
                            "-Dide.home.macro.test=\$APP_PACKAGE/Contents",
                            "-Dcache.dir.macro.test=\$IDE_CACHE_DIR",
                            "-Dij.startup.error.report.url=https://youtrack.jetbrains.com/newissue?project=MPS&clearDraft=true&summary=\$TITLE$&description=\$DESCR$"
                        ).forEach { primitive(it) }
                    }
                    primitive("mainClass", "jetbrains.mps.Launcher")
                }
            }
        }.let { Json { prettyPrint = true }.encodeToString(it) }
    }

}

private fun buildObject(body: ObjBuilder.() -> Unit): JsonObject {
    return ObjBuilder().apply(body).build()
}

private fun buildArray(body: ArrayBuilder.() -> Unit): JsonArray {
    return ArrayBuilder().apply(body).build()
}

@JsonBuilderMarker
private class ObjBuilder {
    private val content = LinkedHashMap<String, JsonElement>()

    fun primitive(key: String, value: String) {
        content.put(key, JsonPrimitive(value))
    }

    fun array(key: String, body: ArrayBuilder.() -> Unit) {
        content.put(key, buildArray(body))
    }

    fun build(): JsonObject = JsonObject(content.toMap())
}

@DslMarker
private annotation class JsonBuilderMarker

@JsonBuilderMarker
private class ArrayBuilder {
    private val content = ArrayList<JsonElement>()

    fun primitive(value: String) {
        content.add(JsonPrimitive(value))
    }

    fun obj(body: ObjBuilder.() -> Unit) {
        content.add(buildObject(body))
    }

    fun build(): JsonArray = JsonArray(content.toList())
}
