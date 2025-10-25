package org.modelix.mps.gitimport.fs

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import jetbrains.mps.vfs.openapi.FileSystem
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

class GitFS(val root: GitObjectAsVirtualFile) : IFileSystem, FileSystem {
    constructor(gitCommit: RevCommit, gitRepo: Repository) : this(
        GitObjectAsVirtualFile(
            parent = null,
            name = gitCommit.name,
            isDirectory = true,
            objectId = gitCommit.tree,
            repository = gitRepo,
        ),
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
}
