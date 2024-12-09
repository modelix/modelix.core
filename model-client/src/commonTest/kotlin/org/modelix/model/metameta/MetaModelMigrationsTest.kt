package org.modelix.model.metameta

import org.modelix.model.ModelMigrations
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.metameta.MetaModelMigration.resolveNewConceptReference
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.withAutoTransactions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaModelMigrationsTest {

    companion object {

        private fun createBranchFromModelData(modelData: ModelData): PBranch {
            val store = MapBaseStore()
            val storeCache = ObjectStoreCache(store)
            val idGenerator = IdGenerator.newInstance(1)
            val emptyTree = CLTree.builder(storeCache)
                .repositoryId("aRepositoryId")
                .build()

            val rawBranch = PBranch(emptyTree, idGenerator)
            val idStrategy: (NodeData) -> Long = { nodeData ->
                val serializedNodeId = nodeData.id
                requireNotNull(serializedNodeId)
                val nodeReference = PNodeReference.deserialize(serializedNodeId)
                nodeReference.id
            }
            modelData.load(rawBranch, idStrategy, setOriginalIdProperty = false)
            return rawBranch
        }

        // Metamodel data as it would be created by `MetaModelSynchronizer`
        // when storing a concept with `MetaModelBranch#toLocalConcept`.
        private val metamodelDataSerialized =
            //language=JSON
            """
       {
            "id": "pnode:100000001@aRepositoryId",
            "concept": "modelix.metameta.Language",
            "role": "languages",
            "children": [
                {
                    "id": "pnode:100000002@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000003@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "id",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/4225291329823310560"
                            }
                        },
                        {
                            "id": "pnode:100000004@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "moduleVersion",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242370"
                            }
                        },
                        {
                            "id": "pnode:100000005@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "compileInMPS",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242373"
                            }
                        },
                        {
                            "id": "pnode:100000006@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "models",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898"
                            },
                            "references": {
                                "childConcept": "pnode:100000029@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000007@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "facets",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242412"
                            },
                            "references": {
                                "childConcept": "pnode:10000000a@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000008@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "dependencies",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242425"
                            },
                            "references": {
                                "childConcept": "pnode:10000000b@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000009@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "languageDependencies",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242439"
                            },
                            "references": {
                                "childConcept": "pnode:100000012@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:10000002f@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "pnode:100000030@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Module",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895"
                    }
                },
                {
                    "id": "pnode:10000000a@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000031@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "ModuleFacet",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242403"
                    }
                },
                {
                    "id": "pnode:10000000b@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000000c@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "reexport",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242416"
                            }
                        },
                        {
                            "id": "pnode:10000000d@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "uuid",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242418"
                            }
                        },
                        {
                            "id": "pnode:10000000e@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "name",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242421"
                            }
                        },
                        {
                            "id": "pnode:10000000f@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "explicit",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750565"
                            }
                        },
                        {
                            "id": "pnode:100000010@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "version",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750570"
                            }
                        },
                        {
                            "id": "pnode:100000011@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "scope",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/8438396892798826745"
                            }
                        },
                        {
                            "id": "pnode:100000032@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "ModuleDependency",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415"
                    }
                },
                {
                    "id": "pnode:100000012@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000013@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "uuid",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575314"
                            }
                        },
                        {
                            "id": "pnode:100000014@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "name",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575315"
                            }
                        },
                        {
                            "id": "pnode:100000033@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "LanguageDependency",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311"
                    }
                },
                {
                    "id": "pnode:100000015@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000034@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:100000002@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Solution",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598211"
                    }
                },
                {
                    "id": "pnode:100000016@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000035@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:100000002@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Language",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598212"
                    }
                },
                {
                    "id": "pnode:100000017@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000036@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:100000002@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "DevKit",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598213"
                    }
                },
                {
                    "id": "pnode:100000018@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000019@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903"
                            },
                            "references": {
                                "childConcept": "pnode:100000002@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:10000001a@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "projects",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/7064605579395546636"
                            },
                            "references": {
                                "childConcept": "pnode:10000001c@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:10000001b@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "tempModules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/8226136427470548682"
                            },
                            "references": {
                                "childConcept": "pnode:100000002@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000037@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Repository",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902"
                    }
                },
                {
                    "id": "pnode:10000001c@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000001d@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4008363636171860450"
                            },
                            "references": {
                                "childConcept": "pnode:100000002@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:10000001e@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "projectModules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4201834143491306088"
                            },
                            "references": {
                                "childConcept": "pnode:100000020@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000038@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "pnode:100000039@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Project",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313"
                    }
                },
                {
                    "id": "pnode:10000001f@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000003a@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "ModuleReference",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/5782622473578468308"
                    }
                },
                {
                    "id": "pnode:100000020@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000021@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "virtualFolder",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4201834143491306084/4201834143491306085"
                            }
                        },
                        {
                            "id": "pnode:10000003b@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:10000001f@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "ProjectModule",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4201834143491306084"
                    }
                },
                {
                    "id": "pnode:100000022@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000003c@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "ModelReference",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/6402965165736932003"
                    }
                },
                {
                    "id": "pnode:100000023@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000024@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "version",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429/2206727074858242435"
                            }
                        },
                        {
                            "id": "pnode:10000003d@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:100000012@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "SingleLanguageDependency",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429"
                    }
                },
                {
                    "id": "pnode:100000025@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000003e@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:100000012@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "DevkitDependency",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575318"
                    }
                },
                {
                    "id": "pnode:100000026@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:100000027@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "generated",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242407"
                            }
                        },
                        {
                            "id": "pnode:100000028@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "path",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242409"
                            }
                        },
                        {
                            "id": "pnode:10000003f@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "pnode:10000000a@aRepositoryId"
                            }
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "JavaModuleFacet",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406"
                    }
                },
                {
                    "id": "pnode:100000029@aRepositoryId",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "pnode:10000002a@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "id",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/2615330535972958738"
                            }
                        },
                        {
                            "id": "pnode:10000002b@aRepositoryId",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "stereotype",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/3832696962605996173"
                            }
                        },
                        {
                            "id": "pnode:10000002c@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "rootNodes",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/474657388638618900"
                            }
                        },
                        {
                            "id": "pnode:10000002d@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modelImports",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/6402965165736931000"
                            },
                            "references": {
                                "childConcept": "pnode:100000022@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:10000002e@aRepositoryId",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "usedLanguages",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/5381564949800872334"
                            },
                            "references": {
                                "childConcept": "pnode:100000023@aRepositoryId"
                            }
                        },
                        {
                            "id": "pnode:100000040@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "pnode:100000041@aRepositoryId",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        }
                    ],
                    "properties": {
                        "abstract": "false",
                        "name": "Model",
                        "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892"
                    }
                }
            ],
            "properties": {
                "name": "org.modelix.model.repositoryconcepts",
                "uid": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80"
            }
        }
        """

        private fun createModelDataBeforeMigration(modelDataSerialized: String) =
            //language=JSON
            """
            {
                "root": {
                    "id": "pnode:1@aRepositoryId",
                    "children": [
                        $metamodelDataSerialized,
                        $modelDataSerialized
                    ]
                }
            }
            """.let { ModelData.fromJson(it) }

        val branchOnlyWithMetaData = """
            {
                "root": {
                    "id": "pnode:1@aRepositoryId",
                    "children": [
                        $metamodelDataSerialized
                    ]
                }
            }
            """.let { createBranchFromModelData(ModelData.fromJson(it)).withAutoTransactions() }
    }

    @Test
    fun dataReferencingMetamodelIsMigrated() {
        // Arrange
        val modelDataChildBeforeMigrationSerialized =
            //language=JSON
            """
            {
                "id": "pnode:100000042@aRepositoryId",
                "concept": "100000029",
                "role": "aChild",
                "properties": {
                  "id": "myId"
                },
                "references": {
                  "someRef": "pnode:100000043@aRepositoryId"
                },
                "children": [
                  {
                    "id": "pnode:100000043@aRepositoryId",
                    "concept": "100000029",
                    "role": "aChild"
                  }
                ]
            }
            """
        val modelDataBeforeMigration = createModelDataBeforeMigration(modelDataChildBeforeMigrationSerialized)
        val rawBranch = createBranchFromModelData(modelDataBeforeMigration)

        // Act
        ModelMigrations.useResolvedConceptsFromMetaModel(rawBranch)

        // Assert
        val modelDataAfterMigration =
            //language=JSON
            """
            {
                "root": {
                    "id": "pnode:1@aRepositoryId",
                    "children": [
                        $metamodelDataSerialized,
                        {
                            "id": "pnode:100000042@aRepositoryId",
                            "concept": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
                            "role": "aChild",
                            "properties": {
                                "id": "myId"
                            },
                            "references": {
                                "someRef": "pnode:100000043@aRepositoryId"
                            },
                            "children": [
                              {
                                "id": "pnode:100000043@aRepositoryId",
                                "concept": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
                                "role": "aChild"
                              }
                            ]
                        }
                    ]
                }
            }
            """.let { ModelData.fromJson(it) }
        rawBranch.runRead {
            assertEquals(modelDataAfterMigration.root, rawBranch.getRootNode().asData())
        }
    }

    @Test
    fun duplicatedMigrationsDoesNotCreateNewTree() {
        // Arrange
        val modelDataChildBeforeMigrationSerialized =
            //language=JSON
            """
            {
                "id": "pnode:100000042@aRepositoryId",
                "concept": "100000029",
                "role": "aChild",
                "properties": {
                  "id": "myId"
                },
                "references": {
                  "someRef": "pnode:100000043@aRepositoryId"
                },
                "children": [
                  {
                    "id": "pnode:100000043@aRepositoryId",
                    "concept": "100000029",
                    "role": "aChild"
                  }
                ]
            }
            """
        val modelDataBeforeMigration = createModelDataBeforeMigration(modelDataChildBeforeMigrationSerialized)
        val rawBranch = createBranchFromModelData(modelDataBeforeMigration)

        // Act
        ModelMigrations.useResolvedConceptsFromMetaModel(rawBranch)
        val treeAfterFirstMigration = rawBranch.withAutoTransactions().transaction.tree
        ModelMigrations.useResolvedConceptsFromMetaModel(rawBranch)
        val treeAfterSecondMigration = rawBranch.withAutoTransactions().transaction.tree

        // Assert
        assertEquals(treeAfterFirstMigration, treeAfterSecondMigration)
    }

    @Test
    fun nullConceptReferenceDoesNotFailToResolve() {
        val oldConcept: ConceptReference? = null

        val newConcept = resolveNewConceptReference(oldConcept, branchOnlyWithMetaData.transaction.tree)

        assertNull(newConcept)
    }

    @Test
    fun conceptReferenceToNodeWithoutUIDIsNotChanged() {
        val oldConcept = ConceptReference("100000030")
        val newConcept = resolveNewConceptReference(oldConcept, branchOnlyWithMetaData.transaction.tree)
        assertEquals(oldConcept, newConcept)
    }

    @Test
    fun conceptReferenceToNonExistingNodeIsNotChanged() {
        val oldConcept = ConceptReference("999999999")

        val newConcept = resolveNewConceptReference(oldConcept, branchOnlyWithMetaData.transaction.tree)

        assertEquals(oldConcept, newConcept)
    }

    @Test
    fun branchWithoutMetamodelDataIsNotMigrated() {
        // Arrange
        val modelConcept = BuiltinLanguages.MPSRepositoryConcepts.Model
        val store = MapBaseStore()
        val storeCache = ObjectStoreCache(store)
        val idGenerator = IdGenerator.newInstance(1)
        val emptyTree = CLTree(storeCache)

        val rawBranch = PBranch(emptyTree, idGenerator)
        var oldTree: ITree? = null
        rawBranch.runWriteT { transaction ->
            val dataChild = rawBranch.getRootNode().addNewChild("aChild", -1, modelConcept)
            dataChild.setPropertyValue(modelConcept.id, "myId")
            oldTree = transaction.tree
        }

        // Act
        ModelMigrations.useResolvedConceptsFromMetaModel(rawBranch)

        // Assert
        rawBranch.runReadT { transaction ->
            assertEquals(oldTree, transaction.tree)
        }
    }
}
