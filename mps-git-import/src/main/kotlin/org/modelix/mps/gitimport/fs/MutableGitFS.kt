package org.modelix.mps.gitimport.fs

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import jetbrains.mps.vfs.openapi.FileSystem
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

class MutableGitFS(
    val gitRepo: Repository,
    val parentCommit: RevCommit,
) : IFileSystem, FileSystem {
    val root: MutableGitDirectoryOrFile = MutableGitDirectoryOrFile(gitFS = this, parent = null, name = "new-commit-${parentCommit.name}")

    init {
        root.loadAsDirectory(parentCommit.tree)
    }

    constructor(gitCommit: RevCommit, gitRepo: Repository) : this(
        gitRepo,
        gitCommit,
    )

    override fun findExistingFile(p0: String): IFile? {
        TODO("Not yet implemented")
    }

    override fun getFile(path: String): IFile {
        if (path.startsWith("/gitfs/")) {
            return root.getDescendant(path.substringAfter("/gitfs/").substringAfter('/'))
        } else {
            return root.getDescendant(path)
        }
    }

    override fun isFileIgnored(p0: String): Boolean {
        return false
    }

    fun createCommit(message: String, author: String?): ObjectId {
        val commit = CommitBuilder()
        commit.setTreeId(root.createObject()!!.objectId)
        commit.setParentId(parentCommit)
        val modelixBot = PersonIdent("Modelix Git Sync", "git-sync@modelix.org")
        commit.author = author?.let { PersonIdent(it, it) } ?: modelixBot
        commit.committer = modelixBot
        commit.message = message
        return gitRepo.newObjectInserter().insert(commit)
    }
}
