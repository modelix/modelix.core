package org.modelix.model.sync.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties
import javax.inject.Inject

@CacheableTask
abstract class GenerateAntScriptForMps @Inject constructor(of: ObjectFactory) : DefaultTask() {

    @Input
    val mpsHomePath: Property<String> = of.property(String::class.java)

    @Input
    val mpsDependenciesPath: Property<String> = of.property(String::class.java)

    @Input
    val mpsHeapSize: Property<String> = of.property(String::class.java)

    @Input
    val repositoryPath: Property<String> = of.property(String::class.java)

    @Input
    val jsonDirPath: Property<String> = of.property(String::class.java)

    @Input
    val exportFlag: Property<Boolean> = of.property(Boolean::class.javaObjectType)

    @get:OutputFile
    val antScriptFile: RegularFileProperty = of.fileProperty()

    @Input
    val includedModules: ListProperty<String> = of.listProperty(String::class.java)

    @TaskAction
    fun generate() {
        val isExport = exportFlag.get()

        val antLibs = getAntLibs(File(mpsHomePath.get()))
        antScriptFile.get().asFile.parentFile.mkdirs()
        antScriptFile.get().asFile.writeText(
            """
        <project name="export-modules" default="build">
            <property name="build.dir" location="build" />
            <property name="build.mps.config.path" location="${"$"}{build.dir}/config" />
            <property name="build.mps.system.path" location="${"$"}{build.dir}/system" />
            <property name="mps.home" location="build/mps" />
            <property name="artifacts.mps" location="${"$"}{mps.home}" />
            <property name="environment" value="env" />
            <property name="env.JAVA_HOME" value="${"$"}{java.home}/.." />
            <property name="jdk.home" value="${"$"}{env.JAVA_HOME}" />

            <path id="path.mps.ant.path">
                ${antLibs.joinToString("\n                    ") {
                """<pathelement location="${"$"}{artifacts.mps}/$it" />"""
            }}
            </path>

            <target name="build" depends="export-modules" />

            <target name="clean">
                <delete dir="${"$"}{build.mps.config.path}" />
                <delete dir="${"$"}{build.mps.system.path}" />
            </target>

            <target name="declare-mps-tasks">
                <taskdef resource="jetbrains/mps/build/ant/antlib.xml" classpathref="path.mps.ant.path" />
            </target>

            <target name="export-modules" depends="clean,declare-mps-tasks">
                <echo message="Running ${if (isExport) "export" else "import"} of modules" />
                <runMPS solution="ac6b4971-2a89-49fb-9c30-c2f0e85de741(org.modelix.model.sync.mps)" startClass="org.modelix.model.sync.mps.MPSRepositorySynchronizer" startMethod="${if (isExport) "export" else "import"}Repository">
                    <library file="${mpsDependenciesPath.get()}" />
                    <library file="${repositoryPath.get()}" />
                    <jvmargs>
                        <arg value="-Dmodelix.model.sync.${if (isExport) "output" else "input"}.path=${jsonDirPath.get()}" />
                        <arg value="-Dmodelix.model.sync.${if (isExport) "output" else "input"}.modules=${includedModules.get().joinToString(",")}" />
                        <arg value="-Dmodelix.model.sync.repo.path=${repositoryPath.get()}" />
                        <arg value="-Didea.config.path=${"$"}{build.mps.config.path}" />
                        <arg value="-Didea.system.path=${"$"}{build.mps.system.path}" />
                        <arg value="-ea" />
                        <arg value="-Xmx${mpsHeapSize.get()}" />
                    </jvmargs>
                </runMPS>
            </target>
        </project>
            """.trimIndent(),
        )
    }

    private fun getAntLibs(mpsHome: File): List<String> {
        val mpsVersion = getMpsVersion(mpsHome)
        return if (mpsVersion < "2021.2") {
            listOf("lib/ant/lib/ant-mps.jar", "lib/log4j.jar", "lib/jdom.jar")
        } else if (mpsVersion < "2021.3") {
            listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar")
        } else {
            listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar", "lib/3rd-party-rt.jar")
        }
    }

    private fun getMpsVersion(mpsHome: File): String {
        val buildPropertiesFile = mpsHome.resolve("build.properties")
        require(buildPropertiesFile.exists()) { "MPS build.properties file not found: ${buildPropertiesFile.absolutePath}" }
        val buildProperties = Properties()
        buildPropertiesFile.inputStream().use { buildProperties.load(it) }

        return listOfNotNull(
            buildProperties["mpsBootstrapCore.version.major"],
            buildProperties["mpsBootstrapCore.version.minor"],
            buildProperties["mpsBootstrapCore.version.eap"],
        )
            .map { it.toString().trim('.') }
            .filter { it.isNotEmpty() }
            .joinToString(".")
    }
}
