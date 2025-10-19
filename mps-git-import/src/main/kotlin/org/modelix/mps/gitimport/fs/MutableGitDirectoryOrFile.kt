package org.modelix.mps.gitimport.fs

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.QualifiedPath
import jetbrains.mps.vfs.openapi.FileSystem
import jetbrains.mps.vfs.path.FilePath
import jetbrains.mps.vfs.path.Path
import jetbrains.mps.vfs.path.PathFormats
import org.eclipse.jgit.dircache.DirCacheBuilder
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.TreeMap

class MutableGitDirectoryOrFile(
    private val gitFS: MutableGitFS,
    private val parent: MutableGitDirectoryOrFile?,
    private val name: String,
) : IFile {
    private var content: Content = NonExisting()

    override fun getName() = name
    override fun getParent() = parent

    fun loadAsFile(objectId: AnyObjectId) {
        content = FileContent(objectId)
    }

    fun loadAsDirectory(objectId: AnyObjectId) {
        val newContent = DirectoryContent()

        val walk = TreeWalk(fs.gitRepo)
        walk.addTree(objectId)
        walk.isRecursive = false
        walk.filter = TreeFilter.ALL
        while (walk.next()) {
            val child = MutableGitDirectoryOrFile(
                gitFS = gitFS,
                parent = this@MutableGitDirectoryOrFile,
                name = walk.nameString,
            )
            if (walk.isSubtree) {
                child.loadAsDirectory(walk.getObjectId(0))
            } else {
                child.loadAsFile(walk.getObjectId(0))
            }
            newContent.children[walk.nameString] = child
        }

        this.content = newContent
    }

    fun createObject() = content.createObject()

    fun loadEntries(builder: DirCacheBuilder, ownPath: String?) {
        content.loadEntries(builder, ownPath)
    }

    @Suppress("removal")
    override fun getFileSystem(): FileSystem {
        return getFS()
    }

    override fun getFS(): MutableGitFS {
        return gitFS
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

    override fun isZipArchive(): Boolean = false

    override fun isInZipArchive(): Boolean = false

    @Suppress("removal")
    override fun getBundleHome(): IFile? {
        TODO("Not yet implemented")
    }

    override fun isDirectory(): Boolean = content.isDirectory()

    override fun isReadOnly(): Boolean = false

    @Suppress("removal")
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
        return content.getChild(name)
    }

    override fun getChildren(): List<IFile> = content.getChildren()

    override fun lastModified(): Long = 0L

    @Suppress("removal")
    override fun length(): Long {
        TODO("Not yet implemented")
    }

    override fun exists(): Boolean = content.exists()

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
        if (content is NonExisting) return false
        content = NonExisting()
        return true
    }

    @Suppress("removal")
    override fun rename(newName: String): Boolean {
        TODO("Not yet implemented")
    }

    @Suppress("removal")
    override fun move(p0: IFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun copy(p0: IFile, p1: String): IFile? {
        TODO("Not yet implemented")
    }

    fun getBytes(): ByteArray = content.getBytes()

    override fun openInputStream(): InputStream {
        return ByteArrayInputStream(getBytes())
    }

    override fun openOutputStream(): OutputStream {
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) {
                buffer.write(b)
            }

            override fun write(b: ByteArray?) {
                buffer.write(b)
            }

            override fun write(b: ByteArray?, off: Int, len: Int) {
                buffer.write(b, off, len)
            }

            override fun flush() {
                writeObject()
            }

            override fun close() {
                writeObject()
            }

            fun writeObject() {
                val objId = fs.gitRepo.newObjectInserter().use { it.insert(Constants.OBJ_BLOB, buffer.toByteArray()) }
                content = FileContent(objId)
            }
        }
    }

    abstract class Content {
        abstract fun createObject(): ChildObject?
        abstract fun isDirectory(): Boolean
        abstract fun exists(): Boolean
        abstract fun getChildren(): List<MutableGitDirectoryOrFile>
        abstract fun getChild(name: String): MutableGitDirectoryOrFile
        abstract fun getBytes(): ByteArray
        abstract fun loadEntries(builder: DirCacheBuilder, ownPath: String?)
    }
    inner class FileContent(val objectId: AnyObjectId) : Content() {
        override fun createObject(): ChildObject {
            return ChildObject(name, FileMode.REGULAR_FILE, objectId)
        }
        override fun isDirectory(): Boolean = false
        override fun exists(): Boolean = true
        override fun getChildren(): List<MutableGitDirectoryOrFile> = emptyList()
        override fun getChild(childName: String): MutableGitDirectoryOrFile {
            throw IllegalStateException("$this@MutableGitDirectoryOrFile is a file. Cannot get child $childName.")
        }
        override fun getBytes(): ByteArray = fs.gitRepo.newObjectReader().open(objectId).bytes
        override fun loadEntries(builder: DirCacheBuilder, ownPath: String?) {
            builder.add(
                DirCacheEntry(ownPath).also {
                    it.setObjectId(objectId)
                    it.fileMode = FileMode.REGULAR_FILE
                },
            )
        }
    }
    inner class DirectoryContent : Content() {
        val children: TreeMap<String, MutableGitDirectoryOrFile> = TreeMap()

        override fun isDirectory(): Boolean = true
        override fun exists(): Boolean = true
        override fun createObject(): ChildObject? {
            val childEntries = children.mapNotNull { it.value.createObject() }
            if (childEntries.isEmpty()) return null

            val formatter = TreeFormatter()
            for (childEntry in childEntries) {
                formatter.append(childEntry.name, childEntry.mode, childEntry.objectId)
            }
            val objectId = fs.gitRepo.newObjectInserter().use { it.insert(formatter) }
            return ChildObject(name, FileMode.TREE, objectId)
        }
        override fun getChildren(): List<MutableGitDirectoryOrFile> = children.values.toList()
        override fun getChild(childName: String): MutableGitDirectoryOrFile {
            return children.getOrPut(childName) { MutableGitDirectoryOrFile(gitFS, this@MutableGitDirectoryOrFile, childName) }
        }
        override fun getBytes(): ByteArray = error("$path is a directory")
        override fun loadEntries(builder: DirCacheBuilder, ownPath: String?) {
            for (child in children) {
                child.value.loadEntries(builder, if (ownPath == null) child.key else "$ownPath/${child.key}")
            }
        }
    }
    inner class NonExisting : Content() {
        override fun isDirectory(): Boolean = false
        override fun exists(): Boolean = false
        override fun createObject(): ChildObject? = null
        override fun getChildren(): List<MutableGitDirectoryOrFile> = emptyList()
        override fun getChild(childName: String): MutableGitDirectoryOrFile {
            return DirectoryContent().also { content = it }.getChild(childName)
        }
        override fun getBytes(): ByteArray = error("$path doesn't exist")
        override fun loadEntries(builder: DirCacheBuilder, ownPath: String?) {}
    }

    class ChildObject(val name: String, val mode: FileMode, val objectId: AnyObjectId)
}
