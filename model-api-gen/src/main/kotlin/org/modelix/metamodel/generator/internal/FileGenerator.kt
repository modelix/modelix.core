package org.modelix.metamodel.generator.internal

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

/**
 * Deduplicates common logic for file generation.
 */
internal interface FileGenerator {
    val outputDir: Path

    fun generateFile() {
        generateFileSpec().toBuilder()
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "warnings").build())
            .build()
            .writeTo(outputDir)
    }

    fun generateFileSpec(): FileSpec
}
