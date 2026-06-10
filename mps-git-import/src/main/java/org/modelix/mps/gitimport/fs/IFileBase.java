package org.modelix.mps.gitimport.fs;

import jetbrains.mps.vfs.IFile;

/**
 * Implemented in Java instead of Kotlin on purpose: depending on the MPS version,
 * {@code stepIntoArchive()} is either an abstract member of {@link IFile} or doesn't exist at all.
 * Kotlin requires the {@code override} modifier in the first case and forbids it in the second,
 * while a plain Java method compiles against both.
 */
public abstract class IFileBase implements IFile {
    public IFile stepIntoArchive() {
        throw new UnsupportedOperationException("Not an archive: " + getPath());
    }
}
