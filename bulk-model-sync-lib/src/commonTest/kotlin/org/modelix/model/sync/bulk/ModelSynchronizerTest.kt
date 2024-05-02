/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.sync.bulk

import org.modelix.model.ModelFacade
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.data.ModelData
import kotlin.js.JsName
import kotlin.test.Test

class ModelSynchronizerTest {

    @Test
    @JsName("with_basic_filter")
    fun `with basic filter`() {
        // language=json
        val initialData = """
           {
             "root": {
               "id": "node:001",
               "children": [
                 {
                   "id": "node:002",
                   "properties": {
                     "purpose" : "property sync test",
                     "name": "nameValue",
                     "description": "descriptionValue",
                     "optional" : "toBeDeleted"
                   }
                 },
                 {
                   "id": "node:003",
                   "role": "child",
                   "properties": {
                     "purpose": "reference sync test"
                   },
                   "references": {
                     "root": "node:001",
                     "self": "node:003"
                   }
                 },
                 {
                   "id": "node:010",
                   "role": "child",
                   "properties": {
                     "purpose": "child sync test"
                   },
                   "children": [
                     {
                       "id": "node:011",
                       "role": "child"
                     },
                     {
                       "id": "node:012",
                       "role": "child"
                     },
                     {
                       "id": "node:013",
                       "role": "child"
                     },
                     {
                       "id": "node:014",
                       "role": "child"
                     },
                     {
                       "id": "node:015",
                       "role": "child"
                     },
                     {
                       "id": "node:004",
                       "role": "child",
                       "properties": {
                         "purpose": "move across parents"
                       }
                     }
                   ]
                 },
                 {
                   "id": "node:005",
                   "role": "child"
                   "properties": {
                     "purpose": "move across parents"
                   }
                 },
                 {
                   "id": "node:006",
                   "role": "roleBefore",
                   "properties": {
                     "purpose": "change role"
                   }
                 }
               ]
             }
           }

        """.trimIndent().let { ModelData.fromJson(it) }

        // language=json
        val expectedData = """
            {
              "root": {
                "id": "node:001",
                "children": [
                  {
                    "id": "node:002",
                    "properties": {
                      "purpose" : "property sync test",
                      "name": "newNameValue",
                      "description": "newDescriptionValue",
                      "newProperty": "newProperty"
                    },
                    "references": {
                      "sibling": "node:003"
                    }
                  },
                  {
                    "id": "node:003",
                    "role": "child",
                    "properties": {
                      "purpose": "reference sync test"
                    },
                    "references": {
                      "root": "node:001",
                      "sibling": "node:002"
                    }
                  },
                  {
                    "id": "node:010",
                    "role": "child",
                    "properties": {
                      "purpose": "child sync test"
                    },
                    "children": [
                      {
                        "id": "node:011",
                        "role": "child"
                      },
                      {
                        "id": "node:013",
                        "role": "child"
                      },
                      {
                        "id": "node:015",
                        "role": "child"
                      },
                      {
                        "id": "node:014",
                        "role": "child"
                      },
                      {
                        "id": "node:016",
                        "role": "child"
                      },
                      {
                        "id": "node:005",
                        "role": "child",
                        "properties": {
                          "purpose": "move across parents"
                        }
                      }
                    ]
                  },
                  {
                    "id": "node:004",
                    "role": "child",
                    "properties": {
                      "purpose": "move across parents"
                    }
                  },
                  {
                    "id": "node:006",
                    "role": "roleAfter",
                    "properties": {
                      "purpose": "change role"
                    }
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        val sourceBranch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree()).also {
            ModelImporter(it.getRootNode()).import(expectedData)
        }

        val targetBranch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree()).also {
            ModelImporter(it.getRootNode()).import(initialData)
        }

        sourceBranch.runRead {
            val sourceRoot = sourceBranch.getRootNode()

            targetBranch.runWrite {
                val targetRoot = targetBranch.getRootNode()
                val synchronizer = ModelSynchronizer(
                    filter = BasicFilter,
                    sourceRoot = sourceRoot,
                    targetRoot = targetRoot,
                    nodeAssociation = BasicAssociation(sourceBranch, targetBranch),
                )
                synchronizer.synchronize()
            }
        }
        targetBranch.runRead {
            assertAllNodesConformToSpec(expectedData.root, targetBranch.getRootNode())
        }
    }

    object BasicFilter : ModelSynchronizer.IFilter {
        override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean {
            return true
        }

        override fun needsSynchronization(node: INode): Boolean {
            return true
        }
    }

    class BasicAssociation(val source: IBranch, val target: IBranch) : INodeAssociation {

        override fun resolveTarget(sourceNode: INode): INode? {
            require(sourceNode is PNodeAdapter)
            return target.computeRead {
                target.getRootNode().getDescendants(true).find { sourceNode.originalId() == it.originalId() }
            }
        }

        override fun associate(sourceNode: INode, targetNode: INode) {
            // not needed for test
        }

        override fun associationMatches(sourceNode: INode, targetNode: INode): Boolean {
            require(sourceNode is PNodeAdapter)
            require(targetNode is PNodeAdapter)
            return sourceNode.nodeId == targetNode.nodeId
        }
    }
}
