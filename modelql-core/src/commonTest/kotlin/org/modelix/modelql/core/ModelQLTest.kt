package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
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
import kotlin.test.assertTrue
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
                val images = it.images.mapLocal { MyImage(it) }.toList()
                assertTrue(images.requiresSingularQueryInput())
                assertTrue(it.requiresSingularQueryInput())
                id.zip(title, images).mapLocal {
                    MyNonSerializableClass(it.first, it.second, it.third)
                }
            }.toList()
        }
        val expected = testDatabase.products.map {
            MyNonSerializableClass(it.id, it.title, it.images.map { MyImage(it) })
        }
        assertEquals(expected, result)
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
    fun testIfEmpty() = runTestWithTimeout {
        val result: String = remoteProductDatabaseQuery { db ->
            db.products.filter { it.id.equalTo(-1) }.map { it.title }.ifEmpty { "alternative".asMono() }.first()
        }
        println(result)
        assertEquals("alternative", result)
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
    fun list_in_filter_condition() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.filter { it.images.isNotEmpty() }.count()
        }
        assertEquals(30, result)
    }

    @Test
    fun repeatingZip() = runTestWithTimeout {
        val ids = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.toList()
        }
        assertEquals((1..30).toList(), ids)

        val result: List<List<Comparable<*>>> = remoteProductDatabaseQuery { db ->
            db.products.map { it.id }.allowEmpty().zip("abc".asMono()).toList()
        }.map { it.values }
        assertEquals((1..30).map { listOf(it, "abc") }, result)
    }

    @Test
    fun testMapLocal2_unusedInput() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.mapLocal2 {
                val title = "xxx".asMono().getLater()
                onSuccess {
                    "Title: " + title.get()
                }
            }.toList()
        }
        println(result)
        assertEquals((1..30).map { "Title: xxx" }, result)
    }

    @Test
    fun testMapLocal2() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.mapLocal2 {
                val title = it.title.getLater()
                onSuccess {
                    "Title: " + title.get()
                }
            }.toList()
        }
        println(result)
        assertEquals(testDatabase.products.map { "Title: " + it.title }, result)
    }

    @Test
    fun testZipOrder() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { it.zip(it.images.assertNotEmpty()) }.mapLocal2 {
                val product = it.first.getLater()
                val image = it.second.getLater()
                onSuccess {
                    val localProduct: Product = product.get()
                    val localImage: String = image.get()
                    localProduct to localImage
                }
            }.toList()
        }
        println(result)
    }

    @Test
    fun testZipCount() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { it.images }.map { it.zip() }.count()
        }
        assertEquals(testDatabase.products.flatMap { it.images }.count(), result)
    }

    @Test
    fun testZipFlowVsSequence() = runTestWithTimeout {
        val flowSize = (1..10).asFlow().flatMapConcat {
            combine(listOf(flowOf(it), (30..60).asFlow())) { it[0] to it[1] }
        }.fold(0) { acc, it -> acc + it.first * it.second }

        val sequenceSize = (1..10).asSequence().flatMap {
            CombiningSequence(arrayOf(sequenceOf(it), (30..60).asSequence()))
        }.fold(0) { acc, it -> acc + (it[0] as Int) * (it[1] as Int) }

        assertEquals(flowSize, sequenceSize)
    }

    data class MyNonSerializableClass(val id: Int, val title: String, val images: List<MyImage>)
    data class MyImage(val url: String)
}

suspend fun <ResultT> remoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    return doRemoteProductDatabaseQuery(body)
}
suspend fun <ResultT> doRemoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    val query: MonoUnboundQuery<ProductDatabase, ResultT> = IUnboundQuery.build(body).castToInstance()
    val json = Json {
        prettyPrint = true
        serializersModule = SerializersModule {
            include(UnboundQuery.serializersModule)
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
    val deserializedQuery = json.decodeFromString<QueryDescriptor>(serializedQuery).createQuery() as MonoUnboundQuery<ProductDatabase, ResultT>
    println("original query    : $query")
    println("deserialized query: $deserializedQuery")
    val remoteResult: IStepOutput<ResultT> = deserializedQuery.execute(testDatabase)
    val serializedResult = json.encodeToString(deserializedQuery.getAggregationOutputSerializer(json.serializersModule), remoteResult)
    println(serializedResult)
    return json.decodeFromString(query.getAggregationOutputSerializer(json.serializersModule), serializedResult).value
}

class ProductsTraversal() : FluxTransformingStep<ProductDatabase, Product>() {
    override fun createFlow(input: StepFlow<ProductDatabase>, context: IFlowInstantiationContext): StepFlow<Product> {
        return input.flatMapConcat { it.value.products.asFlow() }.asStepFlow()
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Product> {
        return getProducer().createSequence(queryInput).flatMap { it.products }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Product>> = serializersModule.serializer<Product>().stepOutputSerializer()

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.products"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductsTraversal()
        }
    }
}

class ProductTitleTraversal : MonoTransformingStep<Product, String>() {
    override fun transform(input: Product): String {
        return input.title
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".title"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> = serializersModule.serializer<String>().stepOutputSerializer()

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductTitleTraversal()
        }
    }
}
class ProductIdTraversal : MonoTransformingStep<Product, Int>() {
    override fun transform(input: Product): Int {
        return input.id
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> = serializersModule.serializer<Int>().stepOutputSerializer()

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.id"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductIdTraversal()
        }
    }
}
class ProductImagesTraversal : FluxTransformingStep<Product, String>() {
    override fun createFlow(input: StepFlow<Product>, context: IFlowInstantiationContext): StepFlow<String> {
        return input.flatMapConcat { it.value.images.asFlow() }.asStepFlow()
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<String> {
        return getProducer().createSequence(queryInput).flatMap { it.images }
    }

    override fun toString(): String {
        return "${getProducer()}.images"
    }
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> = serializersModule.serializer<String>().stepOutputSerializer()

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductImagesTraversal()
        }
    }
}

val IMonoStep<ProductDatabase>.products: IFluxStep<Product> get() = ProductsTraversal().also { connect(it) }
val IMonoStep<Product>.title: IMonoStep<String> get() = ProductTitleTraversal().also { connect(it) }
val IMonoStep<Product>.id: IMonoStep<Int> get() = ProductIdTraversal().also { connect(it) }
val IMonoStep<Product>.images: IFluxStep<String> get() = ProductImagesTraversal().also { connect(it) }
