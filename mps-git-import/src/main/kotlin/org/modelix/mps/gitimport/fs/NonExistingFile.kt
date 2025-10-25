package org.modelix.mps.gitimport.fs

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import jetbrains.mps.vfs.QualifiedPath
import jetbrains.mps.vfs.openapi.FileSystem
import jetbrains.mps.vfs.path.Path
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

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
