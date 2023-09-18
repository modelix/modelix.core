/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.sync.bulk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import java.io.File

class ModelExporterTest {

    companion object {
        lateinit var model: ModelData
        lateinit var branch: IBranch

        @JvmStatic
        @BeforeAll
        fun `initialize model and branch`() {
            model = ModelData.fromJson(File("src/commonTest/resources/newmodel.json").readText())

            val tree = CLTree(ObjectStoreCache(MapBaseStore()))
            branch = PBranch(tree, IdGenerator.getInstance(1))

            branch.runWrite {
                model.load(branch)
            }
        }
    }

    @Test
    fun `can export`() {
        val outputFile = File("build/test/model-export/model.json")
        assertDoesNotThrow {
            branch.runRead {
                ModelExporter(branch.getRootNode()).export(outputFile)
            }
        }

        assert(outputFile.exists())
        assertEquals(model, ModelData.fromJson(outputFile.readText()))
    }
}
