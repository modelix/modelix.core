package org.modelix.mps.gitimport.cli

import org.modelix.buildtools.runner.MPSRunner
import org.modelix.buildtools.runner.MPSRunnerConfig
import java.io.File
import java.util.UUID

fun main(args: Array<String>) {
    val buildDir = File("/tmp/git-import-build")
    val workDir = File("/tmp/git-import-work")
    buildDir.mkdirs()
    workDir.mkdirs()
    val config = MPSRunnerConfig(
        mainClassName = "org.modelix.mps.gitimport.MainKt",
        mainMethodName = "runFromAnt",
        mpsHome = File("/mps"),
        jarFolders = listOf(File("/mps-git-import-cli/lib")),
        jvmArgs = args.mapIndexed { index, arg -> "-Dmodelix.git.import.args.$index=$arg" },
        moduleId = UUID.randomUUID(),
        buildDir = buildDir,
        workDir = workDir,
    )
    val runner = MPSRunner(config)
    runner.run()
}
