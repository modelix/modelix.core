package org.modelix.metamodel.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import java.io.File
import java.util.*
import javax.inject.Inject

@CacheableTask
abstract class GenerateAntScriptForMpsMetaModelExport @Inject constructor(of: ObjectFactory) : DefaultTask() {
    @Input
    val mpsHome: Property<String> = of.property(String::class.java)

    @get:OutputFile
    val antScriptFile: RegularFileProperty = of.fileProperty()

    @Input
    val exporterDir: Property<String> = of.property(String::class.java)

    @Input
    val moduleFolders: ListProperty<String> = of.listProperty(String::class.java)

    @Input
    val heapSize: Property<String> = of.property(String::class.java)

    @Input
    @Optional
    val exportModulesFilter: Property<String> = of.property(String::class.java)

    @TaskAction
    fun generate() {
        val mpsVersion = getMpsVersion()
        val antLibs = if (mpsVersion < "2021.2") {
            listOf("lib/ant/lib/ant-mps.jar", "lib/log4j.jar", "lib/jdom.jar")
        } else if (mpsVersion < "2021.3") {
            listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar")
        } else {
            listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar", "lib/3rd-party-rt.jar")
        }
        antScriptFile.get().asFile.parentFile.mkdirs()
        antScriptFile.get().asFile.writeText("""
            <project name="export-languages" default="build">
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

                <target name="build" depends="export-languages" />

                <target name="clean">
                    <delete dir="${"$"}{build.mps.config.path}" />
                    <delete dir="${"$"}{build.mps.system.path}" />
                    <delete dir="exported-languages" />
                </target>

                <target name="declare-mps-tasks">
                    <taskdef resource="jetbrains/mps/build/ant/antlib.xml" classpathref="path.mps.ant.path" />
                </target>

                <target name="export-languages" depends="clean,declare-mps-tasks">
                    <echo message="Running export of languages" />
                    <runMPS solution="e52a4421-48a2-4de1-8327-d9414e799c67(org.modelix.metamodel.export)" startClass="org.modelix.metamodel.export.CommandlineExporter" startMethod="${if (exportModulesFilter.isPresent) "exportBoth" else "exportLanguages"}">
                        <library file="${getMpsLanguagesDir().absolutePath}" />
                        <library file="${exporterDir.get()}" />
                        ${moduleFolders.get().joinToString("\n                        ") {
                            """<library file="$it" />"""
                        }}                                

                        <jvmargs>
                            <arg value="-Didea.config.path=${"$"}{build.mps.config.path}" />
                            <arg value="-Didea.system.path=${"$"}{build.mps.system.path}" />
                            <arg value="-ea" />
                            <arg value="-Xmx${heapSize.get()}" />
                            ${
                                if (exportModulesFilter.isPresent) {
                                    """<arg value="-Dmodelix.export.includedModules=${exportModulesFilter.get()}" />"""
                                } else ""
                            }
                        </jvmargs>
                    </runMPS>
                </target>
            </project>
        """.trimIndent())
    }

    private fun getMpsBuildPropertiesFile() = File(mpsHome.get()).resolve("build.properties")
    private fun getMpsLanguagesDir() = File(mpsHome.get()).resolve("languages")

    private fun getMpsVersion(): String {
        val buildPropertiesFile = getMpsBuildPropertiesFile()
        require(buildPropertiesFile.exists()) { "MPS build.properties file not found: ${buildPropertiesFile.absolutePath}" }
        val buildProperties = Properties()
        buildPropertiesFile.inputStream().use { buildProperties.load(it) }

        return listOfNotNull(
            buildProperties["mpsBootstrapCore.version.major"],
            buildProperties["mpsBootstrapCore.version.minor"],
            //buildProperties["mpsBootstrapCore.version.bugfixNr"],
            buildProperties["mpsBootstrapCore.version.eap"],
        )
            .map { it.toString().trim('.') }
            .filter { it.isNotEmpty() }
            .joinToString(".")

//        mpsBootstrapCore.version.major=2020
//        mpsBootstrapCore.version.minor=3
//        mpsBootstrapCore.version.bugfixNr=.6
//        mpsBootstrapCore.version.eap=
//        mpsBootstrapCore.version=2020.3
    }
}