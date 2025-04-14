package org.modelix.mps.gitimport

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import org.modelix.model.lazy.RepositoryId

class ModelixSyncCommand : CliktCommand() {
    init {
        subcommands(GitImport())
    }

    override fun run() {}

    class GitImport : CliktCommand(name = "git-import") {
        val gitDir by argument().file(mustExist = true, canBeFile = false, canBeDir = true).help("Path to a Git repository")
        val modelServer by option().default("http://localhost:28101").help("URL of the Modelix model server")
        val repository by option().required().help("Name of the target repository on the model server")
        val branch by option().default("git-import").help("Name of the target branch on the model server")
        val rev by option().default("HEAD").help("Git revision, which can a branch name, a tag or a commit ID")
        val limit by option().int().default(1).help("The maximum number of commits to import")

        override fun run() {
            val importer = GitImporter(
                gitDir = gitDir,
                modelServerUrl = modelServer,
                baseBranch = RepositoryId(repository).getBranchReference(branch),
                targetBranchName = branch,
                gitRevision = rev,
            )
            runBlocking { importer.runSuspending() }
        }
    }
}
