package org.modelix.mps.gitimport

import org.eclipse.jgit.api.Git
import java.io.File

class GitCloner(
    val localDirectory: File,
    val gitUrl: String,
    val gitUser: String?,
    val gitPassword: String?,
) {
    fun cloneRepo(): Git {
        val cmd = Git.cloneRepository()
        cmd.setNoCheckout(true)
        cmd.setCloneAllBranches(true)
        if (gitPassword != null) {
            cmd.applyCredentials(gitUser, gitPassword)
        }
        cmd.configureHttpProxy()
        cmd.setURI(gitUrl)
        localDirectory.mkdirs()
        cmd.setDirectory(localDirectory)
        return cmd.call()
    }
}
