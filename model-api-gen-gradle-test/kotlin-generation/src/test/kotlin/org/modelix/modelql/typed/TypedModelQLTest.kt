/*
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
package org.modelix.modelql.typed

import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import jetbrains.mps.baseLanguage.C_ClassConcept
import jetbrains.mps.baseLanguage.C_IntegerType
import jetbrains.mps.baseLanguage.C_PlusExpression
import jetbrains.mps.baseLanguage.C_PublicVisibility
import jetbrains.mps.baseLanguage.C_ReturnStatement
import jetbrains.mps.baseLanguage.C_StaticMethodDeclaration
import jetbrains.mps.baseLanguage.C_VariableReference
import jetbrains.mps.baseLanguage.ClassConcept
import jetbrains.mps.baseLanguage.StaticMethodDeclaration
import org.modelix.apigen.test.ApigenTestLanguages
import org.modelix.metamodel.typed
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.api.resolve
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.server.light.LightModelServer
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.count
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.map
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.toSet
import org.modelix.modelql.core.zip
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.member
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.parameter
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.variableDeclaration
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.visibility
import org.modelix.modelql.gen.jetbrains.mps.lang.core.name
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.conceptReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedModelQLTest {
    private lateinit var branch: IBranch

    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            LightModelServer(80, branch.getRootNode()).apply { installHandlers() }
        }
        val httpClient = createClient {
        }
        block(httpClient)
    }

    @BeforeTest
    fun setup() {
        ApigenTestLanguages.registerAll()
        val tree = CLTree(ObjectStoreCache(MapBaseStore()))
        branch = PBranch(tree, IdGenerator.getInstance(1))
        val rootNode = branch.getRootNode()
        branch.runWrite {
            val cls1 = rootNode.addNewChild("classes", -1, C_ClassConcept.untyped()).typed<ClassConcept>()
            cls1.apply {
                name = "Math"
                member.addNew(C_StaticMethodDeclaration).apply {
                    name = "plus"
                    returnType.setNew(C_IntegerType)
                    visibility.setNew(C_PublicVisibility)
                    val a = parameter.addNew().apply {
                        name = "a"
                        type.setNew(C_IntegerType)
                    }
                    val b = parameter.addNew().apply {
                        name = "b"
                        type.setNew(C_IntegerType)
                    }
                    body.setNew().apply {
                        statement.addNew(C_ReturnStatement).apply {
                            expression.setNew(C_PlusExpression).apply {
                                leftExpression.setNew(C_VariableReference).apply {
                                    variableDeclaration = a
                                }
                                rightExpression.setNew(C_VariableReference).apply {
                                    variableDeclaration = b
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun simpleTest() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: Int = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .count()
        }
        assertEquals(1, result)
    }

    @Test
    fun test() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: List<Pair<String, String>> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .map { it.name.zip(it.parameter.name.toList(), it.untyped().conceptReference()) }
                .toList()
        }.map { it.first to it.first + "(" + it.second.joinToString(", ") + ") [" + it.third?.resolve()?.getLongName() + "]" }
        assertEquals(listOf("plus" to "plus(a, b) [jetbrains.mps.baseLanguage.StaticMethodDeclaration]"), result)
    }

    @Test
    fun testReferences() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val usedVariables: Set<String> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .descendants()
                .ofConcept(C_VariableReference)
                .variableDeclaration
                .name
                .toSet()
        }
        assertEquals(setOf("a", "b"), usedVariables)
    }

    @Test
    fun testReferencesFqName() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val usedVariables: Set<String> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .map { method ->
                    method.descendants()
                        .ofConcept(C_VariableReference)
                        .variableDeclaration
                        .name
                        .toSet()
                        .zip(method.name)
                }
                .toList()
        }.map { it.first.map { simpleName -> it.second + "." + simpleName } }.flatten().toSet()

        assertEquals(setOf("plus.a", "plus.b"), usedVariables)

        // TODO simplify query
    }

    @Test
    fun testNodeSerialization() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: List<StaticMethodDeclaration> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .untyped()
                .toList()
        }.map { it.typed<StaticMethodDeclaration>() }
        assertEquals("plus", branch.computeRead { result[0].name })
    }

    @Test
    fun returnTypedNode() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: List<StaticMethodDeclaration> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .toList()
        }
        assertEquals("plus", branch.computeRead { result[0].name })
    }
}
