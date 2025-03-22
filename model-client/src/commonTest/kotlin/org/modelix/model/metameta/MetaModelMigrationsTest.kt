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
import org.modelix.model.lazy.createObjectStoreCache
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
            val storeCache = createObjectStoreCache(store)
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
            "id": "modelix:aRepositoryId/100000001",
            "concept": "modelix.metameta.Language",
            "role": "languages",
            "children": [
                {
                    "id": "modelix:aRepositoryId/100000002",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000003",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "id",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/4225291329823310560"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000004",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "moduleVersion",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242370"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000005",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "compileInMPS",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242373"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000006",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "models",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000029"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000007",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "facets",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242412"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/10000000a"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000008",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "dependencies",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242425"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/10000000b"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000009",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "languageDependencies",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242439"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000012"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000002f",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "modelix:aRepositoryId/100000030",
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
                    "id": "modelix:aRepositoryId/10000000a",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000031",
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
                    "id": "modelix:aRepositoryId/10000000b",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000000c",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "reexport",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242416"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000000d",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "uuid",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242418"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000000e",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "name",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242421"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000000f",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "explicit",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750565"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000010",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "version",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750570"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000011",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "scope",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/8438396892798826745"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000032",
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
                    "id": "modelix:aRepositoryId/100000012",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000013",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "uuid",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575314"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000014",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "name",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575315"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000033",
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
                    "id": "modelix:aRepositoryId/100000015",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000034",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/100000002"
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
                    "id": "modelix:aRepositoryId/100000016",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000035",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/100000002"
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
                    "id": "modelix:aRepositoryId/100000017",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000036",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/100000002"
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
                    "id": "modelix:aRepositoryId/100000018",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000019",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000002"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000001a",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "projects",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/7064605579395546636"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/10000001c"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000001b",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "tempModules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/8226136427470548682"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000002"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000037",
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
                    "id": "modelix:aRepositoryId/10000001c",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000001d",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4008363636171860450"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000002"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000001e",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "projectModules",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4201834143491306088"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000020"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000038",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "modelix:aRepositoryId/100000039",
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
                    "id": "modelix:aRepositoryId/10000001f",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000003a",
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
                    "id": "modelix:aRepositoryId/100000020",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000021",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "virtualFolder",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4201834143491306084/4201834143491306085"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000003b",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/10000001f"
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
                    "id": "modelix:aRepositoryId/100000022",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000003c",
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
                    "id": "modelix:aRepositoryId/100000023",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000024",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "version",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429/2206727074858242435"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000003d",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/100000012"
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
                    "id": "modelix:aRepositoryId/100000025",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000003e",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/100000012"
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
                    "id": "modelix:aRepositoryId/100000026",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/100000027",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "generated",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242407"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000028",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "path",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242409"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000003f",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts",
                            "references": {
                                "concept": "modelix:aRepositoryId/10000000a"
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
                    "id": "modelix:aRepositoryId/100000029",
                    "concept": "modelix.metameta.Concept",
                    "role": "concepts",
                    "children": [
                        {
                            "id": "modelix:aRepositoryId/10000002a",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "id",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/2615330535972958738"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000002b",
                            "concept": "modelix.metameta.Property",
                            "role": "properties",
                            "properties": {
                                "name": "stereotype",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/3832696962605996173"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000002c",
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
                            "id": "modelix:aRepositoryId/10000002d",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "modelImports",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/6402965165736931000"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000022"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/10000002e",
                            "concept": "modelix.metameta.ChildLink",
                            "role": "childLinks",
                            "properties": {
                                "multiple": "true",
                                "name": "usedLanguages",
                                "optional": "true",
                                "uid": "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/5381564949800872334"
                            },
                            "references": {
                                "childConcept": "modelix:aRepositoryId/100000023"
                            }
                        },
                        {
                            "id": "modelix:aRepositoryId/100000040",
                            "concept": "modelix.metameta.ConceptReference",
                            "role": "superConcepts"
                        },
                        {
                            "id": "modelix:aRepositoryId/100000041",
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
                    "id": "modelix:aRepositoryId/1",
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
                    "id": "modelix:aRepositoryId/1",
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
                "id": "modelix:aRepositoryId/100000042",
                "concept": "100000029",
                "role": "aChild",
                "properties": {
                  "id": "myId"
                },
                "references": {
                  "someRef": "modelix:aRepositoryId/100000043"
                },
                "children": [
                  {
                    "id": "modelix:aRepositoryId/100000043",
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
                    "id": "modelix:aRepositoryId/1",
                    "children": [
                        $metamodelDataSerialized,
                        {
                            "id": "modelix:aRepositoryId/100000042",
                            "concept": "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
                            "role": "aChild",
                            "properties": {
                                "id": "myId"
                            },
                            "references": {
                                "someRef": "modelix:aRepositoryId/100000043"
                            },
                            "children": [
                              {
                                "id": "modelix:aRepositoryId/100000043",
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
                "id": "modelix:aRepositoryId/100000042",
                "concept": "100000029",
                "role": "aChild",
                "properties": {
                  "id": "myId"
                },
                "references": {
                  "someRef": "modelix:aRepositoryId/100000043"
                },
                "children": [
                  {
                    "id": "modelix:aRepositoryId/100000043",
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
        val storeCache = createObjectStoreCache(store)
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
