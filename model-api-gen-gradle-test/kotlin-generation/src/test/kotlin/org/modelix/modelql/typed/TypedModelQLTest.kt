package org.modelix.modelql.typed

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import jetbrains.mps.baseLanguage.C_ClassConcept
import jetbrains.mps.baseLanguage.C_IntegerType
import jetbrains.mps.baseLanguage.C_MinusExpression
import jetbrains.mps.baseLanguage.C_ParameterDeclaration
import jetbrains.mps.baseLanguage.C_PlusExpression
import jetbrains.mps.baseLanguage.C_PublicVisibility
import jetbrains.mps.baseLanguage.C_ReturnStatement
import jetbrains.mps.baseLanguage.C_StaticMethodDeclaration
import jetbrains.mps.baseLanguage.C_VariableReference
import jetbrains.mps.baseLanguage.ClassConcept
import jetbrains.mps.baseLanguage.StaticMethodDeclaration
import jetbrains.mps.core.xml.C_XmlComment
import jetbrains.mps.core.xml.C_XmlCommentLine
import jetbrains.mps.core.xml.C_XmlDocument
import jetbrains.mps.core.xml.C_XmlFile
import jetbrains.mps.lang.editor.imageGen.C_ImageGenerator
import jetbrains.mps.lang.editor.imageGen.ImageGenerator
import org.junit.jupiter.api.TestInstance
import org.modelix.apigen.test.ApigenTestLanguages
import org.modelix.metamodel.instanceOf
import org.modelix.metamodel.typed
import org.modelix.metamodel.untyped
import org.modelix.metamodel.untypedConcept
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.remove
import org.modelix.model.api.resolve
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.asMono
import org.modelix.modelql.core.count
import org.modelix.modelql.core.equalTo
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.first
import org.modelix.modelql.core.map
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.toSet
import org.modelix.modelql.core.zip
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.addToMember
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.expression
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.member
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.parameter
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.setExpression
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.setVariableDeclaration
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.variableDeclaration
import org.modelix.modelql.gen.jetbrains.mps.baseLanguage.visibility
import org.modelix.modelql.gen.jetbrains.mps.core.xml.addToLines
import org.modelix.modelql.gen.jetbrains.mps.core.xml.document
import org.modelix.modelql.gen.jetbrains.mps.core.xml.lines
import org.modelix.modelql.gen.jetbrains.mps.core.xml.setDocument
import org.modelix.modelql.gen.jetbrains.mps.lang.core.name
import org.modelix.modelql.gen.jetbrains.mps.lang.core.setName
import org.modelix.modelql.gen.jetbrains.mps.lang.editor.imageGen.node
import org.modelix.modelql.gen.jetbrains.mps.lang.editor.imageGen.node_orNull
import org.modelix.modelql.gen.jetbrains.mps.lang.editor.imageGen.setNode
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.conceptReference
import org.modelix.modelql.untyped.descendants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypedModelQLTest {

    private val branchRef
        get() = ModelFacade.createBranchReference(RepositoryId("modelql-test"), "master")

    private fun runTest(block: suspend ApplicationTestBuilder.(ModelQLClient) -> Unit) = testApplication {
        application {
            try {
                installDefaultServerPlugins()
                val repoManager = RepositoriesManager(InMemoryStoreClient())
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }

        val modelClient = ModelClientV2.builder()
            .client(client)
            .url("http://localhost/v2/")
            .build()
            .also { it.init() }
        modelClient.runWrite(branchRef) {
            createTestData(it)
        }
        val modelQlClient = ModelQLClient.builder()
            .httpClient(client)
            .url("http://localhost/v2/repositories/${branchRef.repositoryId.id}/branches/${branchRef.branchName}/query")
            .build()
        block(modelQlClient)
    }

    init {
        ApigenTestLanguages.registerAll()
    }

    protected fun createTestData(rootNode: INode) {
        rootNode.allChildren.forEach { it.remove() }
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
        // Example for optional reference
        rootNode.addNewChild("imageGen", -1, C_ImageGenerator.untyped())
            .typed<ImageGenerator>()
            .apply { node = cls1 }

        // Example for single non-abstract child
        rootNode.addNewChild("xmlFile", -1, C_XmlFile.untyped())

        // Example for mulitple non-abstract child
        rootNode.addNewChild("xmlComment", -1, C_XmlComment.untyped())
    }

    @Test
    fun `simple query`() = runTest { client ->
        val result: Int = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .count()
        }
        assertEquals(1, result)
    }

    @Test
    fun `complex query`() = runTest { client ->
        val result: List<Pair<String, String>> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .map { it.name.zip(it.parameter.name.toList(), it.untyped().conceptReference()) }
                .toList()
        }.map {
            it.first to it.first + "(" + it.second.joinToString(", ") + ") [" + it.third?.resolve()?.getLongName() + "]"
        }
        assertEquals(listOf("plus" to "plus(a, b) [jetbrains.mps.baseLanguage.StaticMethodDeclaration]"), result)
    }

    @Test
    fun `get references`() = runTest { client ->
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
    fun `get references - fqName`() = runTest { client ->
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
    fun `node serialization`() = runTest { client ->
        val result: List<StaticMethodDeclaration> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .untyped()
                .toList()
        }.map { it.typed<StaticMethodDeclaration>() }
        assertEquals("plus", result[0].name)
    }

    @Test
    fun `return typed node`() = runTest { client ->
        val result: List<StaticMethodDeclaration> = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .filter { it.visibility.instanceOf(C_PublicVisibility) }
                .toList()
        }
        assertEquals("plus", result[0].name)
    }

    @Test
    fun `set property`() = runTest { client ->
        val expected = "myRenamedMethod"
        client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .first()
                .setName(expected.asMono())
        }
        val actual = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .first()
        }
        assertEquals(expected, actual.name)
    }

    @Test
    fun `set reference`() = runTest { client ->
        val oldValue = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .descendants()
                .ofConcept(C_ParameterDeclaration)
                .first()
                .name
        }
        val expected = "b"
        client.query { root ->
            val descendants = root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .descendants()

            val target = descendants.ofConcept(C_ParameterDeclaration)
                .filter { it.name.equalTo(expected) }
                .first()

            descendants.ofConcept(C_VariableReference)
                .first()
                .setVariableDeclaration(target)
        }

        val actual = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .descendants()
                .ofConcept(C_VariableReference)
                .first()
        }
        assertNotEquals(expected, oldValue)
        assertEquals(expected, actual.variableDeclaration.name)
    }

    @Test
    fun `set reference - null`() = runTest { client ->
        val oldValue = client.query { root ->
            root.children("imageGen").ofConcept(C_ImageGenerator).first().node
        }

        client.query { root ->
            root.children("imageGen").ofConcept(C_ImageGenerator).first().setNode(null)
        }

        val actual = client.query { root ->
            root.children("imageGen").ofConcept(C_ImageGenerator).first().node_orNull
        }
        assertNotNull(oldValue)
        assertNull(actual)
    }

    @Test
    fun `add new child`() = runTest { client ->
        val oldNumChildren = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .first()
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .count()
        }

        client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .first()
                .addToMember(C_StaticMethodDeclaration)
        }

        val children = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .first()
                .member
                .ofConcept(C_StaticMethodDeclaration)
                .toList()
        }

        assertEquals(oldNumChildren + 1, children.size)
        assertEquals(C_StaticMethodDeclaration.untyped().getUID(), children.last().untyped().concept?.getUID())
    }

    @Test
    fun `add new child - default concept`() = runTest { client ->
        client.query { root ->
            root.descendants().ofConcept(C_XmlComment)
                .first()
                .addToLines()
        }

        val actual = client.query { root ->
            root.descendants().ofConcept(C_XmlComment)
                .first()
                .lines
                .first()
        }

        assertTrue { actual.instanceOf(C_XmlCommentLine) }
    }

    @Test
    fun `set child`() = runTest { client ->
        client.query { root ->
            root.descendants().ofConcept(C_ReturnStatement)
                .first()
                .setExpression(C_MinusExpression)
        }

        val actual = client.query { root ->
            root.descendants().ofConcept(C_ReturnStatement).first().expression
        }
        assertNotNull(actual)
        assertTrue(actual.instanceOf(C_MinusExpression), actual.untypedConcept().getLongName())
    }

    @Test
    fun `set child - default concept`() = runTest { client ->
        client.query { root ->
            root.descendants().ofConcept(C_XmlFile)
                .first()
                .setDocument()
        }

        val actual = client.query { root ->
            root.descendants().ofConcept(C_XmlFile)
                .first()
                .document
        }

        assertTrue { actual.instanceOf(C_XmlDocument) }
    }

    @Test
    fun `write operations return typed nodes`() = runTest { client ->
        val name = client.query { root ->
            root.children("classes").ofConcept(C_ClassConcept)
                .first()
                .addToMember(C_StaticMethodDeclaration)
                .name
        }
        assertEquals("", name)
    }
}
