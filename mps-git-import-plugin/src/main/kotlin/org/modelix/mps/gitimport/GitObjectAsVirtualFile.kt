@file:Suppress("removal")

package org.modelix.mps.gitimport

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import jetbrains.mps.vfs.QualifiedPath
import jetbrains.mps.vfs.openapi.FileSystem
import jetbrains.mps.vfs.path.FilePath
import jetbrains.mps.vfs.path.Path
import jetbrains.mps.vfs.path.PathFormats
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

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

class NonExistingFile(private val parent: IFile, private val name: String) : IFile {
    override fun getFileSystem(): FileSystem {
        TODO("Not yet implemented")
    }

    override fun getFS(): IFileSystem {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getPath(): String {
        return parent.path + "/" + name
    }

    override fun toPath(): Path {
        TODO("Not yet implemented")
    }

    override fun toRealPath(): String {
        TODO("Not yet implemented")
    }

    override fun getUrl(): URL? {
        TODO("Not yet implemented")
    }

    override fun getQualifiedPath(): QualifiedPath? {
        TODO("Not yet implemented")
    }

    override fun getParent(): IFile? {
        return parent
    }

    override fun isZipArchive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInZipArchive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getBundleHome(): IFile? {
        TODO("Not yet implemented")
    }

    override fun isDirectory(): Boolean {
        return false
    }

    override fun isReadOnly(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDescendant(path: String): IFile {
        if (path.contains('/')) {
            val childName = path.substringBefore('/')
            val remainingPath = path.substringAfter('/')
            return findChild(childName).getDescendant(remainingPath)
        } else {
            return findChild(path)
        }
    }

    override fun findChild(name: String): IFile {
        return NonExistingFile(this, name)
    }

    override fun getChildren(): List<IFile?>? {
        return emptyList()
    }

    override fun lastModified(): Long {
        TODO("Not yet implemented")
    }

    override fun length(): Long {
        TODO("Not yet implemented")
    }

    override fun exists(): Boolean {
        return false
    }

    override fun setTimeStamp(p0: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun createNewFile(): Boolean {
        TODO("Not yet implemented")
    }

    override fun mkdirs(): Boolean {
        TODO("Not yet implemented")
    }

    override fun delete(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rename(p0: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun move(p0: IFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun copy(p0: IFile, p1: String): IFile? {
        TODO("Not yet implemented")
    }

    override fun openInputStream(): InputStream? {
        TODO("Not yet implemented")
    }

    override fun openOutputStream(): OutputStream? {
        TODO("Not yet implemented")
    }
}

class GitObjectAsVirtualFile(
    private val parent: GitObjectAsVirtualFile?,
    private val name: String,
    private val isDirectory: Boolean,
    private val objectId: AnyObjectId,
    private val repository: Repository,
) : IFile {
    private val children: Sequence<GitObjectAsVirtualFile> = if (!isDirectory) {
        emptySequence()
    } else {
        sequence {
            val walk = TreeWalk(repository)
            walk.addTree(objectId)
            walk.isRecursive = false
            walk.filter = TreeFilter.ALL
            while (walk.next()) {
                walk.isSubtree
                yield(
                    GitObjectAsVirtualFile(
                        parent = this@GitObjectAsVirtualFile,
                        name = walk.nameString,
                        isDirectory = walk.isSubtree,
                        objectId = walk.getObjectId(0),
                        repository = repository,
                    ),
                )
            }
        }
    }

    override fun toString(): String {
        return path
    }

    fun getRoot(): GitObjectAsVirtualFile = parent?.getRoot() ?: this

    override fun getFileSystem(): FileSystem {
        return GitFS(getRoot())
    }

    override fun getFS(): IFileSystem {
        return GitFS(getRoot())
    }

    override fun getName(): String {
        return name
    }

    override fun getPath(): String {
        return (parent?.path ?: "/gitfs") + "/" + name
    }

    override fun toPath(): Path {
        return FilePath.fromString(path, PathFormats.UNIX)
    }

    override fun toRealPath(): String {
        TODO("Not yet implemented")
    }

    override fun getUrl(): URL? {
        TODO("Not yet implemented")
    }

    override fun getQualifiedPath(): QualifiedPath? {
        TODO("Not yet implemented")
    }

    override fun getParent() = parent

    override fun isZipArchive(): Boolean {
        return false
        // return name.endsWith(".zip") || name.endsWith(".jar")
    }

    override fun isInZipArchive(): Boolean {
        return false
    }

    override fun getBundleHome(): IFile? {
        TODO("Not yet implemented")
    }

    override fun isDirectory(): Boolean {
        return isDirectory
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    override fun getDescendant(path: String): IFile {
        if (path.contains('/')) {
            val childName = path.substringBefore('/')
            val remainingPath = path.substringAfter('/')
            return findChild(childName).getDescendant(remainingPath)
        } else {
            return findChild(path)
        }
    }

    override fun findChild(name: String): IFile {
        return children.find { it.name == name } ?: NonExistingFile(this, name)
    }

    override fun getChildren() = children.toList()

    override fun lastModified(): Long {
        return 0
    }

    override fun length(): Long {
        TODO("Not yet implemented")
    }

    override fun exists(): Boolean {
        return true
    }

    override fun setTimeStamp(p0: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun createNewFile(): Boolean {
        TODO("Not yet implemented")
    }

    override fun mkdirs(): Boolean {
        TODO("Not yet implemented")
    }

    override fun delete(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rename(p0: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun move(p0: IFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun copy(p0: IFile, p1: String): IFile? {
        TODO("Not yet implemented")
    }

    fun getBytes(): ByteArray = repository.newObjectReader().open(objectId).bytes

    override fun openInputStream(): InputStream {
        return ByteArrayInputStream(getBytes())
    }

    override fun openOutputStream(): OutputStream? {
        TODO("Not yet implemented")
    }
}
