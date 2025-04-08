package org.modelix.mps.gitimport

import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryFromName
import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryFromPath
import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryRule
import jetbrains.mps.extapi.persistence.datasource.PreinstalledDataSourceTypes
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.path.Path
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.MultiStreamDataSource
import org.jetbrains.mps.openapi.persistence.NullDataSource
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.datasource.DataSourceType
import org.jetbrains.mps.openapi.persistence.datasource.FileExtensionDataSourceType
import java.io.InputStream
import java.io.OutputStream
import java.util.stream.Stream

class GitDataSourceFactoryRule(private val gitFS: GitFS) : DataSourceFactoryRule {
    override fun spawn(type: DataSourceType): DataSourceFactoryFromName? {
        return null
    }

    override fun spawn(path: Path): DataSourceFactoryFromPath? {
        val pathString = path.toText()
        if (pathString.startsWith("/gitfs/")) {
            return GitDataSourceFactoryFromPath(gitFS)
        }
        return null
    }
}

class GitDataSourceFactoryFromPath(private val gitFS: GitFS) : DataSourceFactoryFromPath {
    override fun create(path: Path): DataSource {
        return createFromFile(gitFS.getFile(path.toText()) as GitObjectAsVirtualFile)
    }

    fun createFromFile(file: GitObjectAsVirtualFile): DataSource {
        if (file.exists() && file.isDirectory()) {
            return GitObjectDataSource(file)
        } else if (file.getPath().endsWith(MPSExtentions.DOT_MODEL_ROOT)) {
            return GitObjectDataSource(file.getParent()!!)
        } else if (file.getPath().endsWith(MPSExtentions.DOT_MODEL_HEADER)) {
            return GitObjectDataSource(file.getParent()!!)
        }
        return GitObjectDataSource(file)
    }
}

class GitObjectDataSource(private val file: GitObjectAsVirtualFile) : StreamDataSource, MultiStreamDataSource {
    override fun openInputStream(): InputStream {
        return file.openInputStream()
    }

    override fun openOutputStream(): OutputStream {
        throw UnsupportedOperationException()
    }

    override fun exists(): Boolean {
        return true
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    override fun getType(): DataSourceType? {
        return if (file.isDirectory) {
            PreinstalledDataSourceTypes.FOLDER
        } else {
            val extension = file.extension
            if (extension.isEmpty()) {
                NullDataSource.NullDataSourceType.INSTANCE
            } else {
                FileExtensionDataSourceType.of(extension)
            }
        }
    }

    override fun toString(): String {
        return file.path
    }

    override fun delete(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getStreamName(): String {
        return file.name
    }

    override fun getLocation(): String {
        return file.path
    }

    override fun getTimestamp(): Long {
        return file.lastModified()
    }

    override fun getSubStreams(): Stream<StreamDataSource> {
        return file.children.map {
            val substream: StreamDataSource = GitObjectDataSource(it)
            substream
        }.stream()
    }

    override fun getStreamByNameOrCreate(name: String): StreamDataSource {
        TODO("Not yet implemented")
    }

    override fun getStreamByName(name: String): StreamDataSource? {
        return file.children.find { it.name == name }?.let { GitObjectDataSource(it) }
    }

    override fun getStreamByNameOrFail(name: String): StreamDataSource {
        val streamByName = getStreamByName(name)
        requireNotNull(streamByName) { "Could not find a stream by the name $name in $this" }
        return streamByName
    }
}

val IFile.extension: String get() = this.name.substringAfterLast('.', "")
