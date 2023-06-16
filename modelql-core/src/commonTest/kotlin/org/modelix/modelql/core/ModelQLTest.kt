package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds

class ModelQLTest {
    fun runTestWithTimeout(body: suspend TestScope.() -> Unit): TestResult {
        return runTest {
            withTimeout(3.seconds) {
                body()
            }
        }
    }

    @Test
    fun test_local() = runTestWithTimeout {
        val result: List<MyNonSerializableClass> = remoteProductDatabaseQuery { db ->
            db.products.map {
                val id = it.id
                val title = it.title
                val images = it.images.toList()
                id.zip(title, images)
            }.toList()
        }.map {
            MyNonSerializableClass(it.first, it.second, it.third)
        }
        println(result)
        assertEquals(30, result.size)
    }

    @Test
    fun test_illegal_cross_map_reference() = runTestWithTimeout {
        assertFails {
            val result = remoteProductDatabaseQuery { db ->
                db.products.map { product ->
                    product.id.map {
                        // referencing the surrounding map input (product) is not allowed
                        product.title.zip(it)
                    }
                }.toList()
            }
            println(result)
        }
    }

    @Test
    fun testFilter() = runTestWithTimeout {
        val result: List<Int> = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.contains("9") }.map { it.id }.toList()
        }
        println(result)
        assertEquals(3, result.size)
    }

    @Test
    fun test2() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            val products = db.products
            val products3 = products.filter { it.title.contains("3") }
            val products9 = products.filter { it.title.contains("9") }

            (products3 + products9).map { it.title }.toList()
        }
        println(result)
        assertEquals(6, result.size)
    }

    @Test
    fun remoteQuery() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            val products = db.products
            val products3 = products.filter { it.title.contains("3") }
            val products9 = products.filter { it.title.contains("9") }

            (products3 + products9).map { it.title }.toList()
        }
        println(result)
        assertEquals(6, result.size)
    }

    @Test
    fun countProducts() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.count()
        }
        assertEquals(30, result)
    }

    @Test
    fun count_products_containing_9() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.contains("9") }.count()
        }
        assertEquals(3, result)
    }

    @Test
    fun repeatingZip() = runTestWithTimeout {
        val ids = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.toList()
        }
        assertEquals((1..30).toList(), ids)

        val result: List<List<Comparable<*>>> = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.zip("abc".asMono()).toList()
        }.map { it.values }
        assertEquals((1..30).map { listOf(it, "abc") }, result)
    }

    data class MyNonSerializableClass(val id: Int, val title: String, val images: List<String>)
}

suspend fun <ResultT> remoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    return doRemoteProductDatabaseQuery(body)
}
suspend fun <ResultT> doRemoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    val query: Query<ProductDatabase, ResultT> = Query.build(body)
    val json = Json {
        prettyPrint = true
        serializersModule = SerializersModule {
            include(Query.serializersModule)
            polymorphic(
                StepDescriptor::class,
                ProductsTraversal.Descriptor::class,
                ProductsTraversal.Descriptor.serializer()
            )
            polymorphic(
                StepDescriptor::class,
                ProductTitleTraversal.Descriptor::class,
                ProductTitleTraversal.Descriptor.serializer()
            )
            polymorphic(
                StepDescriptor::class,
                ProductIdTraversal.Descriptor::class,
                ProductIdTraversal.Descriptor.serializer()
            )
            polymorphic(
                StepDescriptor::class,
                ProductImagesTraversal.Descriptor::class,
                ProductImagesTraversal.Descriptor.serializer()
            )
        }
    }
    val serializedQuery = json.encodeToString(query.createDescriptor())
    println(serializedQuery)
    val deserializedQuery = json.decodeFromString<QueryDescriptor>(serializedQuery).createQuery() as Query<ProductDatabase, ResultT>
    val remoteResult = deserializedQuery.run(testDatabase)
    val serializedResult = json.encodeToString(deserializedQuery.getOutputSerializer(json.serializersModule), remoteResult)
    println(serializedResult)
    return json.decodeFromString(query.getOutputSerializer(json.serializersModule), serializedResult) as ResultT
}

class ProductsTraversal(): FluxTransformingStep<ProductDatabase, Product>() {
    override fun createFlow(input: Flow<ProductDatabase>, context: IFlowInstantiationContext): Flow<Product> {
        return input.flatMapConcat { it.products.asFlow() }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Product> = throw UnsupportedOperationException()

    override fun createDescriptor() = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return ProductsTraversal()
        }
    }
}

class ProductTitleTraversal: MonoTransformingStep<Product, String>() {
    override fun createFlow(input: Flow<Product>, context: IFlowInstantiationContext): Flow<String> {
        return input.map { it.title }
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".title"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> = serializersModule.serializer<String>()

    override fun createDescriptor() = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return ProductTitleTraversal()
        }
    }
}
class ProductIdTraversal: MonoTransformingStep<Product, Int>() {
    override fun createFlow(input: Flow<Product>, context: IFlowInstantiationContext): Flow<Int> {
        return input.map { it.id }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Int> = serializersModule.serializer<Int>()

    override fun createDescriptor() = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return ProductIdTraversal()
        }
    }
}
class ProductImagesTraversal: FluxTransformingStep<Product, String>() {
    override fun createFlow(input: Flow<Product>, context: IFlowInstantiationContext): Flow<String> {
        return input.flatMapConcat { it.images.asFlow() }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> = serializersModule.serializer<String>()

    override fun createDescriptor() = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return ProductImagesTraversal()
        }
    }
}

val IMonoStep<ProductDatabase>.products: IFluxStep<Product> get() = ProductsTraversal().also { connect(it) }
val IMonoStep<Product>.title: IMonoStep<String> get() = ProductTitleTraversal().also { connect(it) }
val IMonoStep<Product>.id: IMonoStep<Int> get() = ProductIdTraversal().also { connect(it) }
val IMonoStep<Product>.images: IFluxStep<String> get() = ProductImagesTraversal().also { connect(it) }