package org.modelix.model.sync.bulk

import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelExporterTest {
    // language=json
    private var serializedModel = """
    {
      "root": {
        "id": "node:001",
        "children": [
          {
            "id": "node:002",
            "properties": {
              "propA": "ValueA",
              "propB": "ValueB"
            },
            "children": [
              {
                "id": "node:004",
                "references": {
                  "refA": "node:003"
                }
              }
            ]
          },
          {
            "id": "node:003",
            "properties": {
              "purpose": "reference sync test"
            },
            "references": {
              "root": "node:001",
              "self": "node:003"
            }
          }
        ]
      }
    }
    """.trimIndent()

    private val model = ModelData.fromJson(serializedModel)

    private fun runTest(body: IBranch.() -> Unit) {
        val branch = PBranch(CLTree(createObjectStoreCache(MapBasedStore())), IdGenerator.getInstance(1))
        branch.runWrite {
            model.load(branch)
        }
        body(branch)
    }

    @Test
    @JsName("export_conforms_to_input")
    fun `export conforms to input`() = runTest {
        val exported = computeRead { getRootNode().asExported() }
        assertEquals(model.root, exported)
    }
}
