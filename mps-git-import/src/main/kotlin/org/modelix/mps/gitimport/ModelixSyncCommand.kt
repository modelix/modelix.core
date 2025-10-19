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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.modelix.model.lazy.RepositoryId
import java.io.File

class ModelixSyncCommand : CliktCommand() {
    init {
        subcommands(GitImportLocal(), GitImportRemote(), GitExportLocal(), GitExportRemote(), SelfTest())
    }

    override fun run() {}

    abstract class GitImport(name: String) : CliktCommand(name = name) {
        val modelServer by option().default("http://localhost:28101").help("URL of the Modelix model server")
        val token by option().help("JWT token for the model server access")
        val repository by option().required().help("Name of the target repository on the model server")
        val branch by option().default("git-import").help("Name of the target branch on the model server")
        val rev by option().default("HEAD").help("Git revision, which can a branch name, a tag or a commit ID")
        val limit by option().int().default(1).help("The maximum number of commits to import (unused)")

        abstract val gitDir: File

        override fun run() {
            val importer = GitImporter(
                gitDir = gitDir,
                modelServerUrl = modelServer,
                baseBranch = RepositoryId(repository).getBranchReference(branch),
                targetBranchName = branch,
                gitRevision = rev,
                token = token,
            )
            runBlocking { importer.runSuspending() }
        }
    }

    class GitImportLocal : GitImport(name = "git-import") {
        override val gitDir by argument().file(mustExist = true, canBeFile = false, canBeDir = true).help("Path to a Git repository")
    }

    class GitImportRemote : GitImport(name = "git-import-remote") {
        override val gitDir = File("git-repo-for-import")

        val gitUrl by argument().help("URL of a Git repository")
        val gitUser by option()
        val gitPassword by option()

        override fun run() {
            println("Cloning $gitUrl into $gitDir")
            GitCloner(
                localDirectory = gitDir,
                gitUrl = gitUrl,
                gitUser = gitUser,
                gitPassword = gitPassword,
            ).cloneRepo()
            super.run()
        }
    }

    class SelfTest : CliktCommand(name = "self-test") {
        override fun run() {
            println("OK")
        }
    }

    abstract class GitExport(name: String) : CliktCommand(name = name) {
        val modelServer by option().default("http://localhost:28101").help("URL of the Modelix model server")
        val token by option().help("JWT token for the model server access")
        val modelixRepository by option().required().help("Name of the source repository on the model server")
        val modelixBranch by option().default(RepositoryId.DEFAULT_BRANCH).help("Name of the source branch on the model server")
        val version by option().help("Modelix version hash")
        val gitBranch by option().help("Name of the target Git branch")

        abstract val gitDir: File

        override fun run() {
            val exporter = GitExporter(
                gitDir = gitDir,
                modelServerUrl = modelServer,
                modelixBranch = RepositoryId(modelixRepository).getBranchReference(modelixBranch),
                modelixVersionHash = version,
                gitBranch = gitBranch,
                token = token,
            )
            runBlocking { exporter.runSuspending() }
        }
    }

    class GitExportLocal : GitExport(name = "git-export") {
        override val gitDir by argument().file(mustExist = true, canBeFile = false, canBeDir = true).help("Path to a Git repository")
    }

    class GitExportRemote : GitExport(name = "git-export-remote") {
        override val gitDir = File("git-repo-for-export")

        val gitUrl by argument().help("URL of a Git repository")
        val gitUser by option()
        val gitPassword by option()

        override fun run() {
            println("Cloning $gitUrl into $gitDir")
            GitCloner(
                localDirectory = gitDir,
                gitUrl = gitUrl,
                gitUser = gitUser,
                gitPassword = gitPassword,
            ).cloneRepo()
            super.run()

            try {
                val cmd = Git.open(gitDir).push()
                cmd.setRefSpecs(RefSpec("refs/heads/$gitBranch"))
                gitPassword?.let { pw ->
                    cmd.applyCredentials(gitUser, pw)
                }
                cmd.configureHttpProxy()
                cmd.call()
            } catch (ex: Throwable) {
                ex.printStackTrace()
                throw ex
            }
        }
    }
}
