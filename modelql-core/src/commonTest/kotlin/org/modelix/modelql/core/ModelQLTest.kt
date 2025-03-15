package org.modelix.modelql.core

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun testAutoSharedCrossQueryStep() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.map { product ->
                product.id.map {
                    product.title.zip(it)
                }
            }.toList()
        }
        assertEquals(
            testDatabase.products.map { it.title to it.id },
            result.map { it.first to it.second },
        )
    }

    @Test
    fun testLegalCrossQueryStep() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.map { product ->
                val sharedProduct: IMonoStep<Product> = product.shared()
                product.id.map {
                    sharedProduct.title.zip(it)
                }
            }.toList()
        }
        println(result)
        assertEquals(
            testDatabase.products.map { it.title to it.id },
            result.map { it.first to it.second },
        )
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
    @Ignore
    fun testMapLocalToSet() = runTestWithTimeout {
        val result: Int = remoteProductDatabaseQuery { db ->
            db.products.map { it.title }.mapLocal { it.substring(0, 1) }.toSet().size()
        }
        println(result)
        assertEquals(testDatabase.products.map { it.title.substring(0, 1) }.toSet().size, result)
    }

    @Test
    fun testMapAccess() = runTestWithTimeout {
        val result: String = remoteProductDatabaseQuery { db ->
            db.products.associateBy { it.id }[5.asMono()].filterNotNull().title
        }
        println(result)
        assertEquals("Huawei P30", result)
    }

    @Test
    fun testMapAccessDeserializer() = runTestWithTimeout {
        val result: String? = remoteProductDatabaseQuery { db ->
            db.products.associate({ it.id }, { it.mapLocal { it.title } })[5.asMono()]
        }
        println(result)
        assertEquals("Huawei P30", result)
    }

    @Test
    fun testFirstOrNull() = runTestWithTimeout {
        val result: Product? = remoteProductDatabaseQuery { db ->
            db.products.filter { it.id.equalTo((-1).asMono()) }.firstOrNull()
        }
        assertEquals(null, result)
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
    fun testIsEmpty() = runTestWithTimeout {
        val result: Boolean = remoteProductDatabaseQuery { db ->
            db.products.filter { it.id.equalTo(-1) }.isEmpty()
        }
        assertTrue(result)
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
    fun zipDestructingMap() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.equalTo("iPhone 9") }.map {
                it.title.zip(it.category).map { (_, category) ->
                    category
                }
            }.first()
        }

        assertEquals("smartphones", result)
    }

    @Test
    fun zipDestructingMapLocal() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.filter { it.title.equalTo("iPhone 9") }.map {
                it.title.zip(it.category).mapLocal { (title, category) ->
                    "$title: $category"
                }
            }.first()
        }

        assertEquals("iPhone 9: smartphones", result)
    }

    @Test
    fun testMapLocal2_unusedInput() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.buildLocalMapping {
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
    fun testLocalMappingBuilder() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.buildLocalMapping {
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
            db.products.flatMap { it.zip(it.images.assertNotEmpty()) }.buildLocalMapping {
                val product = it.first.getLater()
                val image = it.second.getLater()
                onSuccess {
                    val localProduct: Product = product.get()
                    val localImage: String = image.get()
                    localProduct to localImage
                }
            }.toList()
        }
        assertEquals(132, result.size)
    }

    @Test
    fun testZipCount() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { it.images }.map { it.zip() }.count()
        }
        assertEquals(testDatabase.products.flatMap { it.images }.count(), result)
    }

    @Test
    fun test_firstOrNull_nullIfEmpty() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.firstOrNull().nullIfEmpty()
        }
        assertEquals(testDatabase.products.firstOrNull(), result)
    }

    @Test
    fun test_nullIfEmpty() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.nullIfEmpty().toList()
        }
        assertEquals(testDatabase.products, result)
    }

    @Test
    fun mapIfNotNull() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            "a".asMono().mapIfNotNull { it.identity() }.mapIfNotNull { it.identity() }
        }
        assertEquals("a", result)
    }

    @Test
    fun zipElementAccess() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.flatMap { enum ->
                enum.images.allowEmpty().zip(enum.title.firstOrNull()).map { it ->
                    it.first.zip(it.second).mapLocal { "" }
                }
            }.toList()
        }
        assertEquals(testDatabase.products.flatMap { it.images }.map { "" }, result)
    }

    @Test
    fun queryCall() = runTestWithTimeout {
        val query1 = buildMonoQuery<String, String> { it.identity() }
        val result = remoteProductDatabaseQuery { db ->
            // Call the same query twice, once with an input that produces a SimpleStepOutput and once with
            // a MultiplexedOutput to check that the correct serializer is forwarded.
            db.products.map { it.title.callQuery { query1 }.allowEmpty().zip(it.title.emptyStringIfNull().callQuery { query1 }.allowEmpty()) }.toList()
        }
        assertEquals(testDatabase.products.map { it.title to it.title }, result.map { it.first to it.second })
    }

    @Test
    fun paginationTest() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            db.products.drop(3).take(5).map { it.title }.toList()
        }
        assertEquals(testDatabase.products.drop(3).take(5).map { it.title }, result)
    }

    @Test
    fun testFind() = runTestWithTimeout {
        val result: String = remoteProductDatabaseQuery { db ->
            db.find({ it.products }, { it.id }, 3.asMono()).title
        }
        assertEquals("Samsung Universe 9", result)
    }

    @Test
    fun testIllegalCrossQueryStreams() = runTestWithTimeout {
        val ex = assertFailsWith(IllegalArgumentException::class) {
            remoteProductDatabaseQuery<String> { db ->
                // The `elements` query uses the outside `db` instead of the input `it`.
                // The query is only allowed to use values from its input, otherwise it wouldn't be cacheable.
                db.find({ db.products }, { it.id }, 3.asMono()).title
            }
        }
        val expectedMessage = "Unsupported cross-query usage of"
        assertEquals(expectedMessage, (ex.message ?: "").take(expectedMessage.length))
    }

    @Test
    fun testFindAll() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            db.findAll({ it.products }, { it.category }, "smartphones".asMono()).map { it.title }.toList()
        }
        assertEquals(listOf("iPhone 9", "iPhone X", "Samsung Universe 9", "OPPOF19", "Huawei P30"), result)
    }

    @Test
    fun testFindAllMultipleKeys() = runTestWithTimeout {
        val result: List<String> = remoteProductDatabaseQuery { db ->
            db.findAll({ it.products }, { it.category }, fluxOf("smartphones", "laptops")).map { it.title }.toList()
        }
        assertEquals(
            listOf(
                "iPhone 9",
                "iPhone X",
                "Samsung Universe 9",
                "OPPOF19",
                "Huawei P30",
                "MacBook Pro",
                "Samsung Galaxy Book",
                "Microsoft Surface Laptop 4",
                "Infinix INBOOK",
                "HP Pavilion 15-DK1056WM",
            ),
            result,
        )
    }

    @Test
    fun assertNotEmpty_throws_IllegalArgumentException() = runTestWithTimeout {
        assertFailsWith(IllegalArgumentException::class) {
            remoteProductDatabaseQuery<List<String>> { db ->
                db.products.filter { false.asMono() }.assertNotEmpty().map { it.title }.toList()
            }
        }
    }

    @Test
    fun null_mono_of_non_serializable_type() = runTestWithTimeout {
        val result = remoteProductDatabaseQuery { db ->
            // This caused an exception during serialization of the query before
            nullMono<MyNonSerializableClass>()
        }
        assertEquals(null, result)
    }

    data class MyNonSerializableClass(val id: Int, val title: String, val images: List<MyImage>)
    data class MyImage(val url: String)
}

suspend fun <ResultT> remoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    return doRemoteProductDatabaseQuery(body)
}
suspend fun <ResultT> doRemoteProductDatabaseQuery(body: (IMonoStep<ProductDatabase>) -> IMonoStep<ResultT>): ResultT {
    val query: MonoUnboundQuery<ProductDatabase, ResultT> = buildMonoQuery { body(it) }.castToInstance()
    val json = Json {
        prettyPrint = true
        serializersModule = SerializersModule {
            include(UnboundQuery.serializersModule)
            polymorphic(StepDescriptor::class) {
                subclass(ProductsTraversal.Descriptor::class)
                subclass(ProductTitleTraversal.Descriptor::class)
                subclass(ProductCategoryTraversal.Descriptor::class)
                subclass(ProductIdTraversal.Descriptor::class)
                subclass(ProductImagesTraversal.Descriptor::class)
            }
        }
    }
    val serializedQuery = json.encodeToString(query.createDescriptor())
    println(serializedQuery)
    val deserializedQuery = json.decodeFromString<QueryGraphDescriptor>(serializedQuery).createRootQuery() as MonoUnboundQuery<ProductDatabase, ResultT>
    println("original query    : $query")
    println("deserialized query: $deserializedQuery")
    val remoteResult: IStepOutput<ResultT> = deserializedQuery.execute(QueryEvaluationContext.EMPTY, testDatabase.asStepOutput(null))
    val serializedResult = json.encodeToString(deserializedQuery.getAggregationOutputSerializer(SerializationContext(json.serializersModule)), remoteResult)
//    println(serializedResult)
    return json.decodeFromString(query.getAggregationOutputSerializer(SerializationContext(json.serializersModule)), serializedResult).value
}

class ProductsTraversal() : FluxTransformingStep<ProductDatabase, Product>() {
    override fun createStream(input: StepStream<ProductDatabase>, context: IStreamInstantiationContext): StepStream<Product> {
        return input.flatMapIterable { it.value.products }.asStepStream(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Product>> = serializationContext.serializer<Product>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.products"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductsTraversal()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

class ProductTitleTraversal : SimpleMonoTransformingStep<Product, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): String {
        return input.title
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".title"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> = serializationContext.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductTitleTraversal()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}
class ProductCategoryTraversal : SimpleMonoTransformingStep<Product, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): String {
        return input.category
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".category"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> = serializationContext.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductCategoryTraversal()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}
class ProductIdTraversal : SimpleMonoTransformingStep<Product, Int>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: Product): Int {
        return input.id
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> = serializationContext.serializer<Int>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()
    override fun toString(): String {
        return "${getProducer()}.id"
    }

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductIdTraversal()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}
class ProductImagesTraversal : FluxTransformingStep<Product, String>() {
    override fun createStream(input: StepStream<Product>, context: IStreamInstantiationContext): StepStream<String> {
        return input.flatMapIterable { it.value.images }.asStepStream(this)
    }

    override fun toString(): String {
        return "${getProducer()}.images"
    }
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> = serializationContext.serializer<String>().stepOutputSerializer(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    class Descriptor : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ProductImagesTraversal()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

val IMonoStep<ProductDatabase>.products: IFluxStep<Product> get() = ProductsTraversal().also { connect(it) }
val IMonoStep<Product>.title: IMonoStep<String> get() = ProductTitleTraversal().also { connect(it) }
val IMonoStep<Product>.category: IMonoStep<String> get() = ProductCategoryTraversal().also { connect(it) }
val IMonoStep<Product>.id: IMonoStep<Int> get() = ProductIdTraversal().also { connect(it) }
val IMonoStep<Product>.images: IFluxStep<String> get() = ProductImagesTraversal().also { connect(it) }
