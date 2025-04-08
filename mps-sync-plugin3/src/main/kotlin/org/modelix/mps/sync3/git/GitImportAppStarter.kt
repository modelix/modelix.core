package org.modelix.mps.sync3.git

import com.github.ajalt.clikt.core.main
import com.intellij.openapi.application.ApplicationStarter
import kotlin.system.exitProcess

class GitImportAppStarter : ApplicationStarter {

    override fun main(args: List<String>) {
        ModelixSyncCommand().main(args.drop(1))
        exitProcess(0)
    }

    override val isHeadless: Boolean
        get() = true
}
