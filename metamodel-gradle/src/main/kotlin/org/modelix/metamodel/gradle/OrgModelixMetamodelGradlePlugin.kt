package org.modelix.metamodel.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.file.FileCollection
import java.io.File
import java.util.*

class OrgModelixMetamodelGradlePlugin: Plugin<Project> {
    private lateinit var project: Project
    private lateinit var settings: MetaModelGradleSettings
    override fun apply(project: Project) {
        this.project = project
        this.settings = project.extensions.create("metamodel", MetaModelGradleSettings::class.java)

        val generateAntScriptForMpsMetaModelExport = project.tasks.register("generateAntScriptForMpsMetaModelExport") { task ->
            task.doLast {
                val mpsVersion = getMpsVersion()
                val antLibs = if (mpsVersion < "2021.2") {
                    listOf("lib/ant/lib/ant-mps.jar", "lib/log4j.jar", "lib/jdom.jar")
                } else if (mpsVersion < "2021.3") {
                    listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar")
                } else {
                    listOf("lib/ant/lib/ant-mps.jar", "lib/util.jar", "lib/3rd-party-rt.jar")
                }
                val antScriptFile = getAntScriptFile()
                antScriptFile.parentFile.mkdirs()
                antScriptFile.writeText("""
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
                            ${antLibs.joinToString("\n                            ") {
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

                        <target name="export-languages" depends="declare-mps-tasks">
                            <echo message="Running export of languages" />
                            <runMPS solution="cc09f0ed-0e5c-4109-ad8c-a9842c54cb2e(org.modelix.model.metamodel.mpsgenerator)" startClass="org.modelix.model.metamodel.mpsgenerator.plugin.CommandlineExporter" startMethod="exportLanguages">
                                <library file="${"$"}{artifacts.mps}/languages" />
                                ${settings.moduleFolders.joinToString("\n                                ") {
                                    """<library file="${it.absolutePath}" />"""
                                }}                                

                                <jvmargs>
                                    <arg value="-Didea.config.path=${"$"}{build.mps.config.path}" />
                                    <arg value="-Didea.system.path=${"$"}{build.mps.system.path}" />
                                    <arg value="-ea" />
                                    <arg value="-Xmx1024m" />
                                </jvmargs>
                            </runMPS>
                        </target>
                    </project>
                """.trimIndent())
            }
        }

        val antDependencies = project.configurations.create("metamodel-ant-dependencies")
        project.dependencies.add(antDependencies.name, "org.apache.ant:ant-junit:1.10.12")
        project.tasks.register("exportMetaModelFromMps") { task ->
            task.dependsOn(generateAntScriptForMpsMetaModelExport)
            task.doLast {
                project.javaexec { spec ->
                    val javaExecutable = settings.javaExecutable
                    if (javaExecutable != null) {
                        require(javaExecutable.exists()) { "'javaExecutable' not found: ${javaExecutable.absolutePath}" }
                        spec.executable(javaExecutable.absoluteFile)
                    }

                    spec.mainClass.set("org.apache.tools.ant.launch.Launcher")
                    spec.workingDir = getBuildOutputDir()

                    spec.classpath(antDependencies)
                    spec.workingDir = getBuildOutputDir()
                    val mpsHome = getMpsHome()
                    val antVariables = listOf(
                        "mps.home" to mpsHome.absolutePath,
                        "mps_home" to mpsHome.absolutePath,
                        "build.dir" to getBuildOutputDir().absolutePath,
                    ).map { (key, value) -> "-D$key=$value" }
                    spec.args(antVariables)
                    spec.args("-buildfile", getAntScriptFile())
                    spec.args("export-languages")
                }
                println("Languages exported to " + getBuildOutputDir().resolve("exported-languages").absolutePath)
            }
        }
    }

    private fun getBuildOutputDir() = project.buildDir.resolve("metamodel")

    private fun getAntScriptFile() = getBuildOutputDir().resolve("export-languages.xml")

    fun getMpsHome(): File {
        val mpsHome = this.settings.mpsHome
        require(mpsHome != null) { "'mpsHome' is not set in the 'metamodel' settings" }
        require(mpsHome.exists()) { "'mpsHome' doesn't exist: ${mpsHome.absolutePath}" }
        return mpsHome
    }

    fun getMpsVersion(): String {
        return readMPSVersion(getMpsHome())
    }

    private fun readMPSVersion(mpsHome: File): String {
        val buildPropertiesFiles = mpsHome.resolve("build.properties")
        require(buildPropertiesFiles.exists()) { "${buildPropertiesFiles.absolutePath} not found" }
        val buildProperties = Properties()
        buildPropertiesFiles.inputStream().use { buildProperties.load(it) }

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
