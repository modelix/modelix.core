package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

/**
 * Deduplicates common logic for file generation.
 */
internal interface FileGenerator {
    val outputDir: Path

    fun generateFile() {
        generateFileSpec().writeTo(outputDir)
    }

    fun generateFileSpec(): FileSpec
}
