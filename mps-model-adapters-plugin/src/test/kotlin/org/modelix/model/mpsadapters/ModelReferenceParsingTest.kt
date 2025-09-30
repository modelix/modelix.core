package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SModelId
import jetbrains.mps.smodel.SModelReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.util.UUID
import kotlin.test.assertFailsWith

class ModelReferenceParsingTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test model reference can be serialized and deserialized`() {
        val originalReference = SModelReference(null, SModelId.regular(UUID.fromString("9858ad24-9b93-4fb4-b55d-1d5b38a5d03b")), "my.model")
        val referenceWithoutName = SModelReference(null, SModelId.regular(UUID.fromString("9858ad24-9b93-4fb4-b55d-1d5b38a5d03b")), "")
        assertEquals("r:9858ad24-9b93-4fb4-b55d-1d5b38a5d03b()", referenceWithoutName.toString())

        // MPS fails to parse a reference that it serialized itself.
        // This can be considered an MPS bug, but we have to compensate for that.
        // This check ensures that we found the correct cause for this exception.
        assertFailsWith(PersistenceFacade.IncorrectModelReferenceFormatException::class) {
            PersistenceFacade.getInstance().createModelReference(referenceWithoutName.toString())
        }

        // Modelix is expected to remove the name, because MPS ignores the name when comparing two model references.
        // By not storing the name in Modelix we avoid any inconsistent behavior after a model renaming.
        assertEquals("mps-model:r:9858ad24-9b93-4fb4-b55d-1d5b38a5d03b", originalReference.toModelix().serialize())

        // Ensure a reference is still parsable by MPS after it was converted to and from Modelix.
        assertEquals(
            "r:9858ad24-9b93-4fb4-b55d-1d5b38a5d03b(unknown)",
            PersistenceFacade.getInstance().createModelReference(originalReference.toModelix().toMPS().toString()).toString(),
        )
    }
}
